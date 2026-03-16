package com.meudinheiro.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.PatrimonioDao
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.BackupDto
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
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.ResumoDto
import com.meudinheiro.data.ResumoFinanceiroDto
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.TransferenciaAgendada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class MainRepository(private val context: Context, private val dao: ContaSaldoDao? = null,private val patrimonioDao: PatrimonioDao? = null) {
    private val database = AppDatabase.getDatabase(context)
    private val db = AppDatabase.getInstance(context)

    // DAOs
    private val despesaDao = db.despesaDao()
    private val contaSaldoDao = db.contaSaldoDao()
    private val despesaFixaDao = db.despesaFixaDao()
    private val categoriaDao = db.categoriaDao()
    private val orcamentoDao = db.orcamentoDao()
    private val metaDao = db.metaDao()

    private val _atualizacaoSinal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val atualizacaoSinal = _atualizacaoSinal.asSharedFlow()

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
                    pago = false, // Nasce como não paga (pendente),
                    mes = dataDesteMes.get(Calendar.MONTH) + 1,
                    ano = dataDesteMes.get(Calendar.YEAR)
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
        val backup = BackupDto(
            categorias = categoriaDao.obterTodasStatic(),
            despesas = despesaDao.obterTodasStatic(),
            despesasFixas = despesaFixaDao.obterTodasStatic(),
            contas = contaSaldoDao.obterTodasStatic(),
            metas = metaDao.obterTodasStatic(),
            orcamentos = orcamentoDao.obterTodasStatic()
        )
        return Gson().toJson(backup)
    }

    // 2. Limpa o banco atual e restaura o backup
    suspend fun restaurarBackupCompleto(json: String) {
        // 1. Converte o JSON para o Objeto DTO
        val backupData = try {
            Gson().fromJson(json, BackupDto::class.java)
        } catch (e: Exception) {
            throw Exception("Formato de JSON inválido ou incompatível.")
        }

        // 2. Executa a transação no banco
        database.withTransaction {

            // --- A. METAS ---
            val contasSeguras = backupData.contas?.map { contasaldo: ContaSaldo ->
                contasaldo.copy(
                    id = 0, // Zera o ID para gerar um novo
                    saldo = contasaldo.saldo ?: 0.0,
                    banco = contasaldo.banco,
                    pic = contasaldo.pic,
                    agencia = contasaldo.agencia,
                    conta = contasaldo.conta,
                    titular = contasaldo.titular
                )
            } ?: emptyList()

            if (contasSeguras.isNotEmpty()) {
                contaSaldoDao.limparTudo()
                contaSaldoDao.inserirTodas(contasSeguras)
            }


            // --- A. METAS ---
            val metasSeguras = backupData.metas?.map { meta: Meta ->
                meta.copy(
                    id = 0, // Zera o ID para gerar um novo
                    nome = meta.nome ?: "Meta Restaurada",
                    valorObjetivo = meta.valorObjetivo ?: 0.0,
                    valorGuardado = meta.valorGuardado ?: 0.0
                )
            } ?: emptyList()

            if (metasSeguras.isNotEmpty()) {
                metaDao.limparTudo()
                metaDao.inserirTodas(metasSeguras)
            }

            // --- B. DESPESAS FIXAS ---
            val fixasSeguras = backupData.despesasFixas?.map { fixa: DespesaFixa ->
                fixa.copy(
                    id = 0,
                    descricao = fixa.descricao ?: "Despesa Fixa",
                    valor = fixa.valor ?: 0.0,
                    diaVencimento = if (fixa.diaVencimento in 1..31) fixa.diaVencimento else 10,
                    categoria = fixa.categoria ?: "Geral",
                    conta = fixa.conta ?: "Principal"
                )
            } ?: emptyList()

            if (fixasSeguras.isNotEmpty()) {
                despesaFixaDao.limparTudo()
                despesaFixaDao.inserirTodas(fixasSeguras)
            }

            // --- C. ORÇAMENTOS ---
            val orcamentosSeguros = backupData.orcamentos?.map { orc: Orcamento ->
                orc.copy(
                    id = 0,
                    categoria = orc.categoria ?: "Geral",
                    valorLimite = orc.valorLimite ?: 1000.0
                    //mes = orc.mes ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                    //ano = orc.ano ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                )
            } ?: emptyList()

            if (orcamentosSeguros.isNotEmpty()) {
                orcamentoDao.limparTudo()
                orcamentoDao.inserirTodas(orcamentosSeguros)
            }

            // --- D. DESPESAS (PRINCIPAL) ---
            val despesasSeguras = backupData.despesas?.map { despesa: Despesa ->
                despesa.copy(
                    id = 0, // IMPORTANTE: Zera o ID para não dar conflito
                    descricao = despesa.descricao ?: "Sem Descrição",
                    valor = despesa.valor ?: 0.0,
                    categoria = despesa.categoria ?: "Geral",
                    conta = despesa.conta ?: "Principal",
                    pago = despesa.pago ?: false, // Se vier nulo, assume falso
                    tipo = (despesa.tipo ?: "DEBITO") as TipoDespesa,
                    valorOriginal = despesa.valorOriginal ?: 0.0,
                    moedaOriginal = despesa.moedaOriginal ?: "BRL",
                    cotacaoNaData = despesa.cotacaoNaData ?: 1.0
                )
            } ?: emptyList()

            if (despesasSeguras.isNotEmpty()) {
                despesaDao.limparTudo()
                despesaDao.inserirTodas(despesasSeguras)
            }

            // --- E. Categorias ---
            val categoriasSeguras = backupData.categorias?.map { categoria: Categoria ->
                categoria.copy(
                    id = 0, // IMPORTANTE: Zera o ID para não dar conflito
                    pic = categoria.pic ?: "default_pic",
                    nome = categoria.nome ?: "Geral"
                )
            } ?: emptyList()

            if (categoriasSeguras.isNotEmpty()) {
                categoriaDao.limparTudo()
                categoriaDao.inserirTodas(categoriasSeguras)
            }

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
            if (valor <= 0) return@withContext

            db.withTransaction {
                // 1. Atualiza o saldo da conta (Subtração)
                contaSaldoDao.subtrairSaldo(contaId, valor)

                // 2. Atualiza o valor guardado na Meta (Adição)
                metaDao.adicionarAporte(meta.id, valor)

                // 3. Cria a Entity Despesa para o extrato
                val transacaoAporte = Despesa(
                    id = 0, // O Room gera o ID automaticamente se for autoGenerate
                    descricao = "Aporte: ${meta.nome}",
                    valor = valor,
                    data = Date(),
                    conta = contaId,
                    categoria = "Reserva",
                    pic = "reserva",
                    tipo = TipoDespesa.DEBITO,
                    pago = true,
                    mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                    ano = Calendar.getInstance().get(Calendar.YEAR)
                )

                // 4. Usa a função que você especificou
                despesaDao.inserirDespesa(transacaoAporte)
            }
        }

    suspend fun excluirMetaComRestituicao(meta: Meta, contaId: String?) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                // 1. Se houver valor guardado e uma conta destino, devolvemos o dinheiro
                if (meta.valorGuardado > 0 && contaId != null) {
                    contaSaldoDao.subtrairSaldo(
                        contaId,
                        -meta.valorGuardado
                    ) // Soma o valor de volta

                    // Registra a devolução no extrato
                    val transacaoEstorno = Despesa(
                        id = 0,
                        descricao = "Estorno: Meta ${meta.nome}",
                        valor = meta.valorGuardado,
                        data = Date(),
                        conta = contaId,
                        categoria = "Reserva",
                        pic = "reserva",
                        tipo = TipoDespesa.CREDITO, // Entra como crédito
                        pago = true,
                        mes = Calendar.getInstance().get(Calendar.MONTH) + 1,
                        ano = Calendar.getInstance().get(Calendar.YEAR)
                    )
                    despesaDao.inserirDespesa(transacaoEstorno)
                }

                // 2. Remove a meta definitivamente
                metaDao.excluirMeta(meta)
            }
        }

    fun getTotalPoupado(): Flow<Double> {
        return metaDao.getTotalPoupado().map { it ?: 0.0 }
    }

    suspend fun obterResumoGlobal(): ResumoDto = withContext(Dispatchers.IO) {
        val entradas = despesaDao.somarGlobal(TipoDespesa.CREDITO) ?: 0.0
        val saidas = despesaDao.somarGlobal(TipoDespesa.DEBITO) ?: 0.0
        ResumoDto(entradas, saidas)
    }

    suspend fun obterResumoPorPeriodo(inicio: Date, fim: Date): ResumoDto =
        withContext(Dispatchers.IO) {
            val entradas =
                despesaDao.somarPorPeriodo(inicio.time, fim.time, TipoDespesa.CREDITO) ?: 0.0
            val saidas =
                despesaDao.somarPorPeriodo(inicio.time, fim.time, TipoDespesa.DEBITO) ?: 0.0
            ResumoDto(entradas, saidas)
        }

    suspend fun obterTodasAsMetasSync(): List<Meta> {
        return metaDao.obterTodasAsMetasSync()
    }

    // --- ESSA É A FUNÇÃO QUE FALTAVA ---
    fun getTotalDespesasPorPeriodo(mes: Int, ano: Int): Flow<Double> {
        // Formata o mês para ter sempre dois dígitos (01, 02...)
        val mesFormatado = String.format("%02d", mes)
        val anoFormatado = ano.toString()

        return despesaDao.getTotalPorMesEAno(mesFormatado, anoFormatado)
            .map { it ?: 0.0 } // Se for nulo, retorna 0.0
    }

    // Puxa as despesas de um mês específico (e filtra a conta se tiver uma selecionada)
    fun getDespesasPorMes(mes: Int, ano: Int, contaId: String): Flow<List<DespesasDomain>> {
        return despesaDao.getDespesasPorMes(mes, ano, contaId)
    }

    // Puxa as despesas do mês anterior
    fun getDespesasMesAnterior(mes: Int, ano: Int, contaId: String): Flow<List<DespesasDomain>> {
        val mesAnterior = if (mes == 1) 12 else mes - 1
        val anoAnterior = if (mes == 1) ano - 1 else ano
        return despesaDao.getDespesasPorMes(mesAnterior, anoAnterior, contaId)
    }

    // Puxa TODAS as despesas (Filtro "Total")
    fun getTodasDespesas(contaId: String): Flow<List<DespesasDomain>> {
        return despesaDao.getTodasDespesas(contaId)
    }

    // Dentro do MainRepository.kt
    suspend fun transferirEntreContas(origem: String, destino: String, valor: Double): Boolean {
        return contaSaldoDao?.transferir(origem, destino, valor) ?: false
    }
    /*suspend fun transferirEntreContas(origem: String, destino: String, valor: Double) {
        // Aqui o repositório usa o dao que ele já possui internamente
        contaSaldoDao?.transferir(origem, destino, valor)

        // Opcional: recalcular saldos se necessário
        recalcularSaldoTotal(origem)
        recalcularSaldoTotal(destino)
    }*/

    // Busca os agendamentos como Flow
    fun obterAgendamentosPendentesFlow(): Flow<List<TransferenciaAgendada>> {
        return contaSaldoDao.obterAgendamentosPendentesFlow()
    }

    // Exclui o agendamento
    suspend fun excluirAgendamento(id: Int) {
        contaSaldoDao?.excluirAgendamento(id)
    }

    // Salva um novo agendamento
    suspend fun inserirAgendamento(agendamento: TransferenciaAgendada): Long {
        return contaSaldoDao?.inserirAgendamento(agendamento) ?: -1L
    }

    // Busca a lista de transferências para o Worker processar
    suspend fun obterAgendamentosPendentesSync(hoje: Long): List<TransferenciaAgendada> {
        return contaSaldoDao.obterAgendamentosPendentesSync(hoje)
    }

    // Atualiza o status no banco após o sucesso
    suspend fun marcarAgendamentoComoExecutado(id: Int) {
        contaSaldoDao.marcarAgendamentoComoExecutado(id)
    }

    suspend fun limparBancoDeDadosCompleto() {
        contaSaldoDao?.limparTodasAsTabelas()
    }
    fun obterAgendamentosAtivos() = dao?.obterAgendamentosAtivos() ?: flowOf(emptyList())

    fun obterHistoricoPatrimonial(): Flow<List<PatrimonioHistorico>> {
        return patrimonioDao?.obterHistoricoPatrimonial() ?: flowOf(emptyList())
    }

    suspend fun salvarSnapshotPatrimonial(snapshot: PatrimonioHistorico) {
        patrimonioDao?.salvarSnapshot(snapshot)
    }

    suspend fun obterTodasStatic(): List<ContaSaldo> {
        return dao?.obterTodasStatic() ?: emptyList()
    }

    suspend fun obterTotalPendentes(): Double {
        return dao?.somarContasPendentes() ?: 0.0
    }

    suspend fun avisarQueHouveMudanca() {
        _atualizacaoSinal.emit(Unit)
    }

    suspend fun verificarSnapshotMes(mes: String): Boolean {
        return patrimonioDao?.buscarSnapshotPorMes(mes) != null
    }
}
