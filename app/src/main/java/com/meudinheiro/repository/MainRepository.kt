package com.meudinheiro.repository

import android.content.Context
import androidx.room.withTransaction
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.PatrimonioDao
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.BackupDto
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Categoria
import com.meudinheiro.data.Lixeira
import com.meudinheiro.data.CategoriaDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.ResumoDto
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.WidgetResumo
import com.meudinheiro.domain.Natureza
import com.meudinheiro.funcoes.ExtratoPdf
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Violação de uma regra financeira (saldo insuficiente, valor inválido…). A mensagem é exibível ao usuário. */
class RegraFinanceiraException(message: String) : Exception(message)

sealed interface ResultadoAgendamento {
    data object Executado : ResultadoAgendamento
    data class Falhou(val motivo: String) : ResultadoAgendamento
}

/**
 * Fachada de dados do app. Toda operação que mexe em dinheiro roda em UMA transação do Room e termina
 * recalculando os caches derivados do extrato: saldo da conta (R3) e limite do cartão (R4).
 * Regras: docs/CONTRATO_DADOS.md.
 */
@Suppress("unused")
class MainRepository(
    private val context: Context,
    val db: AppDatabase
) {
    /** Construtor usado pelo app (banco único do processo). Os DAOs passados antes são ignorados. */
    constructor(
        context: Context,
        @Suppress("UNUSED_PARAMETER") dao: ContaSaldoDao? = null,
        @Suppress("UNUSED_PARAMETER") patrimonioDao: PatrimonioDao? = null
    ) : this(context, AppDatabase.getInstance(context))

    private val despesaDao = db.despesaDao()
    private val contaSaldoDao = db.contaSaldoDao()
    private val despesaFixaDao = db.despesaFixaDao()
    private val categoriaDao = db.categoriaDao()
    private val orcamentoDao = db.orcamentoDao()
    private val metaDao = db.metaDao()
    private val cartaoDao = db.cartaoDao()
    private val investimentoDao = db.investimentoDao()
    private val transacaoDao = db.transacaoDao()
    private val patrimonioDao = db.patrimonioDao()
    private val lixeiraDao = db.lixeiraDao()

    private fun agora() = System.currentTimeMillis()

    /* ======================= DERIVADOS (R3 / R4) ======================= */

    private suspend fun recalcularConta(numeroConta: String) {
        val saldo = Financas.saldoConta(numeroConta, despesaDao.obterDaConta(numeroConta))
        contaSaldoDao.atualizarSaldo(numeroConta, saldo)
    }

    /** R4/R18: o limite é do GRUPO (físico + virtuais); o mesmo valor é gravado em todos os cartões do grupo. */
    private suspend fun recalcularCartao(cartaoId: Int) {
        val cartao = cartaoDao.getCartaoPorId(cartaoId) ?: return
        val grupo = cartaoDao.obterGrupo(cartao.idDoGrupo)
        val principal = grupo.firstOrNull { it.id == cartao.idDoGrupo } ?: return
        val ids = grupo.map { it.id }.toSet()
        val limite = Financas.limiteDisponivel(principal, despesaDao.obterDosCartoes(ids.toList()), ids)
        grupo.forEach { if (it.limiteDisponivel != limite) cartaoDao.atualizarLimite(it.id, limite) }
    }

    /** Recalcula todos os caches. Chamado após restore e após receber mudanças da nuvem. */
    suspend fun recalcularTudo() = db.withTransaction {
        val despesas = despesaDao.obterTodasStatic()
        contaSaldoDao.obterTodasStatic().forEach { c ->
            val saldo = Financas.saldoConta(c.conta, despesas)
            if (saldo != c.saldo) contaSaldoDao.atualizarSaldo(c.conta, saldo)
        }
        val cartoes = cartaoDao.obterTodasStatic()
        cartoes.filter { it.cartaoPrincipalId == null }.forEach { principal ->
            val ids = Financas.idsDoGrupo(principal, cartoes)
            val limite = Financas.limiteDisponivel(principal, despesas.filter { it.cartaoId in ids }, ids)
            cartoes.filter { it.id in ids && it.limiteDisponivel != limite }.forEach { cartaoDao.atualizarLimite(it.id, limite) }
        }
    }

    /** Recalcula os caches afetados por [lancamentos] (conta e/ou cartão de cada um). */
    private suspend fun recalcularAfetados(lancamentos: Collection<Despesa>) {
        lancamentos.map { it.conta }.distinct().forEach { recalcularConta(it) }
        lancamentos.mapNotNull { it.cartaoId }.distinct().forEach { recalcularCartao(it) }
    }

    private fun normalizar(d: Despesa): Despesa {
        val valor = Dinheiro.arredondar(d.valor)
        val ms = d.data.time
        return d.copy(
            valor = valor,
            mes = Financas.mesDe(ms),
            ano = Financas.anoDe(ms),
            cartaoId = d.cartaoId?.takeIf { it != 0 },
            valorOriginal = if (d.valorOriginal > 0) Dinheiro.arredondar(d.valorOriginal) else valor,
            moedaOriginal = d.moedaOriginal.ifBlank { "BRL" },
            cotacaoNaData = if (d.cotacaoNaData > 0) d.cotacaoNaData else 1.0
        )
    }

    /** Valida e ajusta o lançamento (conta existe; compra no cartão fica na conta do cartão). */
    private suspend fun validar(d: Despesa): Despesa {
        if (d.valor <= 0.0 || !d.valor.isFinite()) throw RegraFinanceiraException("O valor deve ser maior que zero.")
        var resultado = normalizar(d)
        val cartaoId = resultado.cartaoId
        if (cartaoId != null) {
            val cartao = cartaoDao.getCartaoPorId(cartaoId)
                ?: throw RegraFinanceiraException("Cartão não encontrado.")
            val conta = contaSaldoDao.obterPorId(cartao.contaId)
                ?: throw RegraFinanceiraException("A conta vinculada ao cartão não existe mais.")
            resultado = resultado.copy(conta = conta.conta)
        } else if (contaSaldoDao.obterPorNumero(resultado.conta) == null) {
            throw RegraFinanceiraException("Conta não encontrada.")
        }
        return resultado
    }

    /* ======================= LANÇAMENTOS (DESPESAS/RECEITAS) ======================= */

    /** Grava um lançamento e atualiza saldo/limite na mesma transação. Retorna o id gerado. */
    suspend fun registrarLancamento(despesa: Despesa): Long {
        val comAutor = completarAutor(despesa)
        return db.withTransaction {
            val d = validar(comAutor)
            val id = despesaDao.inserirDespesa(d)
            recalcularAfetados(listOf(d))
            atualizarSnapshotPatrimonial()
            id
        }
    }

    /** Nome de quem lança (R32): usa o nome do perfil quando o lançamento não traz autor. */
    private suspend fun completarAutor(d: Despesa): Despesa {
        if (!d.autor.isNullOrBlank()) return d
        val nome = runCatching { UserPreferences(context).userNameFlow.first().trim() }.getOrDefault("")
        return if (nome.isBlank()) d else d.copy(autor = nome)
    }

    /** R8 — compra parcelada (parcelas já nascem consistentes com saldo/limite). */
    suspend fun registrarParcelado(modelo: Despesa, parcelas: Int): List<Long> {
        val comAutor = completarAutor(modelo)
        return db.withTransaction {
            if (parcelas !in 1..120) throw RegraFinanceiraException("Número de parcelas inválido (1 a 120).")
            val base = validar(comAutor)
            val lista = Financas.parcelar(base, parcelas, agora())
            val ids = lista.map { despesaDao.inserirDespesa(it) }
            recalcularAfetados(lista)
            atualizarSnapshotPatrimonial()
            ids
        }
    }

    /** R23 — duplica um lançamento comum para hoje (em aberto). */
    suspend fun duplicarLancamento(id: Long): Long {
        val original = despesaDao.obterDespesaPorId(id) ?: throw RegraFinanceiraException("Lançamento não encontrado.")
        val copia = Analises.duplicar(original, agora())
            ?: throw RegraFinanceiraException("Este tipo de lançamento não pode ser duplicado.")
        return registrarLancamento(copia.copy(autor = null))
    }

    /** R23 — cria [n] repetições futuras do lançamento (a cada [intervalo] dias, semanas ou meses). */
    suspend fun repetirLancamento(id: Long, n: Int, intervalo: Int, unidade: Analises.UnidadeRepeticao): Int {
        val original = despesaDao.obterDespesaPorId(id) ?: throw RegraFinanceiraException("Lançamento não encontrado.")
        if (original.natureza != Natureza.NORMAL) throw RegraFinanceiraException("Este tipo de lançamento não pode ser repetido.")
        val lista = try {
            Analises.repetir(original, n, intervalo, unidade)
        } catch (e: IllegalArgumentException) {
            throw RegraFinanceiraException(e.message ?: "Repetição inválida.")
        }
        return db.withTransaction {
            val validas = lista.map { validar(it) }
            validas.forEach { despesaDao.inserirDespesa(it) }
            recalcularAfetados(validas)
            atualizarSnapshotPatrimonial()
            validas.size
        }
    }

    suspend fun atualizarLancamento(despesa: Despesa) = db.withTransaction {
        val antigo = despesaDao.obterDespesaPorId(despesa.id)
            ?: throw RegraFinanceiraException("Lançamento não encontrado.")
        val novo = validar(despesa)
        despesaDao.inserirDespesa(novo)
        recalcularAfetados(listOf(antigo, novo))
        atualizarSnapshotPatrimonial()
    }

    /** R11 — exclui o lançamento (e o par, se for transferência) e recalcula os derivados. */
    suspend fun excluirLancamento(id: Long) = db.withTransaction {
        val d = despesaDao.obterDespesaPorId(id) ?: return@withTransaction
        if (d.natureza == Natureza.PAGAMENTO_FATURA) {
            throw RegraFinanceiraException(
                "O pagamento de fatura não pode ser excluído. Para reabrir a fatura, marque as compras como não pagas."
            )
        }
        val afetados = if (d.natureza == Natureza.TRANSFERENCIA && d.grupoId != null) {
            despesaDao.obterDoGrupo(d.grupoId).also { despesaDao.excluirPorGrupo(d.grupoId) }
        } else {
            despesaDao.excluirPorId(id)
            listOf(d)
        }
        // R31: guarda 30 dias para desfazer (o saldo inicial não vai para a lixeira).
        afetados.filter { it.natureza != Natureza.SALDO_INICIAL }.forEach { lixeiraDao.inserir(paraLixeira(it)) }
        recalcularAfetados(afetados)
        atualizarSnapshotPatrimonial()
    }

    /** R12 — alterna pago/pendente. Em compra de cartão só o limite muda; em conta, o saldo. */
    suspend fun alternarPago(id: Long, pago: Boolean) = db.withTransaction {
        val d = despesaDao.obterDespesaPorId(id) ?: return@withTransaction
        if (d.pago == pago) return@withTransaction
        despesaDao.atualizarPago(id, pago)
        recalcularAfetados(listOf(d))
        atualizarSnapshotPatrimonial()
    }

    /* ======================= LIXEIRA (R31) ======================= */

    private val gsonLixeira = com.google.gson.Gson()

    private fun paraLixeira(d: Despesa) = Lixeira(
        descricao = d.descricao, valor = d.valor, excluidoEm = agora(),
        payload = gsonLixeira.toJson(com.meudinheiro.storage.FirestoreMapper.toDoc(d))
    )

    @Suppress("UNCHECKED_CAST")
    private fun deLixeira(item: Lixeira): Despesa =
        com.meudinheiro.storage.FirestoreMapper.despesaFromDoc(gsonLixeira.fromJson(item.payload, Map::class.java) as Map<String, Any?>)

    fun lixeiraFlow(): Flow<List<Lixeira>> = lixeiraDao.observar()

    /** Restaura o lançamento (e o par, se for transferência), recalculando saldo e limite. */
    suspend fun restaurarDaLixeira(id: Int): Unit = db.withTransaction {
        val item = lixeiraDao.obter(id) ?: return@withTransaction
        val primeiro = deLixeira(item)
        val itens = if (primeiro.natureza == Natureza.TRANSFERENCIA && primeiro.grupoId != null) {
            lixeiraDao.obterTodasStatic().filter { deLixeira(it).grupoId == primeiro.grupoId }
        } else listOf(item)
        val despesas = itens.map(::deLixeira)

        despesas.forEach { d ->
            contaSaldoDao.obterPorNumero(d.conta) ?: throw RegraFinanceiraException("A conta deste lançamento não existe mais.")
            d.cartaoId?.let { cartaoDao.getCartaoPorId(it) ?: throw RegraFinanceiraException("O cartão deste lançamento não existe mais.") }
        }
        despesas.forEach { d ->
            val idLivre = d.id != 0L && despesaDao.obterDespesaPorId(d.id) == null
            despesaDao.inserirDespesa(normalizar(if (idLivre) d else d.copy(id = 0)))
        }
        lixeiraDao.excluirPorIds(itens.map { it.id })
        recalcularAfetados(despesas)
        atualizarSnapshotPatrimonial()
    }

    suspend fun excluirDaLixeira(id: Int) = lixeiraDao.excluirPorIds(listOf(id))
    suspend fun esvaziarLixeira() = lixeiraDao.limpar()

    /** Remove definitivamente o que está na lixeira há mais de 30 dias. */
    suspend fun purgarLixeira() = lixeiraDao.purgarAnteriores(agora() - 30L * 86_400_000L)

    // Nomes legados (mantidos para não quebrar chamadas existentes).
    suspend fun inserirDespesa(despesa: Despesa) { registrarLancamento(despesa) }
    suspend fun excluirDespesa(id: Int) = excluirLancamento(id.toLong())
    suspend fun excluirDespesaComRestituicao(id: Int) = excluirLancamento(id.toLong())
    suspend fun marcarDespesaComoPaga(id: Int, pago: Boolean) = alternarPago(id.toLong(), pago)
    suspend fun atualizarStatusPago(id: Long, status: Boolean) = alternarPago(id, status)
    suspend fun recalcularSaldoTotal(contaNome: String) = db.withTransaction { recalcularConta(contaNome) }

    suspend fun obterDespesaPorId(id: Long): Despesa? = despesaDao.obterDespesaPorId(id)

    fun obterDespesas(): Flow<List<DespesasDomain>> = despesaDao.obterDespesas()

    fun obterDespesasPorContaFlow(contaId: String): Flow<List<DespesasDomain>> =
        despesaDao.obterDespesasPorContaFlow(contaId)

    val todasDespesasFlow: Flow<List<Despesa>> get() = despesaDao.obterTodasFlow()

    fun getDespesasPorCartao(cartaoId: Int): Flow<List<Despesa>> = despesaDao.getDespesasPorCartao(cartaoId)

    /* ======================= CONTAS ======================= */

    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>> = contaSaldoDao.obterContaSaldo()

    fun getTodasContas(): Flow<List<ContaSaldo>> = contaSaldoDao.getTodasContas()

    suspend fun obterTodasStatic(): List<ContaSaldo> = contaSaldoDao.obterTodasStatic()

    suspend fun obterSaldoPorConta(conta: String): Double = contaSaldoDao.obterSaldoPorConta(conta) ?: 0.0

    /**
     * Cria/atualiza uma conta. Em conta nova, o saldo informado vira um lançamento SALDO_INICIAL
     * (o saldo passa a ser sempre derivado do extrato — R3).
     */
    suspend fun salvarConta(conta: ContaSaldo): Unit = db.withTransaction {
        val numero = conta.conta.trim()
        if (conta.banco.isBlank()) throw RegraFinanceiraException("Informe o banco.")
        if (numero.isEmpty()) throw RegraFinanceiraException("Informe o número da conta.")
        val existente = contaSaldoDao.obterPorNumero(numero)
        if (existente != null && existente.id != conta.id) {
            throw RegraFinanceiraException("Já existe uma conta com o número $numero.")
        }

        val saldoInformado = Dinheiro.arredondar(conta.saldo)
        val nova = conta.id == 0
        contaSaldoDao.inserirContaSaldo(
            conta.copy(conta = numero, saldo = if (nova) 0.0 else conta.saldo)
        )
        if (nova && saldoInformado != 0.0) {
            val ms = agora()
            despesaDao.inserirDespesa(
                Despesa(
                    descricao = "Saldo Inicial",
                    valor = kotlin.math.abs(saldoInformado),
                    data = Date(ms),
                    categoria = "Outros",
                    conta = numero,
                    pic = "deposit",
                    tipo = if (saldoInformado > 0) TipoDespesa.CREDITO else TipoDespesa.DEBITO,
                    mes = Financas.mesDe(ms),
                    ano = Financas.anoDe(ms),
                    pago = true,
                    natureza = Natureza.SALDO_INICIAL
                ).let(::normalizar)
            )
        }
        recalcularConta(numero)
        atualizarSnapshotPatrimonial()
    }

    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo) = salvarConta(contaSaldo)

    /** Exclui a conta com tudo que depende dela: lançamentos, cartões (e suas compras) e agendamentos. */
    suspend fun excluirConta(id: Int) = db.withTransaction {
        val conta = contaSaldoDao.obterPorId(id) ?: return@withTransaction
        val cartoes = cartaoDao.obterPorConta(id).map { it.id }
        despesaDao.excluirDaContaECartoes(conta.conta, cartoes.ifEmpty { listOf(-1) })
        despesaFixaDao.excluirDaConta(conta.conta)
        contaSaldoDao.excluirAgendamentosDaConta(conta.conta)
        contaSaldoDao.excluirConta(id) // ON DELETE CASCADE remove os cartões
        atualizarSnapshotPatrimonial()
    }

    /* ======================= TRANSFERÊNCIAS (R9) ======================= */

    /** Debita a origem e credita o destino com 2 lançamentos ligados por `grupoId`. */
    suspend fun transferirEntreContas(origem: String, destino: String, valor: Double): Boolean = db.withTransaction {
        val v = Dinheiro.arredondar(valor)
        if (v <= 0.0) throw RegraFinanceiraException("O valor da transferência deve ser maior que zero.")
        if (origem.trim() == destino.trim()) throw RegraFinanceiraException("As contas de origem e destino são iguais.")
        val contaOrigem = contaSaldoDao.obterPorNumero(origem) ?: throw RegraFinanceiraException("Conta de origem não encontrada.")
        val contaDestino = contaSaldoDao.obterPorNumero(destino) ?: throw RegraFinanceiraException("Conta de destino não encontrada.")

        val saldoOrigem = Financas.saldoConta(contaOrigem.conta, despesaDao.obterDaConta(contaOrigem.conta))
        if (saldoOrigem < v) {
            throw RegraFinanceiraException("Saldo insuficiente na conta ${contaOrigem.banco} para transferir este valor.")
        }

        val ms = agora()
        val grupo = "transf:${UUID.randomUUID()}"
        fun lancamento(conta: ContaSaldo, tipo: TipoDespesa, descricao: String) = normalizar(
            Despesa(
                descricao = descricao, valor = v, data = Date(ms), categoria = "Transferência",
                conta = conta.conta, pic = "bank", tipo = tipo, mes = 0, ano = 0, pago = true,
                natureza = Natureza.TRANSFERENCIA, grupoId = grupo
            )
        )
        despesaDao.inserirDespesa(lancamento(contaOrigem, TipoDespesa.DEBITO, "Transferência para ${contaDestino.banco}"))
        despesaDao.inserirDespesa(lancamento(contaDestino, TipoDespesa.CREDITO, "Transferência de ${contaOrigem.banco}"))
        recalcularConta(contaOrigem.conta)
        recalcularConta(contaDestino.conta)
        true
    }

    fun obterAgendamentosPendentesFlow(): Flow<List<TransferenciaAgendada>> = contaSaldoDao.obterAgendamentosAtivos()
    fun obterAgendamentosAtivos(): Flow<List<TransferenciaAgendada>> = contaSaldoDao.obterAgendamentosAtivos()

    suspend fun inserirAgendamento(agendamento: TransferenciaAgendada): Long {
        if (agendamento.valor <= 0.0) throw RegraFinanceiraException("O valor deve ser maior que zero.")
        return contaSaldoDao.inserirAgendamento(agendamento.copy(valor = Dinheiro.arredondar(agendamento.valor)))
    }

    suspend fun excluirAgendamento(id: Int) = contaSaldoDao.excluirAgendamento(id)

    suspend fun obterAgendamentosPendentesSync(hoje: Long): List<TransferenciaAgendada> =
        contaSaldoDao.obterAgendamentosPendentesSync(hoje)

    suspend fun marcarAgendamentoComoExecutado(id: Int) = contaSaldoDao.marcarAgendamentoComoExecutado(id)

    /** Executa um agendamento vencido: só marca como executado se a transferência realmente aconteceu. */
    suspend fun executarAgendamento(agendamento: TransferenciaAgendada): ResultadoAgendamento = try {
        db.withTransaction {
            transferirEntreContas(agendamento.contaOrigem, agendamento.contaDestino, agendamento.valor)
            contaSaldoDao.marcarAgendamentoComoExecutado(agendamento.id)
        }
        ResultadoAgendamento.Executado
    } catch (e: RegraFinanceiraException) {
        ResultadoAgendamento.Falhou(e.message ?: "Regra financeira violada")
    }

    /* ======================= CARTÕES (R4, R6, R7) ======================= */

    fun cartoesFlow(): Flow<List<Cartao>> = cartaoDao.obterTodosFlow()
    fun investimentosFlow(): Flow<List<com.meudinheiro.data.Investimento>> = investimentoDao.getTodosInvestimentos()

    fun getTodosOsCartoes(): Flow<List<CartaoComConta>> = cartaoDao.getCartoesComConta()

    suspend fun buscarCartaoPorId(id: Int): Cartao? = cartaoDao.getCartaoPorId(id)

    /** Compras de todos os cartões do mesmo grupo (físico + virtuais) de [cartaoId], em fluxo reativo. */
    fun getDespesasDoGrupoDe(cartaoId: Int): Flow<List<Despesa>> =
        combine(cartaoDao.obterTodosFlow(), despesaDao.obterTodasFlow()) { cartoes, despesas ->
            val alvo = cartoes.firstOrNull { it.id == cartaoId }
            val principal = alvo?.let { a -> cartoes.firstOrNull { it.id == a.idDoGrupo } }
            val ids = principal?.let { Financas.idsDoGrupo(it, cartoes) } ?: setOf(cartaoId)
            despesas.filter { it.cartaoId in ids }.sortedByDescending { it.dataMs }
        }

    /**
     * Salva um cartão. Cartão VIRTUAL (R18) herda conta, limite, fechamento/vencimento e tipo do físico a que
     * pertence; editar o físico propaga essas informações aos virtuais.
     */
    suspend fun salvarCartao(cartao: Cartao): Unit = db.withTransaction {
        if (cartao.nome.isBlank()) throw RegraFinanceiraException("Informe o nome do cartão.")
        var c = cartao

        val principalId = c.cartaoPrincipalId
        if (principalId != null) {
            if (principalId == c.id) throw RegraFinanceiraException("Um cartão não pode ser virtual de si mesmo.")
            val principal = cartaoDao.getCartaoPorId(principalId)
                ?: throw RegraFinanceiraException("Selecione o cartão físico ao qual o cartão virtual pertence.")
            if (principal.cartaoPrincipalId != null) {
                throw RegraFinanceiraException("Um cartão virtual precisa estar ligado a um cartão físico (não a outro virtual).")
            }
            c = c.copy(
                contaId = principal.contaId, limiteTotal = principal.limiteTotal,
                diaFechamento = principal.diaFechamento, diaVencimento = principal.diaVencimento, tipo = principal.tipo
            )
        } else if (c.id != 0) {
            val anterior = cartaoDao.getCartaoPorId(c.id)
            if (anterior?.cartaoPrincipalId != null) {
                throw RegraFinanceiraException("Este cartão é virtual: exclua-o e crie um cartão físico novo.")
            }
        }

        if (c.limiteTotal < 0) throw RegraFinanceiraException("O limite não pode ser negativo.")
        if (c.diaFechamento !in 1..31 || c.diaVencimento !in 1..31) {
            throw RegraFinanceiraException("Dias de fechamento e vencimento devem estar entre 1 e 31.")
        }
        contaSaldoDao.obterPorId(c.contaId) ?: throw RegraFinanceiraException("Selecione a conta vinculada ao cartão.")

        cartaoDao.inserirCartao(
            c.copy(limiteTotal = Dinheiro.arredondar(c.limiteTotal), limiteDisponivel = Dinheiro.arredondar(c.limiteTotal))
        )
        if (c.cartaoPrincipalId == null && c.id != 0) {
            cartaoDao.propagarParaVirtuais(c.id, c.contaId, Dinheiro.arredondar(c.limiteTotal), c.diaFechamento, c.diaVencimento, c.tipo)
        }
        cartaoDao.obterTodasStatic().forEach { recalcularCartao(it.id) }
    }

    /** Virtual: só sem compras em aberto (histórico passa ao físico). Físico: grupo inteiro sem compras em aberto. */
    suspend fun excluirCartao(cartao: Cartao): Unit = db.withTransaction {
        val atual = cartaoDao.getCartaoPorId(cartao.id) ?: return@withTransaction
        val principalId = atual.cartaoPrincipalId
        // Recorrências pagas neste cartão passam a debitar direto na conta do cartão (nada é perdido).
        despesaFixaDao.desvincularCartoes(cartaoDao.obterGrupo(atual.idDoGrupo).filter { it.id == atual.id || atual.cartaoPrincipalId == null }.map { it.id })
        if (principalId != null) {
            if (despesaDao.obterDoCartao(atual.id).any { !it.pago }) {
                throw RegraFinanceiraException("Este cartão virtual tem compras em aberto. Pague a fatura antes de excluí-lo.")
            }
            despesaDao.reatribuirCartao(atual.id, principalId)
            cartaoDao.deletarCartao(atual)
            return@withTransaction
        }
        val grupo = cartaoDao.obterGrupo(atual.id)
        val compras = despesaDao.obterDosCartoes(grupo.map { it.id })
        if (compras.any { !it.pago }) {
            throw RegraFinanceiraException("Este cartão (ou um virtual dele) tem compras em aberto. Pague as faturas antes de excluí-lo.")
        }
        compras.forEach { despesaDao.excluirPorId(it.id) }
        grupo.filter { it.id != atual.id }.forEach { cartaoDao.deletarCartao(it) }
        cartaoDao.deletarCartao(atual)
    }

    /**
     * R7 — paga a fatura [ref] do cartão com saldo da conta vinculada. As compras da fatura passam a
     * "pagas" (liberando o limite) e um único lançamento PAGAMENTO_FATURA debita a conta.
     * Retorna o valor pago.
     */
    suspend fun pagarFatura(cartaoId: Int, ref: Financas.FaturaRef): Double = db.withTransaction {
        val escolhido = cartaoDao.getCartaoPorId(cartaoId) ?: throw RegraFinanceiraException("Cartão não encontrado.")
        // R18: a fatura é do grupo; pagar por qualquer cartão (físico ou virtual) quita a fatura do físico.
        val cartao = cartaoDao.getCartaoPorId(escolhido.idDoGrupo) ?: throw RegraFinanceiraException("Cartão físico não encontrado.")
        val ids = cartaoDao.obterGrupo(cartao.id).map { it.id }.toSet()
        val conta = contaSaldoDao.obterPorId(cartao.contaId) ?: throw RegraFinanceiraException("Conta do cartão não encontrada.")
        val resumo = Financas.resumoFatura(cartao, despesaDao.obterDosCartoes(ids.toList()), ref, ids)
        if (resumo.pendente <= 0.0) throw RegraFinanceiraException("Esta fatura não possui valor pendente.")

        val ms = agora()
        despesaDao.marcarComoPagas(resumo.itensPendentes.map { it.id })
        despesaDao.inserirDespesa(
            normalizar(
                Despesa(
                    descricao = "Pagamento fatura ${cartao.nome} %02d/%d".format(ref.mes, ref.ano),
                    valor = resumo.pendente, data = Date(ms), categoria = "Cartão", conta = conta.conta,
                    pic = "payments", tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true,
                    natureza = Natureza.PAGAMENTO_FATURA,
                    grupoId = "fatura:${cartao.id}:${ref.ano}-%02d".format(ref.mes)
                )
            )
        )
        recalcularConta(conta.conta)
        recalcularCartao(cartao.id)
        atualizarSnapshotPatrimonial()
        resumo.pendente
    }

    /** "Dar baixa" numa pendência: conta comum → marca paga; compra de cartão → paga a fatura inteira dela. */
    suspend fun baixarPendencia(item: Despesa) {
        val cartaoId = item.cartaoId
        if (cartaoId == null) {
            alternarPago(item.id, true)
        } else {
            val cartao = cartaoDao.getCartaoPorId(cartaoId) ?: return
            pagarFatura(cartaoId, Financas.faturaDaCompra(item.dataMs, cartao.diaFechamento))
        }
    }

    /* ======================= RECORRÊNCIAS (R16) ======================= */

    suspend fun salvarDespesaFixa(despesaFixa: DespesaFixa) {
        if (despesaFixa.diaVencimento !in 1..31) throw RegraFinanceiraException("O dia de vencimento deve estar entre 1 e 31.")
        if (despesaFixa.valor <= 0) throw RegraFinanceiraException("O valor deve ser maior que zero.")
        despesaFixaDao.inserir(resolverOrigem(despesaFixa).copy(valor = Dinheiro.arredondar(despesaFixa.valor)))
        processarRecorrencias()
    }

    /** Cartão como origem (R16): a conta da regra passa a ser a do cartão; cartão inexistente é recusado. */
    private suspend fun resolverOrigem(regra: DespesaFixa): DespesaFixa {
        val cartaoId = regra.cartaoId?.takeIf { it != 0 } ?: return regra.copy(cartaoId = null)
        val cartao = cartaoDao.getCartaoPorId(cartaoId) ?: throw RegraFinanceiraException("Cartão não encontrado.")
        val conta = contaSaldoDao.obterPorId(cartao.contaId) ?: throw RegraFinanceiraException("A conta do cartão não existe mais.")
        return regra.copy(cartaoId = cartaoId, conta = conta.conta)
    }

    /** Troca a origem do pagamento: cartão (físico/virtual) ou conta — exatamente uma das duas. */
    suspend fun alterarOrigemRecorrencia(id: Int, conta: String?, cartaoId: Int?): Unit = db.withTransaction {
        val regra = despesaFixaDao.obterPorId(id) ?: throw RegraFinanceiraException("Recorrência não encontrada.")
        val nova = if (cartaoId != null) {
            resolverOrigem(regra.copy(cartaoId = cartaoId))
        } else {
            val c = conta?.let { contaSaldoDao.obterPorNumero(it) } ?: throw RegraFinanceiraException("Escolha uma conta ou um cartão.")
            regra.copy(cartaoId = null, conta = c.conta)
        }
        despesaFixaDao.atualizar(nova)
    }

    /** Lança as ocorrências vencidas (inclusive meses perdidos com o app fechado), sem duplicar. */
    suspend fun processarRecorrencias(): Unit = db.withTransaction {
        val hoje = agora()
        despesaFixaDao.obterTodas().forEach { regra ->
            val datas = Financas.ocorrenciasPendentes(regra, hoje)
            if (datas.isEmpty()) return@forEach
            // Origem válida? (cartão → conta do cartão; conta → precisa existir). Senão a regra espera, sem perder meses.
            val origem = runCatching { resolverOrigem(regra) }.getOrNull() ?: return@forEach
            if (contaSaldoDao.obterPorNumero(origem.conta) == null) return@forEach
            datas.forEach { ms ->
                despesaDao.inserirDespesa(
                    normalizar(
                        Despesa(
                            descricao = regra.descricao, valor = regra.valor, data = Date(ms),
                            categoria = regra.categoria, conta = origem.conta, pic = regra.pic,
                            tipo = regra.tipo, mes = 0, ano = 0, pago = false, cartaoId = origem.cartaoId
                        )
                    )
                )
            }
            origem.cartaoId?.let { recalcularCartao(it) } // a compra no cartão consome limite (R4/R18)
            despesaFixaDao.atualizar(origem.copy(ultimaDataLancamento = Date(datas.last())))
        }
    }

    suspend fun obterTodasRecorrencias(): List<DespesaFixa> = despesaFixaDao.obterTodas()
    suspend fun excluirRecorrencia(id: Int) = despesaFixaDao.excluir(id)

    /* ======================= METAS (R10) ======================= */

    fun getTodasMetas(): Flow<List<Meta>> = metaDao.getTodasMetas()
    suspend fun obterTodasAsMetasSync(): List<Meta> = metaDao.obterTodasAsMetasSync()
    fun getTotalPoupado(): Flow<Double> = metaDao.getTotalPoupado().map { Dinheiro.arredondar(it ?: 0.0) }

    suspend fun salvarMeta(meta: Meta) {
        if (meta.nome.isBlank()) throw RegraFinanceiraException("Informe o nome da meta.")
        if (meta.valorObjetivo <= 0) throw RegraFinanceiraException("O objetivo deve ser maior que zero.")
        metaDao.salvarMeta(
            meta.copy(
                valorObjetivo = Dinheiro.arredondar(meta.valorObjetivo),
                valorGuardado = Dinheiro.arredondar(meta.valorGuardado)
            )
        )
    }

    suspend fun excluirMeta(meta: Meta) = metaDao.excluirMeta(meta)

    /** Tira [valor] da conta e guarda na meta (lançamento APORTE_META). */
    suspend fun realizarAporte(meta: Meta, contaId: String, valor: Double): Unit = db.withTransaction {
        val v = Dinheiro.arredondar(valor)
        if (v <= 0.0) throw RegraFinanceiraException("O valor do aporte deve ser maior que zero.")
        val conta = contaSaldoDao.obterPorNumero(contaId) ?: throw RegraFinanceiraException("Selecione uma conta de origem.")
        val atual = metaDao.obterPorId(meta.id) ?: throw RegraFinanceiraException("Meta não encontrada.")
        val saldo = Financas.saldoConta(conta.conta, despesaDao.obterDaConta(conta.conta))
        if (saldo < v) throw RegraFinanceiraException("Saldo insuficiente na conta ${conta.banco} para este aporte.")

        val ms = agora()
        metaDao.salvarMeta(atual.copy(valorGuardado = Dinheiro.arredondar(atual.valorGuardado + v)))
        despesaDao.inserirDespesa(
            normalizar(
                Despesa(
                    descricao = "Aporte: ${atual.nome}", valor = v, data = Date(ms), categoria = "Reserva",
                    conta = conta.conta, pic = "reserva", tipo = TipoDespesa.DEBITO, mes = 0, ano = 0,
                    pago = true, natureza = Natureza.APORTE_META
                )
            )
        )
        recalcularConta(conta.conta)
        atualizarSnapshotPatrimonial()
    }

    suspend fun depositarNaMeta(metaId: Int, contaId: String, valor: Double) {
        val meta = metaDao.obterPorId(metaId) ?: throw RegraFinanceiraException("Meta não encontrada.")
        realizarAporte(meta, contaId, valor)
    }

    /** Exclui a meta devolvendo o valor guardado à conta escolhida (RESGATE_META). */
    suspend fun excluirMetaComRestituicao(meta: Meta, contaId: String?): Unit = db.withTransaction {
        val atual = metaDao.obterPorId(meta.id) ?: return@withTransaction
        if (atual.valorGuardado > 0) {
            val conta = contaId?.let { contaSaldoDao.obterPorNumero(it) }
                ?: throw RegraFinanceiraException("Escolha uma conta para receber o valor guardado (${atual.valorGuardado}).")
            val ms = agora()
            despesaDao.inserirDespesa(
                normalizar(
                    Despesa(
                        descricao = "Resgate: ${atual.nome}", valor = atual.valorGuardado, data = Date(ms),
                        categoria = "Reserva", conta = conta.conta, pic = "reserva", tipo = TipoDespesa.CREDITO,
                        mes = 0, ano = 0, pago = true, natureza = Natureza.RESGATE_META
                    )
                )
            )
            recalcularConta(conta.conta)
        }
        metaDao.excluirMeta(atual)
        atualizarSnapshotPatrimonial()
    }

    /* ======================= ORÇAMENTOS / CATEGORIAS ======================= */

    fun obterOrcamentosFlow(): Flow<List<Orcamento>> = orcamentoDao.obterTodosFlow()

    suspend fun salvarOrcamento(categoria: String, valor: Double) {
        if (valor <= 0) throw RegraFinanceiraException("O limite do orçamento deve ser maior que zero.")
        // Um orçamento por categoria: atualiza se já existir (antes duplicava linhas).
        val existente = orcamentoDao.obterTodasStatic().firstOrNull { it.categoria.trim().equals(categoria.trim(), true) }
        orcamentoDao.salvarOrcamento(
            Orcamento(id = existente?.id ?: 0, categoria = categoria.trim(), valorLimite = Dinheiro.arredondar(valor))
        )
    }

    suspend fun excluirOrcamento(categoria: String) = orcamentoDao.excluirPorCategoria(categoria)
    suspend fun atualizarOrcamento(categoria: String, novoValor: Double) =
        orcamentoDao.atualizarPorCategoria(categoria, Dinheiro.arredondar(novoValor))

    val categorias: List<CategoriaDomain> = listOf(
        CategoriaDomain(pic = "fuel", title = "Combustível"),
        CategoriaDomain(pic = "restaurant", title = "Alimentação"),
        CategoriaDomain(pic = "transport", title = "Transporte"),
        CategoriaDomain(pic = "shopping", title = "Compras"),
        CategoriaDomain(pic = "cinema", title = "Cinema"),
        CategoriaDomain(pic = "health", title = "Saúde"),
        CategoriaDomain(pic = "education", title = "Educação"),
        CategoriaDomain(pic = "salary", title = "Salário"),
        CategoriaDomain(pic = "repair_car", title = "Oficina"),
        CategoriaDomain(pic = "supermarket", title = "Supermercado"),
        CategoriaDomain(pic = "gym", title = "Academia"),
        CategoriaDomain(pic = "games", title = "Jogos"),
        CategoriaDomain(pic = "drink", title = "Bebidas"),
        CategoriaDomain(pic = "lunch", title = "Lanche"),
        CategoriaDomain(pic = "reserva", title = "Reserva")
    )

    val bancos: List<BancoDomain> = listOf(
        BancoDomain(id = 1, nome = "Banco do Brasil", pic = "banco_do_brasil"),
        BancoDomain(id = 2, nome = "Bradesco", pic = "bradesco"),
        BancoDomain(id = 3, nome = "Santander", pic = "santander"),
        BancoDomain(id = 4, nome = "Caixa Econômica", pic = "caixa_economica"),
        BancoDomain(id = 5, nome = "Itaú", pic = "itau"),
        BancoDomain(id = 6, nome = "HSBC", pic = "hsbc"),
        BancoDomain(id = 7, nome = "Nubank", pic = "nubank"),
        BancoDomain(id = 8, nome = "C6", pic = "c6"),
        BancoDomain(id = 9, nome = "MercadoPago", pic = "mercado_pago"),
        BancoDomain(id = 10, nome = "Sicoob", pic = "sicoob"),
        BancoDomain(id = 11, nome = "Banco Original", pic = "banco_original"),
        BancoDomain(id = 12, nome = "Banco Pan", pic = "banco_pan"),
        BancoDomain(id = 13, nome = "Banco do Nordeste", pic = "banco_do_nordeste"),
        BancoDomain(id = 14, nome = "Banco Inter", pic = "banco_inter"),
        BancoDomain(id = 15, nome = "Banco Itaú BBA", pic = "banco_itau_bba"),
        BancoDomain(id = 16, nome = "Banco BMG", pic = "banco_bmg")
    )

    fun getPicCategoria(titulo: String): String = categorias.find { it.title == titulo }?.pic ?: "default_pic"
    fun getPicBanco(titulo: String): String = bancos.find { it.nome == titulo }?.pic ?: "default_pic"

    fun obterCategoriasCustom(): Flow<List<CategoriaDomain>> = categoriaDao.obterTodas()
    suspend fun salvarCategoria(categoria: Categoria) = categoriaDao.inserir(categoria)
    suspend fun excluirCategoriaPorNome(nome: String) = categoriaDao.excluirPorNome(nome)

    /* ======================= RESUMOS / KPIs (R5, R13–R15, R17) ======================= */

    /** KPIs reativos do período. `inicio/fim == null` → histórico completo. */
    fun kpisFlow(inicio: Long?, fim: Long?): Flow<Financas.Kpis> =
        despesaDao.obterTodasFlow().map { Financas.kpisPeriodo(it, inicio, fim) }

    suspend fun obterKpis(inicio: Long?, fim: Long?): Financas.Kpis =
        withContext(Dispatchers.IO) { Financas.kpisPeriodo(despesaDao.obterTodasStatic(), inicio, fim) }

    suspend fun obterResumoGlobal(): ResumoDto = obterKpis(null, null).let { ResumoDto(it.receitasRealizadas, it.despesasTotal) }

    suspend fun obterResumoPorPeriodo(inicio: Date, fim: Date): ResumoDto =
        obterKpis(inicio.time, fim.time).let { ResumoDto(it.receitasRealizadas, it.despesasTotal) }

    /** Total de despesas (R5) de um mês 1–12. */
    fun getTotalDespesasPorPeriodo(mes: Int, ano: Int): Flow<Double> =
        kpisFlow(Financas.inicioDoMes(mes, ano), Financas.fimDoMes(mes, ano)).map { it.despesasTotal }

    /** R15 — previsão até o fim do mês corrente, reativa. */
    fun previsaoFlow(): Flow<Financas.Previsao> = combine(
        contaSaldoDao.getTodasContas(), despesaDao.obterTodasFlow(), cartaoDao.obterTodosFlow()
    ) { contas, despesas, cartoes ->
        val agora = agora()
        Financas.previsaoMes(
            saldoAtual = Dinheiro.somar(contas.map { it.saldo }),
            despesas = despesas,
            cartoes = cartoes,
            fimDoMes = Financas.fimDoMes(Financas.mesDe(agora), Financas.anoDe(agora))
        )
    }

    /** R14 — patrimônio líquido, reativo. */
    fun patrimonioLiquidoFlow(): Flow<Double> = combine(
        contaSaldoDao.getTodasContas(), investimentoDao.getTodosInvestimentos(),
        metaDao.getTodasMetas(), despesaDao.obterTodasFlow()
    ) { contas, investimentos, metas, despesas ->
        Financas.patrimonioLiquido(
            contas.map { it.saldo }, investimentos.map { it.valorAtual }, metas.map { it.valorGuardado }, despesas
        )
    }

    /** R14 — um snapshot por mês/ano, atualizado enquanto o mês corre. */
    suspend fun atualizarSnapshotPatrimonial() {
        val contas = contaSaldoDao.obterTodasStatic()
        if (contas.isEmpty()) return
        val total = Financas.patrimonioLiquido(
            contas.map { it.saldo },
            investimentoDao.obterTodasStatic().map { it.valorAtual },
            metaDao.obterTodasStatic().map { it.valorGuardado },
            despesaDao.obterTodasStatic()
        )
        val ms = agora()
        val inicio = Financas.inicioDoMes(Financas.mesDe(ms), Financas.anoDe(ms))
        val fim = Financas.fimDoMes(Financas.mesDe(ms), Financas.anoDe(ms))
        val existente = patrimonioDao.buscarSnapshotNoPeriodo(inicio, fim)
        val rotulo = SimpleDateFormat("MMM", Locale("pt", "BR")).format(Date(ms)).uppercase().replace(".", "").trim()
        patrimonioDao.salvarSnapshot(
            PatrimonioHistorico(id = existente?.id ?: 0, dataMillis = ms, valorTotal = total, mesReferencia = rotulo)
        )
    }

    fun obterHistoricoPatrimonial(): Flow<List<PatrimonioHistorico>> = patrimonioDao.obterHistoricoPatrimonial()
    suspend fun salvarSnapshotPatrimonial(snapshot: PatrimonioHistorico) = patrimonioDao.salvarSnapshot(snapshot)

    /* ======================= PENDÊNCIAS ======================= */

    /**
     * Contas a pagar atrasadas + a vencer em [daysAhead] dias. Com [onlyCredit] ("Filtro Rígido"),
     * lista só compras de cartão cujas faturas vencem na janela (ou já venceram).
     */
    suspend fun listarPendencias(daysAhead: Int, onlyCredit: Boolean): List<Despesa> {
        val (inicio, fim) = janela(daysAhead)
        if (!onlyCredit) {
            val atrasadas = despesaDao.obterPendentesAtrasadasPorTipo(inicio, TipoDespesa.DEBITO)
            val aVencer = despesaDao.obterPendentesVencendoDate(inicio, fim)
            return (atrasadas + aVencer).distinctBy { it.id }.sortedBy { it.data.time }
        }
        val cartoes = cartaoDao.obterTodasStatic().associateBy { it.id }
        return despesaDao.obterTodasStatic()
            .filter { it.cartaoId != null && !it.pago && it.tipo == TipoDespesa.DEBITO }
            .filter { d ->
                val cartao = cartoes[d.cartaoId] ?: return@filter false
                val ref = Financas.faturaDaCompra(d.dataMs, cartao.diaFechamento)
                Financas.dataVencimento(cartao.diaFechamento, cartao.diaVencimento, ref) <= fim.time
            }
            .sortedBy { it.data.time }
    }

    /** Resumo do widget (saldo, metas e contas a pagar em [dias] dias), lido de uma vez do banco. */
    suspend fun resumoParaWidget(dias: Int): WidgetResumo = WidgetResumo.montar(
        saldos = contaSaldoDao.obterTodasStatic().map { it.saldo },
        metas = metaDao.obterTodasAsMetasSync(),
        pendencias = listarPendencias(dias, onlyCredit = false),
        agora = agora(),
        historico = despesaDao.obterTodasStatic(),
        orcamentos = orcamentoDao.obterTodasStatic()
    )

    suspend fun contarPendencias(daysAhead: Int, onlyCredit: Boolean): Int = listarPendencias(daysAhead, onlyCredit).size

    suspend fun getPendentesAVencer(inicio: Date, fim: Date, onlyCredit: Boolean): List<Despesa> =
        if (onlyCredit) despesaDao.obterPendentesVencendoDatePorTipo(inicio, fim, TipoDespesa.CREDITO)
        else despesaDao.obterPendentesVencendoDate(inicio, fim)

    private fun janela(daysAhead: Int): Pair<Date, Date> {
        val inicio = Financas.inicioDoDia(agora())
        val fim = Financas.fimDoDia(inicio + daysAhead * 86_400_000L)
        return Date(inicio) to Date(fim)
    }

    /* ======================= BACKUP / RESTORE ======================= */

    suspend fun gerarBackup(): String = BackupJson.toJson(
        BackupDto(
            categorias = categoriaDao.obterTodasStatic(),
            despesas = despesaDao.obterTodasStatic(),
            despesasFixas = despesaFixaDao.obterTodasStatic(),
            contas = contaSaldoDao.obterTodasStatic(),
            metas = metaDao.obterTodasStatic(),
            orcamentos = orcamentoDao.obterTodasStatic(),
            cartoes = cartaoDao.obterTodasStatic(),
            patrimonio = patrimonioDao.obterTodasStatic(),
            investimentos = investimentoDao.obterTodasStatic(),
            transacao = transacaoDao.obterTodasStatic(),
            transferenciasAgendadas = contaSaldoDao.obterAgendamentosStatic(),
            lixeira = lixeiraDao.obterTodasStatic()
        )
    )

    /**
     * Substitui TODOS os dados pelo backup, preservando ids (os vínculos cartão→conta e despesa→cartão
     * continuam válidos) e recalculando os derivados. Tudo ou nada: falhou, nada muda.
     */
    suspend fun restaurarBackupCompleto(json: String) {
        val backup = try {
            BackupJson.fromJson(json)
        } catch (e: IllegalArgumentException) {
            throw RegraFinanceiraException(e.message ?: "Formato de JSON inválido ou incompatível.")
        }
        val contas = backup.contas.orEmpty()
        val idsContas = contas.map { it.id }.toSet()
        val cartoes = backup.cartoes.orEmpty().filter { it.contaId in idsContas }

        db.withTransaction {
            limparTodasAsTabelas()
            contaSaldoDao.inserirTodas(contas)
            cartaoDao.inserirTodas(cartoes)
            categoriaDao.inserirTodas(backup.categorias.orEmpty())
            despesaDao.inserirTodas(backup.despesas.orEmpty().map(::normalizarRestore))
            despesaFixaDao.inserirTodas(backup.despesasFixas.orEmpty())
            orcamentoDao.inserirTodas(backup.orcamentos.orEmpty())
            metaDao.inserirTodas(backup.metas.orEmpty())
            investimentoDao.inserirTodas(backup.investimentos.orEmpty())
            transacaoDao.inserirTodas(backup.transacao.orEmpty())
            patrimonioDao.inserirTodas(backup.patrimonio.orEmpty())
            contaSaldoDao.inserirAgendamentos(backup.transferenciasAgendadas.orEmpty())
            lixeiraDao.inserirTodas(backup.lixeira.orEmpty())
        }
        recalcularTudo()
    }

    private fun normalizarRestore(d: Despesa): Despesa =
        d.copy(valor = Dinheiro.arredondar(d.valor), mes = Financas.mesDe(d.dataMs), ano = Financas.anoDe(d.dataMs))

    suspend fun limparBancoDeDadosCompleto() = db.withTransaction { limparTodasAsTabelas() }

    /** Ordem: dependentes antes das referenciadas. */
    private suspend fun limparTodasAsTabelas() {
        contaSaldoDao.apagarAgendamentos()
        contaSaldoDao.apagarDespesas()
        contaSaldoDao.apagarCartoes()
        contaSaldoDao.apagarDespesasFixas()
        contaSaldoDao.apagarOrcamentos()
        contaSaldoDao.apagarMetas()
        contaSaldoDao.apagarInvestimentos()
        contaSaldoDao.apagarPatrimonioHistorico()
        contaSaldoDao.apagarTransacoes()
        contaSaldoDao.apagarCategorias()
        contaSaldoDao.apagarContas()
        lixeiraDao.limpar()
    }

    /* ======================= EXPORTAÇÃO / SINAIS ======================= */

    fun exportarExtratoPDF(context: Context, mes: String, ano: Int, despesas: List<DespesasDomain>) =
        ExtratoPdf.exportarECompartilhar(context, mes, ano, despesas)

    suspend fun avisarQueHouveMudanca() {
        _atualizacaoSinal.emit(Unit)
    }

    val atualizacaoSinal get() = _atualizacaoSinal.asSharedFlow()

    companion object {
        // Compartilhado entre todas as instâncias (telas, receivers e workers criam repositórios próprios).
        private val _atualizacaoSinal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }
}
