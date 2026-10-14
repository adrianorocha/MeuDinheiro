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
import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Divida
import com.meudinheiro.data.Pessoa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.Projeto
import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.ResumoDto
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.Autoria
import com.meudinheiro.domain.ConferenciaSaldos
import com.meudinheiro.domain.ConfigGeral
import com.meudinheiro.domain.Projetos
import com.meudinheiro.domain.Regras
import com.meudinheiro.domain.Tags
import com.meudinheiro.domain.Texto
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Dividas
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Rateios
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.LancamentoAcoes
import com.meudinheiro.domain.WidgetResumo
import com.meudinheiro.domain.Natureza
import com.meudinheiro.domain.centavosAssinados
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

/** Uma correção de saldo de conta aplicada por [MainRepository.recalcularSaldos]. */
data class CorrecaoConta(val conta: String, val banco: String, val antes: Double, val depois: Double)

/** Uma correção do limite disponível de um cartão (o limite é do grupo; cada cartão do grupo é gravado). */
data class CorrecaoCartao(val cartaoId: Int, val nome: String, val antes: Double, val depois: Double)

data class ResultadoRecalculo(val contas: List<CorrecaoConta>, val cartoes: List<CorrecaoCartao>) {
    val saldosCorrigidos: Int get() = contas.size
    val limitesCorrigidos: Int get() = cartoes.size
    val totalCorrecoes: Int get() = contas.size + cartoes.size
    val nenhumaDiferenca: Boolean get() = totalCorrecoes == 0
}

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
    private val regraDao = db.regraCategoriaDao()
    private val projetoDao = db.projetoDao()
    private val dividaDao = db.dividaDao()
    private val pessoaDao = db.pessoaDao()
    private val acertoDao = db.acertoDao()
    private val configuracaoLocal by lazy { com.meudinheiro.storage.ConfiguracaoLocal(db, UserPreferences(context)) }

    private fun agora() = System.currentTimeMillis()

    /** R66 — trilha local de alterações (`files/trilha/alteracoes.jsonl`). Falha de log NUNCA derruba a operação. */
    private val trilha by lazy { com.meudinheiro.domain.TrilhaArquivo(java.io.File(context.filesDir, "trilha/alteracoes.jsonl")) }

    private inline fun registrarTrilha(montar: () -> com.meudinheiro.domain.EntradaTrilha) {
        try { trilha.registrarSeguro(montar()) } catch (_: Throwable) { /* auditoria é só informativa */ }
    }

    /** R66 — registra na trilha um evento que não passa pelas operações de lançamento (ex.: restauração de backup). */
    fun registrarEventoNaTrilha(entrada: com.meudinheiro.domain.EntradaTrilha) = registrarTrilha { entrada }

    /** R66 — leitura da trilha (mais recentes primeiro). */
    fun lerTrilha(): List<com.meudinheiro.domain.EntradaTrilha> = trilha.ler()

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

    /** Conferência somente leitura: compara os caches gravados com o que o extrato calcula (nada é alterado). */
    suspend fun conferirSaldos(inicio: Long? = null, fim: Long? = null): ConferenciaSaldos.Relatorio =
        withContext(Dispatchers.IO) {
            ConferenciaSaldos.auditar(
                contaSaldoDao.obterTodasStatic(), cartaoDao.obterTodasStatic(), despesaDao.obterTodasStatic(), agora(), inicio, fim
            )
        }

    /**
     * Recalcula saldos das contas (R3) e limites dos cartões (R4/R18) a partir do extrato, em uma transação,
     * e atualiza o snapshot patrimonial. Não apaga nem altera lançamentos. Idempotente: a 2ª chamada devolve 0 correções.
     */
    suspend fun recalcularSaldos(): ResultadoRecalculo = withContext(Dispatchers.IO) {
        val resultado = db.withTransaction {
            val rel = ConferenciaSaldos.auditar(
                contaSaldoDao.obterTodasStatic(), cartaoDao.obterTodasStatic(), despesaDao.obterTodasStatic(), agora()
            )
            val contas = rel.contas.filter { it.divergente }.map {
                contaSaldoDao.atualizarSaldo(it.conta, it.saldoCalculado)
                CorrecaoConta(it.conta, it.banco, it.saldoGravado, it.saldoCalculado)
            }
            val cartoes = rel.cartoes.flatMap { g ->
                g.gravados.filter { it.limiteGravado != g.limiteCalculado }.map {
                    cartaoDao.atualizarLimite(it.cartaoId, g.limiteCalculado)
                    CorrecaoCartao(it.cartaoId, it.nome, it.limiteGravado, g.limiteCalculado)
                }
            }
            atualizarSnapshotPatrimonial()
            ResultadoRecalculo(contas, cartoes)
        }
        // O Room já reemite os Flows ao gravar; o sinal avisa também quem escuta o repositório.
        avisarQueHouveMudanca()
        resultado
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

    /**
     * R42 — compra com cartão: CRÉDITO consome limite/entra na fatura; DÉBITO vira lançamento direto na conta
     * do cartão (sem cartão, pago conforme a data); MÚLTIPLO segue [pedida] (padrão crédito).
     */
    private suspend fun comModalidade(d: Despesa, pedida: Financas.Modalidade?): Despesa {
        val id = d.cartaoId?.takeIf { it != 0 } ?: return d
        val cartao = cartaoDao.getCartaoPorId(id) ?: throw RegraFinanceiraException("Cartão não encontrado.")
        val conta = contaSaldoDao.obterPorId(cartao.contaId)
            ?: throw RegraFinanceiraException("A conta vinculada ao cartão não existe mais.")
        return Financas.aplicarModalidade(d, cartao, conta.conta, pedida, agora())
    }

    /* ======================= LANÇAMENTOS (DESPESAS/RECEITAS) ======================= */

    /** Grava um lançamento e atualiza saldo/limite na mesma transação. Retorna o id gerado. */
    suspend fun registrarLancamento(despesa: Despesa, modalidade: Financas.Modalidade? = null): Long {
        val id = registrarLancamentoBase(despesa, modalidade)
        registrarTrilha { com.meudinheiro.domain.Trilha.criar(agora(), despesa.copy(id = id)) }
        return id
    }

    private suspend fun registrarLancamentoBase(despesa: Despesa, modalidade: Financas.Modalidade?): Long {
        val comAutor = completarAutor(despesa)
        return db.withTransaction {
            val d = validar(comModalidade(comAutor, modalidade))
            val id = despesaDao.inserirDespesa(d)
            recalcularAfetados(listOf(d))
            atualizarSnapshotPatrimonial()
            id
        }
    }

    /** Nome de quem lança (R32) e uid (R67, só no modo nuvem): preenche o que o lançamento não traz. */
    private suspend fun completarAutor(d: Despesa): Despesa {
        val nome = runCatching { UserPreferences(context).userNameFlow.first().trim() }.getOrDefault("")
        return Autoria.completar(d, nome, uidAutor())
    }

    /** Lançamento criado por duplicar/repetir: o autor é quem executa (R67). */
    private suspend fun completarAutorExecutor(d: Despesa): Despesa =
        completarAutor(d.copy(autor = null, autorUid = null))

    /** uid do usuário logado na nuvem; vazio (null) em modo local-only ou sem Firebase. */
    private suspend fun uidAutor(): String? = runCatching {
        if (UserPreferences(context).storageModeFlow.first() != com.meudinheiro.storage.StorageMode.FIREBASE) null
        else com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }.getOrNull()

    /** R8 — compra parcelada (parcelas já nascem consistentes com saldo/limite). */
    suspend fun registrarParcelado(
        modelo: Despesa, parcelas: Int, modalidade: Financas.Modalidade? = null, parcelaAtual: Int = 1
    ): List<Long> {
        val ids = registrarParceladoBase(modelo, parcelas, modalidade, parcelaAtual)
        registrarTrilha { com.meudinheiro.domain.Trilha.criar(agora(), modelo.copy(id = ids.firstOrNull() ?: 0L), if (ids.size > 1) "${ids.size} parcelas" else null) }
        return ids
    }

    private suspend fun registrarParceladoBase(
        modelo: Despesa, parcelas: Int, modalidade: Financas.Modalidade?, parcelaAtual: Int
    ): List<Long> {
        val comAutor = completarAutor(modelo)
        return db.withTransaction {
            if (parcelas !in 1..120) throw RegraFinanceiraException("Número de parcelas inválido (1 a 120).")
            if (parcelaAtual !in 1..parcelas) throw RegraFinanceiraException("A parcela atual deve estar entre 1 e $parcelas.")
            val resolvida = comModalidade(comAutor, modalidade)
            val naConta = comAutor.cartaoId?.takeIf { it != 0 } != null && resolvida.cartaoId == null // R42: débito não parcela
            val base = validar(resolvida)
            val lista = if (naConta) Financas.parcelar(base, 1, agora())
            else Financas.parcelar(base, parcelas, agora(), aPartirDe = parcelaAtual)
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
        return registrarLancamento(copia.copy(autor = null, autorUid = null))
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
        val doExecutor = lista.map { completarAutorExecutor(it) }
        return db.withTransaction {
            val validas = doExecutor.map { validar(it) }
            validas.forEach { despesaDao.inserirDespesa(it) }
            recalcularAfetados(validas)
            atualizarSnapshotPatrimonial()
            validas.size
        }
    }

    suspend fun atualizarLancamento(despesa: Despesa, modalidade: Financas.Modalidade? = null) {
        val antes = runCatching { despesaDao.obterDespesaPorId(despesa.id) }.getOrNull()
        atualizarLancamentoBase(despesa, modalidade)
        if (antes != null) registrarTrilha {
            val depois = runCatching { despesaDao.obterDespesaPorId(despesa.id) }.getOrNull() ?: despesa
            com.meudinheiro.domain.Trilha.editar(agora(), antes, depois)
        }
    }

    private suspend fun atualizarLancamentoBase(despesa: Despesa, modalidade: Financas.Modalidade?) = db.withTransaction {
        val antigo = despesaDao.obterDespesaPorId(despesa.id)
            ?: throw RegraFinanceiraException("Lançamento não encontrado.")
        if (antigo.natureza in LancamentoAcoes.BLOQUEADAS_EDICAO) throw RegraFinanceiraException(LancamentoAcoes.MSG_NAO_EDITAVEL)
        // R42: só reavalia a modalidade quando o cartão foi trocado (dados antigos não são migrados).
        val trocouCartao = despesa.cartaoId?.takeIf { it != 0 } != null && despesa.cartaoId != antigo.cartaoId
        var base = if (trocouCartao) comModalidade(despesa, modalidade) else despesa
        // Trocou para outro cartão (crédito): a compra volta a ficar em aberto, quitada só pela fatura.
        if (trocouCartao && base.cartaoId != null) base = base.copy(pago = false)
        // Mover o lançamento de débito para outra conta desfaz o vínculo com o cartão (R42b).
        if (Financas.cartaoDeDebito(antigo) != null && base.cartaoId == null && base.conta != antigo.conta &&
            base.grupoId == antigo.grupoId) base = base.copy(grupoId = null)
        if (antigo.natureza == Natureza.SALDO_INICIAL) base = base.copy(pago = true)
        // R60: anexos só mudam por `atualizarAnexos` (um envio em segundo plano pode ter trocado o caminho enquanto o formulário estava aberto).
        val novo = validar(base).copy(anexos = antigo.anexos)
        despesaDao.inserirDespesa(novo)
        recalcularAfetados(listOf(antigo, novo))
        atualizarSnapshotPatrimonial()
    }

    /**
     * R11 — exclui o lançamento (e o par, se for transferência) e recalcula os derivados. Com [grupoParcelas] e
     * lançamento de parcelamento (`parc:`), exclui todas as parcelas do grupo. Retorna quantos foram removidos.
     */
    suspend fun excluirLancamento(id: Long, grupoParcelas: Boolean = false): Int {
        val antes = runCatching { despesaDao.obterDespesaPorId(id) }.getOrNull()
        val n = excluirLancamentoBase(id, grupoParcelas)
        if (antes != null && n > 0) registrarTrilha { com.meudinheiro.domain.Trilha.excluir(agora(), antes, if (n > 1) "$n itens" else null) }
        return n
    }

    private suspend fun excluirLancamentoBase(id: Long, grupoParcelas: Boolean): Int = db.withTransaction {
        val d = despesaDao.obterDespesaPorId(id) ?: return@withTransaction 0
        LancamentoAcoes.motivoExclusaoBloqueada(d.natureza)?.let { throw RegraFinanceiraException(it) }
        val afetados = if (d.natureza == Natureza.TRANSFERENCIA && d.grupoId != null) {
            despesaDao.obterDoGrupo(d.grupoId).also { despesaDao.excluirPorGrupo(d.grupoId) }
        } else if (grupoParcelas && LancamentoAcoes.ehGrupoParcelas(d.natureza, d.grupoId)) {
            despesaDao.obterDoGrupo(d.grupoId!!).filter { it.natureza == Natureza.NORMAL }
                .also { lista -> lista.forEach { despesaDao.excluirPorId(it.id) } }
        } else {
            despesaDao.excluirPorId(id)
            listOf(d)
        }
        // R31: guarda 30 dias para desfazer (o saldo inicial não vai para a lixeira).
        afetados.filter { it.natureza != Natureza.SALDO_INICIAL }.forEach { lixeiraDao.inserir(paraLixeira(it)) }
        recalcularAfetados(afetados)
        atualizarSnapshotPatrimonial()
        afetados.size
    }

    /** R12 — alterna pago/pendente. Em compra de cartão só o limite muda; em conta, o saldo. */
    suspend fun alternarPago(id: Long, pago: Boolean) {
        val antes = runCatching { despesaDao.obterDespesaPorId(id) }.getOrNull()
        alternarPagoBase(id, pago)
        if (antes != null && antes.pago != pago) registrarTrilha { com.meudinheiro.domain.Trilha.pago(agora(), antes, pago) }
    }

    private suspend fun alternarPagoBase(id: Long, pago: Boolean) = db.withTransaction {
        val d = despesaDao.obterDespesaPorId(id) ?: return@withTransaction
        if (d.natureza != Natureza.NORMAL && d.natureza != Natureza.AJUSTE) throw RegraFinanceiraException(LancamentoAcoes.MSG_NAO_ALTERNAVEL)
        if (d.pago == pago) return@withTransaction
        despesaDao.atualizarPago(id, pago)
        recalcularAfetados(listOf(d))
        atualizarSnapshotPatrimonial()
    }

    /** R40 — lançamentos em aberto que podem ser antecipados junto com [id] (mesmo parcelamento/repetição, mesma conta). */
    suspend fun antecipaveisDoGrupo(id: Long): List<Despesa> {
        val d = despesaDao.obterDespesaPorId(id) ?: return emptyList()
        if (!Financas.antecipavel(d)) return emptyList()
        val g = d.grupoId
        val mesmoGrupo = g != null && (LancamentoAcoes.ehGrupoParcelas(d.natureza, g) || g.startsWith("rep:"))
        val lista = if (mesmoGrupo) despesaDao.obterDoGrupo(g!!) else listOf(d)
        return lista.filter { Financas.antecipavel(it) && it.conta == d.conta }.sortedWith(compareBy({ it.dataMs }, { it.id }))
    }

    /**
     * R40 — paga adiantado [ids] (ordem de consumo), com [desconto] opcional; [data] padrão = agora. Atômico.
     * Devolve a economia (desconto) e o restante em aberto.
     */
    suspend fun anteciparPagamento(ids: List<Long>, valorPago: Double, desconto: Double = 0.0, data: Long? = null): Financas.ResultadoAntecipacao =
        db.withTransaction {
            val itens = ids.distinct().map { despesaDao.obterDespesaPorId(it) ?: throw RegraFinanceiraException("Lançamento não encontrado.") }
            if (itens.isNotEmpty() && contaSaldoDao.obterPorNumero(itens[0].conta) == null) throw RegraFinanceiraException("Conta não encontrada.")
            val r = try {
                Financas.antecipar(itens, valorPago, desconto, data ?: agora())
            } catch (e: IllegalArgumentException) {
                throw RegraFinanceiraException(e.message ?: "Antecipação inválida.")
            }
            r.substituidos.forEach { despesaDao.inserirDespesa(it) }
            r.novos.forEach { despesaDao.inserirDespesa(normalizar(it)) }
            recalcularAfetados(itens)
            atualizarSnapshotPatrimonial()
            r
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
    suspend fun restaurarDaLixeira(id: Int) {
        val item = runCatching { lixeiraDao.obter(id) }.getOrNull()
        restaurarDaLixeiraBase(id)
        if (item != null) registrarTrilha {
            com.meudinheiro.domain.EntradaTrilha(agora(), com.meudinheiro.domain.AcaoTrilha.RESTAURAR_LIXEIRA, null, item.descricao, item.valor)
        }
    }

    private suspend fun restaurarDaLixeiraBase(id: Int): Unit = db.withTransaction {
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

    /**
     * R60 — o que sai definitivamente da lixeira (excluir um item, esvaziar, purgar 30 dias) leva os arquivos dos anexos
     * (Storage/local), em melhor esforço. **Mandar para a lixeira NÃO apaga arquivo**: restaurar mantém os anexos.
     */
    private suspend fun apagarArquivosDaLixeira(itens: List<Lixeira>) {
        if (itens.isEmpty()) return
        val anexos = itens.flatMap { runCatching { deLixeira(it).anexos }.getOrDefault(emptyList()) }
        if (anexos.isNotEmpty()) com.meudinheiro.storage.AnexosGerenciador.get(context).excluirTodos(anexos)
    }

    suspend fun excluirDaLixeira(id: Int) {
        apagarArquivosDaLixeira(listOfNotNull(lixeiraDao.obter(id)))
        lixeiraDao.excluirPorIds(listOf(id))
    }

    suspend fun esvaziarLixeira() {
        apagarArquivosDaLixeira(lixeiraDao.obterTodasStatic())
        lixeiraDao.limpar()
    }

    /** Remove definitivamente o que está na lixeira há mais de 30 dias. */
    suspend fun purgarLixeira() {
        val corte = agora() - 30L * 86_400_000L
        apagarArquivosDaLixeira(lixeiraDao.obterTodasStatic().filter { it.excluidoEm < corte })
        lixeiraDao.purgarAnteriores(corte)
    }

    /**
     * R60 — troca só a lista de anexos do lançamento (sem mexer em saldo/limite). [transformar] recebe a lista atual do banco.
     * `false` se o lançamento não existe mais (excluído/na lixeira).
     */
    suspend fun atualizarAnexos(id: Long, transformar: (List<com.meudinheiro.data.Anexo>) -> List<com.meudinheiro.data.Anexo>): Boolean =
        db.withTransaction {
            val atual = despesaDao.obterDespesaPorId(id) ?: return@withTransaction false
            val novos = com.meudinheiro.domain.Anexos.normalizar(transformar(atual.anexos))
            if (novos != atual.anexos) despesaDao.inserirDespesa(atual.copy(anexos = novos))
            true
        }

    // Nomes legados (mantidos para não quebrar chamadas existentes).
    suspend fun inserirDespesa(despesa: Despesa) { registrarLancamento(despesa) }
    suspend fun excluirDespesa(id: Long) = excluirLancamento(id)
    suspend fun excluirDespesaComRestituicao(id: Long) = excluirLancamento(id)
    suspend fun marcarDespesaComoPaga(id: Long, pago: Boolean) = alternarPago(id, pago)
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

    /**
     * R42 - iguala o saldo do sistema ao do banco com 1 lançamento AJUSTE (conta como saldo, mas não como
     * receita/despesa). Retorna o ajuste feito, ou `null` se já estava igual. Pode ser excluído para desfazer.
     */
    suspend fun ajustarSaldoConta(numeroConta: String, saldoReal: Double, observacao: String? = null): Financas.Ajuste? =
        db.withTransaction {
            if (!saldoReal.isFinite()) throw RegraFinanceiraException("Informe o saldo real da conta.")
            val conta = contaSaldoDao.obterPorNumero(numeroConta) ?: throw RegraFinanceiraException("Conta não encontrada.")
            val atual = Financas.saldoConta(conta.conta, despesaDao.obterDaConta(conta.conta))
            val ajuste = Financas.calcularAjusteSaldo(atual, saldoReal) ?: return@withTransaction null
            val ms = agora()
            val obs = observacao?.trim().orEmpty()
            despesaDao.inserirDespesa(
                normalizar(
                    Despesa(
                        descricao = "Ajuste de saldo (conferido com o banco)" + if (obs.isNotEmpty()) " - $obs" else "",
                        valor = ajuste.valor, data = Date(ms),
                        categoria = "Ajuste de saldo", conta = conta.conta, pic = "bank", tipo = ajuste.tipo,
                        mes = 0, ano = 0, pago = true, natureza = Natureza.AJUSTE
                    )
                )
            )
            recalcularConta(conta.conta)
            atualizarSnapshotPatrimonial()
            ajuste
        }

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
    fun getDespesasDoGrupoDe(cartaoId: Int): Flow<List<Despesa>> = getComprasDoGrupoDe(cartaoId).map { it.credito }

    /**
     * Compras do grupo (físico + virtuais) de [cartaoId]: `credito` = com `cartaoId` do grupo (fatura/limite);
     * `debito` = lançamentos diretos da conta vinculados por `grupoId = "debito:<id do grupo>"` (R42), só informativos.
     * Traz o id do principal para a tela descartar emissões de outro grupo ao trocar de cartão.
     */
    fun getComprasDoGrupoDe(cartaoId: Int): Flow<Financas.ComprasDoGrupo> =
        combine(cartaoDao.obterTodosFlow(), despesaDao.obterTodasFlow()) { cartoes, despesas ->
            Financas.comprasDoGrupo(cartaoId, cartoes, despesas)
        }

    /**
     * Salva um cartão. Cartão VIRTUAL (R18) herda conta, limite, fechamento/vencimento e tipo do físico a que
     * pertence; editar o físico propaga essas informações aos virtuais.
     */
    suspend fun salvarCartao(cartao: Cartao): Unit = db.withTransaction {
        if (cartao.nome.isBlank()) throw RegraFinanceiraException("Informe o nome do cartão.")
        var c = cartao
        var principalTotal = c.limiteTotal

        val principalId = c.cartaoPrincipalId
        val anterior = if (c.id != 0) cartaoDao.getCartaoPorId(c.id) else null
        if (principalId != null) {
            if (principalId == c.id) throw RegraFinanceiraException("Um cartão não pode ser virtual de si mesmo.")
            val principal = cartaoDao.getCartaoPorId(principalId)
                ?: throw RegraFinanceiraException("Selecione o cartão físico ao qual o cartão virtual pertence.")
            if (principal.cartaoPrincipalId != null) {
                throw RegraFinanceiraException("Um cartão virtual precisa estar ligado a um cartão físico (não a outro virtual).")
            }
            // R42: um cartão que tem virtuais próprios não pode virar virtual.
            if (c.id != 0 && cartaoDao.obterGrupo(c.id).any { it.id != c.id }) {
                throw RegraFinanceiraException("Este cartão físico tem cartões virtuais. Mova ou exclua os virtuais antes de ligá-lo a outro cartão.")
            }
            c = c.copy(
                contaId = principal.contaId, limiteTotal = principal.limiteTotal,
                diaFechamento = principal.diaFechamento, diaVencimento = principal.diaVencimento, tipo = principal.tipo
            )
            principalTotal = principal.limiteTotal
        } else if (anterior?.cartaoPrincipalId != null) {
            // R42: virtual → físico independente; mantém os valores herdados, sem limite próprio.
            c = c.copy(limiteProprio = null)
            if (c.limiteTotal <= 0.0 && c.contaId == 0) {
                c = c.copy(
                    contaId = anterior.contaId, limiteTotal = anterior.limiteTotal, diaFechamento = anterior.diaFechamento,
                    diaVencimento = anterior.diaVencimento, tipo = anterior.tipo
                )
            }
            principalTotal = c.limiteTotal
        }

        if (c.limiteTotal < 0) throw RegraFinanceiraException("O limite não pode ser negativo.")
        if (c.diaFechamento !in 1..31 || c.diaVencimento !in 1..31) {
            throw RegraFinanceiraException("Dias de fechamento e vencimento devem estar entre 1 e 31.")
        }
        contaSaldoDao.obterPorId(c.contaId) ?: throw RegraFinanceiraException("Selecione a conta vinculada ao cartão.")

        // R41: limite próprio opcional (0/nulo = sem teto); >0 e <= limite total do físico.
        val proprio = c.limiteProprio?.takeIf { it != 0.0 }?.let { if (it.isFinite()) Dinheiro.arredondar(it) else Double.NaN }
        if (proprio != null && (proprio.isNaN() || proprio <= 0.0 || proprio > Dinheiro.arredondar(principalTotal))) {
            throw RegraFinanceiraException(MSG_LIMITE_PROPRIO)
        }
        if (c.cartaoPrincipalId == null && c.id != 0) {
            cartaoDao.obterGrupo(c.id).filter { it.id != c.id }.forEach { v ->
                val p = v.limiteProprio
                if (p != null && p > Dinheiro.arredondar(c.limiteTotal)) {
                    throw RegraFinanceiraException("O limite total não pode ficar abaixo do limite próprio do cartão virtual \"${v.nome}\".")
                }
            }
        }
        c = c.copy(limiteProprio = proprio)

        cartaoDao.inserirCartao(
            c.copy(limiteTotal = Dinheiro.arredondar(c.limiteTotal), limiteDisponivel = Dinheiro.arredondar(c.limiteTotal))
        )
        if (c.cartaoPrincipalId == null && c.id != 0) {
            cartaoDao.propagarParaVirtuais(c.id, c.contaId, Dinheiro.arredondar(c.limiteTotal), c.diaFechamento, c.diaVencimento, c.tipo)
        }
        // R42: as compras do cartão seguem o número de conta do cartão (e dos virtuais, se for físico).
        if (c.id != 0) {
            cartaoDao.obterGrupo(c.id).forEach { g ->
                contaSaldoDao.obterPorId(g.contaId)?.let { despesaDao.atualizarContaDoCartao(g.id, it.conta) }
            }
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
            despesaDao.reatribuirGrupo(Financas.grupoIdDebito(atual.id), Financas.grupoIdDebito(principalId))
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

    /**
     * Pagamento seletivo: quita só os [itemIds] (em aberto, do grupo do cartão) com UM lançamento
     * PAGAMENTO_FATURA do valor líquido (débitos − estornos, em centavos). Retorna o valor pago.
     */
    suspend fun pagarItens(cartaoId: Int, itemIds: List<Long>): Double = db.withTransaction {
        val escolhido = cartaoDao.getCartaoPorId(cartaoId) ?: throw RegraFinanceiraException("Cartão não encontrado.")
        val cartao = cartaoDao.getCartaoPorId(escolhido.idDoGrupo) ?: throw RegraFinanceiraException("Cartão físico não encontrado.")
        val ids = cartaoDao.obterGrupo(cartao.id).map { it.id }.toSet()
        val conta = contaSaldoDao.obterPorId(cartao.contaId) ?: throw RegraFinanceiraException("Conta do cartão não encontrada.")
        val unicos = itemIds.distinct()
        if (unicos.isEmpty()) throw RegraFinanceiraException("Selecione ao menos um item.")
        val doGrupo = despesaDao.obterDosCartoes(ids.toList())
        val porId = doGrupo.associateBy { it.id }
        val itens = unicos.map { porId[it] ?: throw RegraFinanceiraException("Item não pertence a este cartão.") }
        if (itens.any { it.pago }) throw RegraFinanceiraException("Há itens já pagos na seleção.")
        val liquidoC = itens.sumOf { -it.centavosAssinados }
        if (liquidoC <= 0L) throw RegraFinanceiraException("Não há valor a pagar nos itens selecionados.")

        val refs = itens.map { Financas.faturaDaCompra(it.dataMs, cartao.diaFechamento) }.distinct()
        val rotulo: String
        val grupoId: String
        if (refs.size == 1) {
            val ref = refs.first()
            val abertosDaFatura = doGrupo.count { !it.pago && Financas.faturaDaCompra(it.dataMs, cartao.diaFechamento) == ref }
            val base = "fatura:${cartao.id}:${ref.ano}-%02d".format(ref.mes)
            if (abertosDaFatura == itens.size) {
                rotulo = "%02d/%d".format(ref.mes, ref.ano); grupoId = base
            } else {
                rotulo = "%02d/%d (parcial)".format(ref.mes, ref.ano)
                grupoId = "$base:parcial:${java.util.UUID.randomUUID()}"
            }
        } else {
            rotulo = "(itens selecionados)"
            grupoId = "fatura:${cartao.id}:multi:${java.util.UUID.randomUUID()}"
        }

        val valor = Dinheiro.reais(liquidoC)
        despesaDao.marcarComoPagas(itens.map { it.id })
        despesaDao.inserirDespesa(
            normalizar(
                Despesa(
                    descricao = "Pagamento fatura ${cartao.nome} $rotulo",
                    valor = valor, data = Date(agora()), categoria = "Cartão", conta = conta.conta,
                    pic = "payments", tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true,
                    natureza = Natureza.PAGAMENTO_FATURA, grupoId = grupoId
                )
            )
        )
        recalcularConta(conta.conta)
        recalcularCartao(cartao.id)
        atualizarSnapshotPatrimonial()
        valor
    }

    /**
     * Fatura já paga por fora: só quita (marca como pagos) os [itemIds] em aberto do grupo do cartão, liberando
     * o limite. NÃO cria PAGAMENTO_FATURA e NÃO altera o saldo de nenhuma conta. Aceita líquido <= 0.
     * Retorna a quantidade de itens marcados.
     */
    suspend fun marcarItensComoPagos(cartaoId: Int, itemIds: List<Long>): Int = db.withTransaction {
        val escolhido = cartaoDao.getCartaoPorId(cartaoId) ?: throw RegraFinanceiraException("Cartão não encontrado.")
        val cartao = cartaoDao.getCartaoPorId(escolhido.idDoGrupo) ?: throw RegraFinanceiraException("Cartão físico não encontrado.")
        val ids = cartaoDao.obterGrupo(cartao.id).map { it.id }.toSet()
        val unicos = itemIds.distinct()
        if (unicos.isEmpty()) throw RegraFinanceiraException("Selecione ao menos um item.")
        val porId = despesaDao.obterDosCartoes(ids.toList()).associateBy { it.id }
        val itens = unicos.map { porId[it] ?: throw RegraFinanceiraException("Item não pertence a este cartão.") }
        if (itens.any { it.pago }) throw RegraFinanceiraException("Há itens já pagos na seleção.")

        despesaDao.marcarComoPagas(itens.map { it.id })
        recalcularCartao(cartao.id)
        atualizarSnapshotPatrimonial()
        itens.size
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
        despesaFixaDao.obterTodas().forEach { regraSalva ->
            // R47: pausada de fato não lança nada nem mexe em ultimaDataLancamento; prazo vencido retoma sem catch-up.
            if (Financas.recorrenciaPausada(regraSalva, hoje)) return@forEach
            val regra = if (regraSalva.pausada) Financas.retomar(regraSalva, hoje).also { despesaFixaDao.atualizar(it) } else regraSalva
            val datas = Financas.ocorrenciasPendentes(regra, hoje)
            if (datas.isEmpty()) return@forEach
            // Origem válida? (cartão → conta do cartão; conta → precisa existir). Senão a regra espera, sem perder meses.
            val origem = runCatching { resolverOrigem(regra) }.getOrNull() ?: return@forEach
            if (contaSaldoDao.obterPorNumero(origem.conta) == null) return@forEach
            // R42: cartão DÉBITO gera na conta; CRÉDITO/MÚLTIPLO seguem como compra no cartão.
            val cartaoDaRegra = origem.cartaoId?.let { cartaoDao.getCartaoPorId(it) }
            val noCredito = cartaoDaRegra == null || Financas.modalidadeDaCompra(cartaoDaRegra) == Financas.Modalidade.CREDITO
            val cartaoGerado = if (noCredito) origem.cartaoId else null
            datas.forEach { ms ->
                despesaDao.inserirDespesa(
                    normalizar(
                        Despesa(
                            descricao = regra.descricao, valor = regra.valor, data = Date(ms),
                            categoria = regra.categoria, conta = origem.conta, pic = regra.pic,
                            tipo = regra.tipo, mes = 0, ano = 0, pago = false, cartaoId = cartaoGerado
                        )
                    )
                )
            }
            cartaoGerado?.let { recalcularCartao(it) } // a compra no cartão consome limite (R4/R18)
            despesaFixaDao.atualizar(origem.copy(ultimaDataLancamento = Date(datas.last())))
        }
    }

    /** R47 — pausa a recorrência (até [ate], futuro, ou até retomar). Não altera nada já lançado. */
    suspend fun pausarRecorrencia(id: Int, ate: Long? = null): Unit = db.withTransaction {
        val regra = despesaFixaDao.obterPorId(id) ?: throw RegraFinanceiraException("Recorrência não encontrada.")
        if (ate != null && ate <= agora()) throw RegraFinanceiraException("A data de retomada deve ser futura.")
        despesaFixaDao.atualizar(regra.copy(pausada = true, pausadaAte = ate?.let { Date(it) }))
    }

    /** R47 — retoma manualmente; os meses pausados não são recuperados. */
    suspend fun retomarRecorrencia(id: Int): Unit = db.withTransaction {
        val regra = despesaFixaDao.obterPorId(id) ?: throw RegraFinanceiraException("Recorrência não encontrada.")
        if (!regra.pausada) throw RegraFinanceiraException("A recorrência não está pausada.")
        despesaFixaDao.atualizar(Financas.retomar(regra, agora()))
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

    /* ======================= R55 — REGRAS DE CATEGORIA ======================= */

    fun obterRegrasFlow(): Flow<List<RegraCategoria>> = regraDao.observar()
    suspend fun obterRegras(): List<RegraCategoria> = regraDao.obterTodasStatic()

    suspend fun salvarRegra(regra: RegraCategoria): Long {
        val limpa = regra.copy(
            padrao = regra.padrao.trim(),
            categoria = regra.categoria.trim(),
            conta = regra.conta?.trim()?.takeIf { it.isNotEmpty() },
            tipo = regra.tipo?.takeIf { it == "DEBITO" || it == "CREDITO" }
        )
        Regras.validar(limpa.padrao, limpa.categoria, limpa.tipoMatch)?.let { throw RegraFinanceiraException(it) }
        return regraDao.salvar(if (limpa.id == 0) limpa.copy(criadaEm = agora()) else limpa)
    }

    suspend fun alternarRegra(id: Int) {
        val r = regraDao.obterTodasStatic().firstOrNull { it.id == id } ?: throw RegraFinanceiraException("Regra não encontrada.")
        regraDao.salvar(r.copy(ativa = !r.ativa))
    }

    suspend fun ajustarPrioridadeRegra(id: Int, delta: Int) {
        val r = regraDao.obterTodasStatic().firstOrNull { it.id == id } ?: throw RegraFinanceiraException("Regra não encontrada.")
        regraDao.salvar(r.copy(prioridade = r.prioridade + delta))
    }

    suspend fun excluirRegra(id: Int) = regraDao.excluir(id)

    /** Categoria da regra vencedora para o lançamento (ou `null`). Regra explícita vence a sugestão heurística (R21). */
    suspend fun categoriaPorRegras(descricao: String, conta: String?, tipo: TipoDespesa): String? =
        Regras.categoria(regraDao.obterTodasStatic(), Regras.Entrada(descricao, conta, tipo.name))

    /** R55 — aprendizado leve: depois de trocar a categoria, há 2+ lançamentos iguais com ela? Sugere a regra (nunca cria sozinho). */
    suspend fun sugerirRegraAprendida(editado: Despesa): Regras.Sugestao? =
        Regras.sugerirAprendida(despesaDao.obterTodasStatic(), regraDao.obterTodasStatic(), editado)

    suspend fun criarRegraAprendida(s: Regras.Sugestao): Long = regraDao.salvar(Regras.novaRegraAprendida(s, agora()))

    /** Prévia (não altera nada): lançamentos NORMAL cuja categoria mudaria ao aplicar as regras. */
    suspend fun previaRegrasExistentes(): List<Regras.Mudanca> =
        Regras.previa(despesaDao.obterTodasStatic(), regraDao.obterTodasStatic())

    /** O necessário para desfazer a aplicação (categoria e ícone anteriores). */
    data class CategoriaAnterior(val id: Long, val categoria: String, val pic: String)

    /** Aplica a prévia confirmada (só lançamentos NORMAL; categoria não afeta saldo). Devolve o que é preciso para desfazer. */
    suspend fun aplicarRegrasExistentes(mudancas: List<Regras.Mudanca>): List<CategoriaAnterior> = db.withTransaction {
        val pics = categoriaDao.obterTodasStatic().associate { Texto.normalizar(it.nome) to it.pic }
        val anteriores = ArrayList<CategoriaAnterior>()
        for (m in mudancas) {
            val d = despesaDao.obterDespesaPorId(m.id) ?: continue
            if (d.natureza != Natureza.NORMAL) continue
            anteriores += CategoriaAnterior(d.id, d.categoria, d.pic)
            despesaDao.inserirDespesa(d.copy(categoria = m.para, pic = pics[Texto.normalizar(m.para)] ?: d.pic))
        }
        anteriores
    }

    suspend fun desfazerRegrasExistentes(anteriores: List<CategoriaAnterior>) = db.withTransaction {
        for (a in anteriores) {
            val d = despesaDao.obterDespesaPorId(a.id) ?: continue
            despesaDao.inserirDespesa(d.copy(categoria = a.categoria, pic = a.pic))
        }
    }

    /* ======================= R56 — TAGS E PROJETOS ======================= */

    fun obterProjetosFlow(): Flow<List<Projeto>> = projetoDao.observar()

    suspend fun salvarProjeto(projeto: Projeto): Long {
        val limpo = projeto.copy(
            nome = projeto.nome.trim(),
            tag = Tags.chave(projeto.tag),
            orcamento = projeto.orcamento?.takeIf { it > 0.0 }?.let { Dinheiro.arredondar(it) },
            cor = projeto.cor?.takeIf { it.isNotBlank() }
        )
        Projetos.validar(limpo.nome, limpo.tag, limpo.orcamento, limpo.inicio, limpo.fim, projetoDao.obterTodasStatic(), limpo.id.takeIf { it != 0 })
            ?.let { throw RegraFinanceiraException(it) }
        return projetoDao.salvar(limpo)
    }

    suspend fun excluirProjeto(id: Int) = projetoDao.excluir(id)

    /* ======================= R58 — DÍVIDAS ======================= */

    fun obterDividasFlow(): Flow<List<Divida>> = dividaDao.observar()

    /** Cria/edita a dívida. Na criação o valor original é o saldo; na edição preserva o original já gravado. */
    suspend fun salvarDivida(divida: Divida): Long {
        val saldo = Dinheiro.arredondar(divida.saldoDevedor)
        val anterior = if (divida.id != 0) dividaDao.obterPorId(divida.id) else null
        val limpa = divida.copy(
            nome = divida.nome.trim(),
            credor = divida.credor?.trim()?.takeIf { it.isNotEmpty() },
            saldoDevedor = saldo,
            valorOriginal = divida.valorOriginal?.takeIf { it > 0.0 }?.let { Dinheiro.arredondar(it) } ?: anterior?.valorOriginal ?: saldo,
            taxaJurosMensal = if (divida.taxaJurosMensal > 0.0) divida.taxaJurosMensal else 0.0,
            parcelaMinima = divida.parcelaMinima?.takeIf { it > 0.0 }?.let { Dinheiro.arredondar(it) },
            conta = divida.conta?.trim()?.takeIf { it.isNotEmpty() }
        )
        Dividas.validar(limpa.nome, limpa.tipo, limpa.saldoDevedor, limpa.taxaJurosMensal, limpa.parcelaMinima, limpa.parcelasRestantes, limpa.diaVencimento)
            ?.let { throw RegraFinanceiraException(it) }
        return dividaDao.salvar(limpa)
    }

    suspend fun excluirDivida(id: Int) = dividaDao.excluir(id)

    /** Juros do mês (informados pelo usuário) acrescentam ao saldo devedor; nada em conta é alterado. */
    suspend fun registrarJurosDivida(id: Int, juros: Double) {
        val d = dividaDao.obterPorId(id) ?: throw RegraFinanceiraException("Dívida não encontrada.")
        val j = Dinheiro.centavos(juros)
        if (j <= 0) throw RegraFinanceiraException("Os juros devem ser maiores que zero.")
        dividaDao.salvar(d.copy(saldoDevedor = Dinheiro.reais(Dinheiro.centavos(d.saldoDevedor) + j), ativa = true))
    }

    /**
     * Paga a dívida: lançamento NORMAL de despesa (pago) na conta, `grupoId = "divida:<id>"`, e abate o saldo devedor na MESMA
     * transação. O saldo da conta já cai pelo lançamento; o saldo devedor é controle próprio. [juros] (opcional) é somado ao
     * saldo devedor antes de abater. Saldo zero = quitada (`ativa = false`). Devolve o id do lançamento.
     */
    suspend fun pagarDivida(dividaId: Int, valor: Double, conta: String, data: Long? = null, categoria: String? = null, juros: Double = 0.0): Long =
        db.withTransaction {
            val d = dividaDao.obterPorId(dividaId) ?: throw RegraFinanceiraException("Dívida não encontrada.")
            if (contaSaldoDao.obterPorNumero(conta) == null) throw RegraFinanceiraException("Conta não encontrada.")
            val cat = categoria?.trim()?.takeIf { it.isNotEmpty() } ?: Dividas.CATEGORIA
            val pago = Dinheiro.centavos(valor)
            val r = Dividas.aplicarPagamento(Dinheiro.centavos(d.saldoDevedor), Dinheiro.centavos(juros), pago)
            r.erro?.let { throw RegraFinanceiraException(it) }
            val pic = categoriaDao.obterTodasStatic().firstOrNull { Texto.normalizar(it.nome) == Texto.normalizar(cat) }?.pic ?: "default_pic"
            val id = registrarLancamento(
                Despesa(
                    descricao = "Pagamento: ${d.nome}", valor = Dinheiro.reais(pago), data = Date(data ?: agora()), categoria = cat,
                    conta = conta, pic = pic, tipo = TipoDespesa.DEBITO, mes = 0, ano = 0, pago = true, grupoId = Dividas.grupoId(d.id)
                )
            )
            dividaDao.salvar(d.copy(saldoDevedor = Dinheiro.reais(r.saldo), ativa = r.saldo > 0))
            id
        }

    /* ======================= R59 — PESSOAS, RATEIOS E ACERTOS ======================= */

    fun obterPessoasFlow(): Flow<List<Pessoa>> = pessoaDao.observar()
    fun obterAcertosFlow(): Flow<List<Acerto>> = acertoDao.observar()
    fun obterAcertosDaDespesaFlow(despesaId: Long): Flow<List<Acerto>> = acertoDao.observarDaDespesa(despesaId)

    suspend fun salvarPessoa(pessoa: Pessoa): Long {
        val nome = pessoa.nome.trim()
        if (nome.isEmpty()) throw RegraFinanceiraException("Informe o nome da pessoa.")
        val alvo = Texto.normalizar(nome)
        if (pessoaDao.obterTodasStatic().any { it.id != pessoa.id && Texto.normalizar(it.nome) == alvo }) {
            throw RegraFinanceiraException("Já existe uma pessoa com esse nome.")
        }
        return pessoaDao.salvar(pessoa.copy(nome = nome, contato = pessoa.contato?.trim()?.takeIf { it.isNotEmpty() }))
    }

    suspend fun excluirPessoa(id: Int) {
        if (acertoDao.obterTodasStatic().any { it.pessoaId == id }) {
            throw RegraFinanceiraException("Esta pessoa tem acertos. Exclua os acertos antes de removê-la.")
        }
        pessoaDao.excluir(id)
    }

    /**
     * "Dividir esta despesa": cria os acertos A_RECEBER do rateio (cotas em centavos, sobra no pagador). O lançamento e os
     * saldos NÃO mudam. Devolve a parte que fica com você (centavos).
     */
    suspend fun dividirDespesa(despesaId: Long, modo: Rateios.Modo, participantes: List<Rateios.Participante>, vocePercentual: Long? = null): Long =
        db.withTransaction {
            val d = despesaDao.obterDespesaPorId(despesaId) ?: throw RegraFinanceiraException("Lançamento não encontrado.")
            if (d.tipo != TipoDespesa.DEBITO || d.natureza != Natureza.NORMAL) throw RegraFinanceiraException("Só despesas comuns podem ser divididas.")
            val grupo = Rateios.grupoRateio(d.id)
            if (acertoDao.obterTodasStatic().any { it.grupoRateio == grupo }) {
                throw RegraFinanceiraException("Este lançamento já foi dividido. Desfaça a divisão antes de dividir de novo.")
            }
            val pessoas = pessoaDao.obterTodasStatic().map { it.id }.toSet()
            if (participantes.any { it.pessoaId !in pessoas }) throw RegraFinanceiraException("Pessoa não encontrada.")
            when (val r = Rateios.ratear(Dinheiro.centavos(d.valor), modo, participantes, vocePercentual)) {
                is Rateios.Resultado.Erro -> throw RegraFinanceiraException(r.mensagem)
                is Rateios.Resultado.Ok -> {
                    // R68: vínculos já feitos contam como comprometidos; a divisão não pode passar do total.
                    val vinculado = Rateios.vinculadoAReceber(acertoDao.obterTodasStatic(), d.id)
                    if (vinculado > 0 && r.cotas.sumOf { it.centavos } + vinculado > Dinheiro.centavos(d.valor)) {
                        throw RegraFinanceiraException("Este lançamento já tem ${FormatoCentavos.brl(vinculado)} vinculados a pessoas; a divisão passaria do valor da despesa.")
                    }
                    acertoDao.inserirTodas(
                        r.cotas.map { c ->
                            Acerto(
                                pessoaId = c.pessoaId, tipo = Rateios.A_RECEBER, valor = Dinheiro.reais(c.centavos), descricao = d.descricao,
                                data = d.data.time, despesaId = d.id, quitado = false, quitadoEm = null, grupoRateio = grupo
                            )
                        }
                    )
                    r.voce
                }
            }
        }

    /**
     * R68 — vincula lançamentos JÁ EXISTENTES a uma pessoa: cria SOMENTE acertos (grupo `vinculo:<despesaId>`). Não cria, altera nem exclui
     * lançamento; saldo de conta, limite de cartão e KPIs não mudam. Atômico (uma transação): um item inválido não cria nenhum.
     * Devolve a quantidade de acertos criados.
     */
    suspend fun vincularLancamentos(pessoaId: Int, tipo: String, itens: List<Rateios.ItemVinculo>): Int = db.withTransaction {
        val cache = HashMap<Long, Despesa?>()
        for (i in itens) cache[i.despesaId] = despesaDao.obterDespesaPorId(i.despesaId)
        when (val r = Rateios.vincular({ cache[it] }, acertoDao.obterTodasStatic(), pessoaDao.obterTodasStatic(), pessoaId, tipo, itens)) {
            is Rateios.ResultadoVinculo.Erro -> throw RegraFinanceiraException(r.mensagem)
            is Rateios.ResultadoVinculo.Ok -> {
                acertoDao.inserirTodas(r.acertos)
                r.acertos.size
            }
        }
    }

    /** Remove os acertos do rateio (só se nenhum foi quitado). O lançamento não muda. */
    suspend fun desfazerRateio(despesaId: Long): Int = db.withTransaction {
        val grupo = Rateios.grupoRateio(despesaId)
        val doGrupo = acertoDao.obterTodasStatic().filter { it.grupoRateio == grupo }
        if (doGrupo.isEmpty()) throw RegraFinanceiraException("Este lançamento não foi dividido.")
        if (doGrupo.any { it.quitado }) throw RegraFinanceiraException("Há acertos já quitados neste rateio; não dá para desfazer a divisão.")
        acertoDao.excluirDoGrupo(grupo)
        doGrupo.size
    }

    /** Lançamento (já pago) para o saldo da conta bater com o banco. */
    private suspend fun lancarAcerto(descricao: String, valor: Double, data: Long, categoria: String, conta: String, tipo: TipoDespesa): Long {
        val pic = categoriaDao.obterTodasStatic().firstOrNull { Texto.normalizar(it.nome) == Texto.normalizar(categoria) }?.pic ?: "default_pic"
        return registrarLancamento(
            Despesa(descricao = descricao, valor = valor, data = Date(data), categoria = categoria, conta = conta, pic = pic, tipo = tipo, mes = 0, ano = 0, pago = true)
        )
    }

    /**
     * "Emprestei" ([tipo] = A_RECEBER) ou "Devo" (A_PAGAR): acerto avulso. Com [contaLancamento], gera também o lançamento
     * (saída no "Emprestei", entrada no "Devo") e o vincula como origem. Devolve o id do acerto.
     */
    suspend fun criarAcertoAvulso(
        pessoaId: Int, tipo: String, valor: Double, descricao: String?, data: Long? = null,
        contaLancamento: String? = null, categoria: String? = null
    ): Long = db.withTransaction {
        val pessoa = pessoaDao.obterTodasStatic().firstOrNull { it.id == pessoaId } ?: throw RegraFinanceiraException("Pessoa não encontrada.")
        if (tipo != Rateios.A_RECEBER && tipo != Rateios.A_PAGAR) throw RegraFinanceiraException("Tipo de acerto inválido.")
        val centavos = Dinheiro.centavos(valor)
        if (centavos <= 0) throw RegraFinanceiraException("O valor deve ser maior que zero.")
        val quando = data ?: agora()
        val recebe = tipo == Rateios.A_RECEBER
        val despesaId = contaLancamento?.takeIf { it.isNotBlank() }?.let { conta ->
            if (contaSaldoDao.obterPorNumero(conta) == null) throw RegraFinanceiraException("Conta não encontrada.")
            lancarAcerto(
                if (recebe) "Empréstimo para ${pessoa.nome}" else "Empréstimo de ${pessoa.nome}", Dinheiro.reais(centavos), quando,
                categoria?.trim()?.takeIf { it.isNotEmpty() } ?: Rateios.CATEGORIA_EMPRESTIMOS, conta,
                if (recebe) TipoDespesa.DEBITO else TipoDespesa.CREDITO
            )
        }
        acertoDao.salvar(
            Acerto(
                pessoaId = pessoa.id, tipo = tipo, valor = Dinheiro.reais(centavos),
                descricao = descricao?.trim()?.takeIf { it.isNotEmpty() } ?: if (recebe) "Empréstimo" else "Dívida",
                data = quando, despesaId = despesaId, quitado = false, quitadoEm = null, grupoRateio = null
            )
        )
    }

    /**
     * Receber/pagar o acerto: marca quitado e, com [contaLancamento], cria o lançamento já pago (receita "Reembolso" ao
     * receber; despesa "Acertos" ao pagar) para o saldo bater com o banco. Devolve o id do lançamento (ou `null`).
     */
    suspend fun quitarAcerto(acertoId: Int, data: Long? = null, contaLancamento: String? = null, categoria: String? = null): Long? =
        db.withTransaction {
            val a = acertoDao.obterTodasStatic().firstOrNull { it.id == acertoId } ?: throw RegraFinanceiraException("Acerto não encontrado.")
            if (a.quitado) throw RegraFinanceiraException("Este acerto já está quitado.")
            val quando = data ?: agora()
            val nome = pessoaDao.obterTodasStatic().firstOrNull { it.id == a.pessoaId }?.nome ?: "pessoa"
            val recebendo = a.tipo == Rateios.A_RECEBER
            val id = contaLancamento?.takeIf { it.isNotBlank() }?.let { conta ->
                if (contaSaldoDao.obterPorNumero(conta) == null) throw RegraFinanceiraException("Conta não encontrada.")
                lancarAcerto(
                    if (recebendo) "Reembolso: $nome - ${a.descricao}" else "Acerto com $nome: ${a.descricao}", a.valor, quando,
                    categoria?.trim()?.takeIf { it.isNotEmpty() } ?: if (recebendo) Rateios.CATEGORIA_REEMBOLSO else Rateios.CATEGORIA_ACERTOS,
                    conta, if (recebendo) TipoDespesa.CREDITO else TipoDespesa.DEBITO
                )
            }
            acertoDao.salvar(a.copy(quitado = true, quitadoEm = quando))
            id
        }

    suspend fun excluirAcerto(id: Int) = acertoDao.excluir(id)

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
            lixeira = lixeiraDao.obterTodasStatic(),
            regrasCategoria = regraDao.obterTodasStatic(),
            projetos = projetoDao.obterTodasStatic(),
            dividas = dividaDao.obterTodasStatic(),
            pessoas = pessoaDao.obterTodasStatic(),
            acertos = acertoDao.obterTodasStatic(),
            // R57: só leva as configurações se o usuário já alterou alguma (carimbo > 0)
            configuracoes = runCatching { configuracaoLocal.documentoParaEnvio() }.getOrNull()
        )
    )

    /**
     * Substitui TODOS os dados pelo backup, preservando ids (os vínculos cartão→conta e despesa→cartão
     * continuam válidos) e recalculando os derivados. Tudo ou nada: falhou, nada muda.
     */
    suspend fun restaurarBackupCompleto(json: String) {
        restaurarBackupCompletoBase(json)
        registrarTrilha { com.meudinheiro.domain.EntradaTrilha(agora(), com.meudinheiro.domain.AcaoTrilha.RESTAURAR_BACKUP, null, "Backup restaurado") }
    }

    private suspend fun restaurarBackupCompletoBase(json: String) {
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
            regraDao.inserirTodas(backup.regrasCategoria.orEmpty())
            projetoDao.inserirTodas(backup.projetos.orEmpty())
            dividaDao.inserirTodas(backup.dividas.orEmpty())
            pessoaDao.inserirTodas(backup.pessoas.orEmpty())
            acertoDao.inserirTodas(backup.acertos.orEmpty())
        }
        recalcularTudo()
        // R57: configurações do backup entram só se forem mais novas que as atuais (empate/sem data = local vence).
        backup.configuracoes?.let { doc -> runCatching { configuracaoLocal.aplicar(ConfigGeral.deDoc(doc)) } }
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
        regraDao.limpar()
        projetoDao.limpar()
        dividaDao.limpar()
        pessoaDao.limpar()
        acertoDao.limpar()
    }

    /* ======================= EXPORTAÇÃO / SINAIS ======================= */

    fun exportarExtratoPDF(context: Context, mes: String, ano: Int, despesas: List<DespesasDomain>) =
        ExtratoPdf.exportarECompartilhar(context, mes, ano, despesas)

    suspend fun avisarQueHouveMudanca() {
        _atualizacaoSinal.emit(Unit)
    }

    val atualizacaoSinal get() = _atualizacaoSinal.asSharedFlow()

    companion object {
        const val MSG_LIMITE_PROPRIO = "O limite próprio deve ser maior que zero e não pode passar do limite total do cartão físico."
        // Compartilhado entre todas as instâncias (telas, receivers e workers criam repositórios próprios).
        private val _atualizacaoSinal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }
}
