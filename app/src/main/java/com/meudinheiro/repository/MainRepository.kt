package com.meudinheiro.repository

import android.content.Context
import androidx.room.withTransaction
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.BackupData
import com.meudinheiro.data.BancoDomain
import com.meudinheiro.data.Categoria
import com.meudinheiro.data.CategoriaDomain
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.ContaSaldoDomain
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaAviso
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.ResumoFinanceiroDto
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import kotlin.Int

class MainRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    // DAOs
    private val despesaDao = db.despesaDao()
    private val contaSaldoDao = db.contaSaldoDao()
    private val despesaFixaDao = db.despesaFixaDao()
    private val categoriaDao = db.categoriaDao()
    private val orcamentoDao = db.orcamentoDao()
    private val metaDao = db.metaDao()


    /* ======================= DESPESAS ======================= */

    suspend fun inserirDespesa(despesa: Despesa) {
        despesaDao.inserirDespesa(despesa)
    }

    fun obterDespesas(): Flow<List<DespesasDomain>> {
        return despesaDao.obterDespesas()
    }

    suspend fun obterDespesaPorId(id: Int): Despesa? {
        return despesaDao.obterDespesaPorId(id)
    }

    suspend fun obterDespesasPorConta(contaId: String): List<DespesasDomain> {
        return despesaDao.obterDespesasPorConta(contaId)
    }

    suspend fun excluirDespesa(id: Int) {
        despesaDao.excluirDespesa(id)
    }

    fun obterDespesasPorContaFlow(contaId: String): Flow<List<DespesasDomain>> {
        return despesaDao.obterDespesasPorContaFlow(contaId)
    }

    suspend fun marcarDespesaComoPaga(id: Int, pago: Boolean) {
        despesaDao.setPago(id, pago)
    }

    suspend fun atualizarStatusPago(id: Long, status: Boolean) {
        despesaDao.atualizarStatusPago(id, status)
    }

    suspend fun recalcularSaldoTotal(contaNome: String) {
        // Busca todas as movimentações dessa conta
        val movimentacoes = despesaDao.obterDespesasPorConta(contaNome)

        var novoSaldo = 0.0

        movimentacoes.forEach { item ->
            // Assume que DespesasDomain ou Despesa tem 'pago', 'tipo' e 'valor'
            if (item.pago) {
                when (item.tipo) {
                    TipoDespesa.CREDITO -> novoSaldo += item.valor
                    TipoDespesa.DEBITO -> novoSaldo -= item.valor
                }
            }
        }

        // Atualiza a tabela de contas com o valor calculado
        contaSaldoDao.atualizarSaldo(contaNome, novoSaldo)
    }

    suspend fun excluirDespesaComRestituicao(id: Int) {
        db.withTransaction {
            // 1. Buscar a despesa
            val despesa = despesaDao.obterDespesaPorId(id) ?: return@withTransaction

            // 2. Obter saldo atual (tratando possível null do DAO)
            val saldoAtual = contaSaldoDao.obterSaldoPorConta(despesa.conta) ?: 0.0

            // 3. Calcular novo saldo
            val novoSaldo = when (despesa.tipo) {
                TipoDespesa.DEBITO -> saldoAtual + despesa.valor   // devolve o débito
                TipoDespesa.CREDITO -> saldoAtual - despesa.valor  // remove o crédito
                else -> saldoAtual                                  // fallback se tiver outro tipo
            }

            // 4. Atualizar saldo da conta
            contaSaldoDao.atualizarSaldo(despesa.conta, novoSaldo)

            // 5. Excluir a despesa
            despesaDao.excluirDespesa(id)
        }
    }

    /* ======================= CATEGORIAS / BANCOS (IN-MEMORY) ======================= */

    // Listas imutáveis — você não precisa alterá-las em tempo de execução
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

    fun getPicCategoria(titulo: String): String {
        val categoria = categorias.find { it.title == titulo }
        return categoria?.pic ?: "default_pic"
    }

    fun getPicBanco(titulo: String): String {
        val banco = bancos.find { it.nome == titulo }
        return banco?.pic ?: "default_pic"
    }

    /* ======================= SALDO DE CONTAS ======================= */

    fun obterContaSaldo(): Flow<List<ContaSaldoDomain>> {
        return contaSaldoDao.obterContaSaldo()
    }

    suspend fun inserirContaSaldo(contaSaldo: ContaSaldo) {
        contaSaldoDao.inserirContaSaldo(contaSaldo)
    }

    suspend fun obterSaldoPorConta(conta: String): Double {
        return contaSaldoDao.obterSaldoPorConta(conta) ?: 0.0
    }

    suspend fun excluirConta(id: Int) {
        contaSaldoDao.excluirConta(id)
    }

    suspend fun atualizarSaldo(conta: String, novoSaldo: Double) {
        contaSaldoDao.atualizarSaldo(conta, novoSaldo)
    }

    suspend fun getDespesasAVencer(
        startMillis: Long,
        endMillis: Long,
        onlyCredit: Boolean
    ): List<DespesaAviso> {
        // AJUSTE AQUI: consulte seu DAO e retorne uma lista com:
        // titulo/descricao, valor, vencimentoMillis e tipo(para filtrar crédito)
        TODO("implementar no seu DAO")
    }

    suspend fun getPendentesAVencer(inicio: Date, fim: Date, onlyCredit: Boolean): List<Despesa> {
        val dao = db.despesaDao() // AJUSTE: como você acessa o DAO dentro do repository
        return if (onlyCredit) {
            dao.obterPendentesVencendoDatePorTipo(inicio, fim, TipoDespesa.DEBITO)
        } else {
            dao.obterPendentesVencendoDate(inicio, fim)
        }
    }

    suspend fun listarPendencias(
        daysAhead: Int,
        onlyCredit: Boolean
    ): List<Despesa> {
        val (inicio, fim) = buildWindowDates(daysAhead)
        val dao = db.despesaDao()

        val tipoAlvo = if (onlyCredit) TipoDespesa.CREDITO else TipoDespesa.DEBITO

        val aVencer = dao.obterPendentesVencendoDatePorTipo(inicio, fim, TipoDespesa.DEBITO)

        val atrasadas = dao.obterPendentesAtrasadasPorTipo(inicio, tipoAlvo)

        return (atrasadas + aVencer).distinctBy { it.id }.sortedBy { it.data.time }
    }

    suspend fun contarPendencias(daysAhead: Int, onlyCredit: Boolean): Int {
        return listarPendencias(daysAhead, onlyCredit = false).size
    }

    private fun buildWindowDates(daysAhead: Int): Pair<Date, Date> {
        val calIni = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calFim = Calendar.getInstance().apply {
            timeInMillis = calIni.timeInMillis
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Date(calIni.timeInMillis) to Date(calFim.timeInMillis)
    }

    suspend fun obterResumoFinanceiro(inicio: Date, fim: Date): List<ResumoFinanceiroDto> {
        return despesaDao.obterResumoPorPeriodo(inicio, fim)
    }


    suspend fun obterResumoGlobalPorConta(): List<ResumoFinanceiroDto> {
        return despesaDao.obterResumoGlobalPorConta()
    }

    // Função auxiliar para pegar o primeiro e último dia do Mês Atual (para o painel ser útil)
    fun getDatesCurrentMonth(): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.time

        cal.add(Calendar.MONTH, 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.DATE, -1)
        val end = cal.time

        return Pair(start, end)
    }

// Adicione esses métodos no seu MainRepository

    // 1. Salvar a regra da recorrência
    suspend fun salvarDespesaFixa(despesaFixa: DespesaFixa) {
        despesaFixaDao.inserir(despesaFixa)
        // Tenta processar imediatamente caso o dia já tenha chegado
        processarRecorrencias()
    }

    // 2. O Cérebro da Operação: Verifica e Lança
    suspend fun processarRecorrencias() {
        val recorrencias = despesaFixaDao.obterTodas()
        val hoje = Calendar.getInstance()

        // Zera hora/minuto para comparação limpa de datas
        hoje.set(Calendar.HOUR_OF_DAY, 0)
        hoje.set(Calendar.MINUTE, 0)
        hoje.set(Calendar.SECOND, 0)
        hoje.set(Calendar.MILLISECOND, 0)

        recorrencias.forEach { regra ->
            val calUltimoLancamento = Calendar.getInstance()

            // Se nunca foi lançada, assumimos uma data bem antiga
            val ultimaData = regra.ultimaDataLancamento ?: Date(0)
            calUltimoLancamento.time = ultimaData

            // Verifica se o mês atual da regra já foi processado
            // Se o mês/ano da última vez for diferente do mês/ano atual (ou se nunca foi lançada)
            val jaLancouNesteMes =
                (calUltimoLancamento.get(Calendar.MONTH) == hoje.get(Calendar.MONTH)) &&
                        (calUltimoLancamento.get(Calendar.YEAR) == hoje.get(Calendar.YEAR))

            // Se ainda não lançou neste mês E hoje já é (ou passou) do dia de vencimento
            if (!jaLancouNesteMes && hoje.get(Calendar.DAY_OF_MONTH) >= regra.diaVencimento) {

                // Cria a data de vencimento para ESTE mês
                val dataDesteMes = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, regra.diaVencimento)
                    set(Calendar.HOUR_OF_DAY, 12) // Meio dia para evitar fuso
                }

                // Cria a despesa real na tabela principal
                val novaDespesa = Despesa(
                    descricao = regra.descricao,
                    valor = regra.valor,
                    conta = regra.conta,
                    categoria = regra.categoria,
                    pic = regra.pic,
                    tipo = regra.tipo,
                    data = dataDesteMes.time,
                    pago = false // Nasce como não paga (pendente)
                )

                // Insere na tabela de extrato
                despesaDao.inserirDespesa(novaDespesa)

                // Recalcula saldo da conta (importante!)
                recalcularSaldoTotal(regra.conta)

                // Atualiza a regra dizendo: "Já lancei a de Mês X / Ano Y"
                val regraAtualizada = regra.copy(ultimaDataLancamento = dataDesteMes.time)
                despesaFixaDao.atualizar(regraAtualizada)
            }
        }
    }

    fun exportarExtratoPDF(
        context: Context,
        mes: String,
        ano: Int,
        despesas: List<DespesasDomain>
    ) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val paint = android.graphics.Paint()
        val titlePaint = android.graphics.Paint().apply {
            isFakeBoldText = true
            textSize = 18f
            color = android.graphics.Color.BLACK
        }

        // Configuração da Página (A4: 595 x 842 pts)
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Cabeçalho
        canvas.drawText("Extrato Mensal - $mes / $ano", 40f, 50f, titlePaint)
        canvas.drawText("Gerado pelo Meu Dinheiro App", 40f, 75f, paint.apply { textSize = 12f })

        var yPosition = 120f
        paint.textSize = 10f

        // Cabeçalho da Tabela
        canvas.drawText("Data", 40f, yPosition, titlePaint.apply { textSize = 10f })
        canvas.drawText("Descrição", 120f, yPosition, titlePaint)
        canvas.drawText("Valor", 450f, yPosition, titlePaint)

        canvas.drawLine(40f, yPosition + 5f, 550f, yPosition + 5f, paint)
        yPosition += 25f

        // Itens
        despesas.forEach { item ->
            val dataStr =
                java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(item.data)
            canvas.drawText(dataStr, 40f, yPosition, paint)
            canvas.drawText(item.descricao, 120f, yPosition, paint)

            val valorStr =
                if (item.tipo == TipoDespesa.CREDITO) "+ ${item.valor}" else "- ${item.valor}"
            canvas.drawText(valorStr, 450f, yPosition, paint)

            yPosition += 20f

            // Se a página encher, você precisaria criar uma nova (lógica simplificada aqui)
        }

        pdfDocument.finishPage(page)

        // Salvar e Compartilhar
        val fileName = "Extrato_${mes}_${ano}.pdf"
        val file = java.io.File(context.cacheDir, fileName)

        try {
            pdfDocument.writeTo(java.io.FileOutputStream(file))
            pdfDocument.close()

            // Intent para compartilhar
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(
                    intent,
                    "Compartilhar Extrato"
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    // Busca todas as assinaturas ativas para a tela de gerenciamento

    suspend fun obterTodasRecorrencias(): List<DespesaFixa> {
        return despesaFixaDao.obterTodas()
    }

    // Remove a regra (cancela a assinatura futura)
    suspend fun excluirRecorrencia(id: Int) {
        despesaFixaDao.excluir(id)
    }

    fun obterCategoriasCustom(): Flow<List<CategoriaDomain>> {
        return categoriaDao.obterTodas().map { entities ->
            entities.map { entity ->
                CategoriaDomain(
                    pic = entity.pic,   // String do ícone
                    title = entity.title // Nome da categoria
                )
            }
        }
    }

    suspend fun salvarCategoria(categoria: Categoria) {
        categoriaDao.inserir(categoria)
    }

    suspend fun excluirCategoriaPorNome(nome: String) {
        categoriaDao.excluirPorNome(nome)
    }

    // 1. Coleta tudo do banco para o Backup
    suspend fun gerarBackup(): String {
        val backup = BackupData(
            categorias = categoriaDao.obterTodasStatic(),
            despesas = despesaDao.obterTodasStatic(),
            despesasFixas = despesaFixaDao.obterTodasStatic(),
            contas = contaSaldoDao.obterTodasStatic()
        )
        return com.google.gson.Gson().toJson(backup)
    }

    // 2. Limpa o banco atual e restaura o backup
    suspend fun restaurarBackupCompleto(json: String) {
        val backup = com.google.gson.Gson().fromJson(json, BackupData::class.java)

        db.withTransaction { // Executa tudo ou nada
            // 1. Limpa o que for necessário (Opcional)
            despesaDao.limparTudo()

            // 2. Restaura em massa
            categoriaDao.inserirLista(backup.categorias)
            contaSaldoDao.inserirLista(backup.contas)
            despesaDao.inserirLista(backup.despesas)
            despesaFixaDao.inserirLista(backup.despesasFixas)
        }
    }

    // --- LÓGICA DE ORÇAMENTOS (BUDGETS) ---

    fun obterOrcamentosFlow(): Flow<List<Orcamento>> {
        return orcamentoDao.obterTodosFlow()
    }

    suspend fun salvarOrcamento(categoria: String, valor: Double) {
        val orcamento = Orcamento(
            categoria = categoria,
            valorLimite = valor
        )
        orcamentoDao.salvarOrcamento(orcamento)
    }

    suspend fun excluirOrcamento(categoria: String) {
        orcamentoDao.excluirPorCategoria(categoria)
    }

    suspend fun atualizarOrcamento(categoria: String, novoValor: Double) {
        orcamentoDao.atualizarPorCategoria(categoria, novoValor)
    }

    val todasDespesasFlow: Flow<List<Despesa>> = despesaDao.obterTodasFlow()

    // --- OPERAÇÕES DE METAS ---

    // Busca todas as metas em tempo real (Flow)
    fun getTodasMetas(): Flow<List<Meta>> {
        return metaDao.getTodasMetas()
    }

    // Salva uma nova meta ou atualiza uma existente
    suspend fun salvarMeta(meta: Meta) {
        metaDao.salvarMeta(meta)
    }

    // Remove uma meta do planejamento
    suspend fun excluirMeta(meta: Meta) {
        metaDao.excluirMeta(meta)
    }

    fun getTodasContas(): Flow<List<ContaSaldo>> {
        return contaSaldoDao.getTodasContas()
    }

    // Adiciona um valor específico ao que já foi guardado
    suspend fun adicionarAporte(id: Int, valor: Double) {
        metaDao.adicionarAporte(id, valor)
    }

    suspend fun realizarAporte(meta: Meta, contaId: String, valor: Double) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                // 1. Subtrair o valor do saldo da conta bancária
                contaSaldoDao.subtrairSaldo(contaId, valor)

                // 2. Aumentar o valor guardado na meta
                metaDao.adicionarAporte(meta.id, valor)

                // 3. Criar registro no extrato
                val transacaoReserva = DespesasDomain(
                    id = 0,
                    pic = "reserva",
                    descricao = "Aporte: ${meta.nome}",
                    valor = valor,
                    data = Date().time,
                    conta = contaId,
                    categoria = "Reserva",
                    tipo = TipoDespesa.DEBITO,
                    pago = true
                )
                // Use o DAO que aceita DespesasDomain se existir, ou converta para Despesa
                despesaDao.insert(transacaoReserva)
            }
        }
}
