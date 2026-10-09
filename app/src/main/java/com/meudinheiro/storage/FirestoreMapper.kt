package com.meudinheiro.storage

import com.meudinheiro.dao.CartaoDao
import com.meudinheiro.dao.CategoriaDao
import com.meudinheiro.dao.ContaSaldoDao
import com.meudinheiro.dao.DespesaDao
import com.meudinheiro.dao.DespesaFixaDao
import com.meudinheiro.dao.InvestimentoDao
import com.meudinheiro.dao.MetaDao
import com.meudinheiro.dao.OrcamentoDao
import com.meudinheiro.dao.PatrimonioDao
import com.meudinheiro.dao.TransacaoDao
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Categoria
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Investimento
import com.meudinheiro.data.Lixeira
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.PatrimonioHistorico
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.data.Transacao
import com.meudinheiro.data.TransferenciaAgendada
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.Natureza
import java.util.Date

/** Documento Firestore = mapa campo → valor (números, texto, booleano ou nulo). */
typealias Doc = Map<String, Any?>

/**
 * Uma coleção sincronizável (contrato em docs/CONTRATO_DADOS.md, seção 2).
 * - [derivados]: campos-cache recalculados localmente (R3/R4); não entram na comparação de alterações.
 * - Leitura/gravação local trabalham com [Doc] para o motor de sync ser genérico.
 */
class Colecao(
    val nome: String,
    val derivados: Set<String> = emptySet(),
    val lerLocal: suspend () -> Map<String, Doc>,
    val gravarLocal: suspend (List<Doc>) -> Unit,
    val apagarLocal: suspend (List<String>) -> Unit
)

/** Leitores tolerantes: o Firestore devolve inteiros como Long e decimais como Double. */
internal fun Doc.str(chave: String, padrao: String = ""): String = this[chave]?.toString() ?: padrao
internal fun Doc.strOuNulo(chave: String): String? = this[chave]?.toString()
internal fun Doc.long(chave: String, padrao: Long = 0L): Long = longOuNulo(chave) ?: padrao
internal fun Doc.longOuNulo(chave: String): Long? = when (val v = this[chave]) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}
internal fun Doc.int(chave: String, padrao: Int = 0): Int = longOuNulo(chave)?.toInt() ?: padrao
internal fun Doc.intOuNulo(chave: String): Int? = longOuNulo(chave)?.toInt()
internal fun Doc.double(chave: String, padrao: Double = 0.0): Double = when (val v = this[chave]) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull() ?: padrao
    else -> padrao
}
internal fun Doc.bool(chave: String, padrao: Boolean = false): Boolean = this[chave] as? Boolean ?: padrao

/** Entidades ⇄ documentos. Os nomes dos campos são exatamente os das propriedades Kotlin. */
object FirestoreMapper {

    fun toDoc(c: ContaSaldo): Doc = mapOf(
        "id" to c.id, "saldo" to c.saldo, "banco" to c.banco, "pic" to c.pic,
        "agencia" to c.agencia, "conta" to c.conta, "titular" to c.titular
    )

    fun contaFromDoc(d: Doc) = ContaSaldo(
        id = d.int("id"), saldo = d.double("saldo"), banco = d.str("banco"), pic = d.str("pic", "default_pic"),
        agencia = d.str("agencia"), conta = d.str("conta"), titular = d.str("titular")
    )

    fun toDoc(d: Despesa): Doc = mapOf(
        "id" to d.id, "descricao" to d.descricao, "valor" to d.valor, "data" to d.data.time,
        "categoria" to d.categoria, "conta" to d.conta, "pic" to d.pic, "tipo" to d.tipo.name,
        "mes" to d.mes, "ano" to d.ano, "cartaoId" to d.cartaoId, "valorOriginal" to d.valorOriginal,
        "moedaOriginal" to d.moedaOriginal, "cotacaoNaData" to d.cotacaoNaData, "pago" to d.pago,
        "natureza" to d.natureza, "grupoId" to d.grupoId, "autor" to d.autor,
        "fitid" to d.fitid, "conciliadoEm" to d.conciliadoEm
    )

    fun despesaFromDoc(d: Doc): Despesa {
        val ms = d.long("data", System.currentTimeMillis())
        val tipo = runCatching { TipoDespesa.valueOf(d.str("tipo")) }.getOrDefault(TipoDespesa.DEBITO)
        return Despesa(
            id = d.long("id"),
            descricao = d.str("descricao"),
            valor = Dinheiro.arredondar(d.double("valor")),
            data = Date(ms),
            categoria = d.str("categoria", "Geral"),
            conta = d.str("conta"),
            pic = d.str("pic", "default_pic"),
            tipo = tipo,
            mes = Financas.mesDe(ms),
            ano = Financas.anoDe(ms),
            cartaoId = d.intOuNulo("cartaoId")?.takeIf { it != 0 },
            valorOriginal = d.double("valorOriginal"),
            moedaOriginal = d.str("moedaOriginal", "BRL"),
            cotacaoNaData = d.double("cotacaoNaData", 1.0).takeIf { it > 0 } ?: 1.0,
            pago = d.bool("pago"),
            natureza = d.str("natureza", Natureza.NORMAL).takeIf { it in Natureza.TODAS } ?: Natureza.NORMAL,
            grupoId = d.strOuNulo("grupoId"),
            autor = d.strOuNulo("autor"),
            fitid = d.strOuNulo("fitid"),
            conciliadoEm = d.longOuNulo("conciliadoEm")
        )
    }

    fun toDoc(f: DespesaFixa): Doc = mapOf(
        "id" to f.id, "descricao" to f.descricao, "valor" to f.valor, "conta" to f.conta,
        "categoria" to f.categoria, "pic" to f.pic, "tipo" to f.tipo.name,
        "diaVencimento" to f.diaVencimento, "ultimaDataLancamento" to f.ultimaDataLancamento?.time,
        "cartaoId" to f.cartaoId, "pausada" to f.pausada, "pausadaAte" to f.pausadaAte?.time
    )

    fun fixaFromDoc(d: Doc) = DespesaFixa(
        id = d.int("id"), descricao = d.str("descricao"), valor = d.double("valor"), conta = d.str("conta"),
        categoria = d.str("categoria", "Geral"), pic = d.str("pic", "default_pic"),
        tipo = runCatching { TipoDespesa.valueOf(d.str("tipo")) }.getOrDefault(TipoDespesa.DEBITO),
        diaVencimento = d.int("diaVencimento", 1).coerceIn(1, 31),
        ultimaDataLancamento = d.longOuNulo("ultimaDataLancamento")?.let { Date(it) },
        cartaoId = d.intOuNulo("cartaoId")?.takeIf { it != 0 },
        pausada = d.bool("pausada"),
        pausadaAte = d.longOuNulo("pausadaAte")?.let { Date(it) }
    )

    fun toDoc(c: Categoria): Doc = mapOf("id" to c.id, "nome" to c.nome, "pic" to c.pic)
    fun categoriaFromDoc(d: Doc) = Categoria(id = d.int("id"), nome = d.str("nome"), pic = d.str("pic", "default_pic"))

    fun toDoc(o: Orcamento): Doc = mapOf("id" to o.id, "categoria" to o.categoria, "valorLimite" to o.valorLimite)
    fun orcamentoFromDoc(d: Doc) = Orcamento(id = d.int("id"), categoria = d.str("categoria"), valorLimite = d.double("valorLimite"))

    fun toDoc(m: Meta): Doc = mapOf(
        "id" to m.id, "nome" to m.nome, "valorObjetivo" to m.valorObjetivo,
        "valorGuardado" to m.valorGuardado, "icone" to m.icone, "dataAlvo" to m.dataAlvo
    )

    fun metaFromDoc(d: Doc) = Meta(
        id = d.int("id"), nome = d.str("nome"), valorObjetivo = d.double("valorObjetivo"),
        valorGuardado = d.double("valorGuardado"), icone = d.str("icone", "ic_savings"),
        dataAlvo = d.longOuNulo("dataAlvo")
    )

    fun toDoc(l: Lixeira): Doc = mapOf(
        "id" to l.id, "tipo" to l.tipo, "descricao" to l.descricao, "valor" to l.valor,
        "excluidoEm" to l.excluidoEm, "payload" to l.payload
    )

    fun lixeiraFromDoc(d: Doc) = Lixeira(
        id = d.int("id"), tipo = d.str("tipo", "DESPESA"), descricao = d.str("descricao"), valor = d.double("valor"),
        excluidoEm = d.long("excluidoEm"), payload = d.str("payload")
    )

    fun toDoc(i: Investimento): Doc = mapOf(
        "id" to i.id, "nome" to i.nome, "tipo" to i.tipo,
        "valorInvestido" to i.valorInvestido, "valorAtual" to i.valorAtual
    )

    fun investimentoFromDoc(d: Doc) = Investimento(
        id = d.int("id"), nome = d.str("nome"), tipo = d.str("tipo", "Renda Fixa"),
        valorInvestido = d.double("valorInvestido"), valorAtual = d.double("valorAtual")
    )

    fun toDoc(c: Cartao): Doc = mapOf(
        "id" to c.id, "nome" to c.nome, "finalCartao" to c.finalCartao, "tipo" to c.tipo,
        "limiteDisponivel" to c.limiteDisponivel, "limiteTotal" to c.limiteTotal,
        "diaFechamento" to c.diaFechamento, "diaVencimento" to c.diaVencimento, "contaId" to c.contaId,
        "cartaoPrincipalId" to c.cartaoPrincipalId, "limiteProprio" to c.limiteProprio?.takeIf { it > 0.0 }
    )

    fun cartaoFromDoc(d: Doc) = Cartao(
        id = d.int("id"), nome = d.str("nome"), finalCartao = d.str("finalCartao"), tipo = d.str("tipo", "CRÉDITO"),
        limiteDisponivel = d.double("limiteDisponivel"), limiteTotal = d.double("limiteTotal"),
        diaFechamento = d.int("diaFechamento", 1).coerceIn(1, 31),
        diaVencimento = d.int("diaVencimento", 1).coerceIn(1, 31), contaId = d.int("contaId"),
        cartaoPrincipalId = d.intOuNulo("cartaoPrincipalId")?.takeIf { it != 0 },
        limiteProprio = (d["limiteProprio"] as? Number)?.toDouble()?.takeIf { it > 0.0 && it.isFinite() }
    )

    fun toDoc(t: TransferenciaAgendada): Doc = mapOf(
        "id" to t.id, "dataAgendada" to t.dataAgendada, "contaOrigem" to t.contaOrigem,
        "contaDestino" to t.contaDestino, "valor" to t.valor, "executada" to t.executada
    )

    fun agendamentoFromDoc(d: Doc) = TransferenciaAgendada(
        id = d.int("id"), dataAgendada = d.long("dataAgendada"), contaOrigem = d.str("contaOrigem"),
        contaDestino = d.str("contaDestino"), valor = d.double("valor"), executada = d.bool("executada")
    )

    fun toDoc(p: PatrimonioHistorico): Doc = mapOf(
        "id" to p.id, "dataMillis" to p.dataMillis, "valorTotal" to p.valorTotal, "mesReferencia" to p.mesReferencia
    )

    fun patrimonioFromDoc(d: Doc) = PatrimonioHistorico(
        id = d.int("id"), dataMillis = d.long("dataMillis"), valorTotal = d.double("valorTotal"),
        mesReferencia = d.str("mesReferencia")
    )

    fun toDoc(t: Transacao): Doc = mapOf(
        "id" to t.id, "descricao" to t.descricao, "valor" to t.valor, "bancoNome" to t.bancoNome,
        "categoriaNome" to t.categoriaNome, "categoriaCorHex" to t.categoriaCorHex, "timestamp" to t.timestamp
    )

    fun transacaoFromDoc(d: Doc) = Transacao(
        id = d.int("id"), descricao = d.str("descricao"), valor = d.double("valor"), bancoNome = d.str("bancoNome"),
        categoriaNome = d.str("categoriaNome"), categoriaCorHex = d.str("categoriaCorHex", "#FFFFFF"),
        timestamp = d.long("timestamp")
    )

    /**
     * Catálogo na ORDEM DE DEPENDÊNCIA para gravação local (contas antes de cartões, que têm FK).
     * O motor aplica remoções na ordem inversa.
     */
    fun catalogo(db: AppDatabase): List<Colecao> {
        val contas: ContaSaldoDao = db.contaSaldoDao()
        val cartoes: CartaoDao = db.cartaoDao()
        val categorias: CategoriaDao = db.categoriaDao()
        val despesas: DespesaDao = db.despesaDao()
        val fixas: DespesaFixaDao = db.despesaFixaDao()
        val orcamentos: OrcamentoDao = db.orcamentoDao()
        val metas: MetaDao = db.metaDao()
        val investimentos: InvestimentoDao = db.investimentoDao()
        val patrimonio: PatrimonioDao = db.patrimonioDao()
        val transacoes: TransacaoDao = db.transacaoDao()
        val lixeira = db.lixeiraDao()

        fun <T> colecao(
            nome: String,
            derivados: Set<String> = emptySet(),
            ler: suspend () -> List<T>,
            id: (T) -> String,
            doc: (T) -> Doc,
            de: (Doc) -> T,
            gravar: suspend (List<T>) -> Unit,
            apagar: suspend (List<String>) -> Unit
        ) = Colecao(
            nome = nome,
            derivados = derivados,
            lerLocal = { ler().associate { id(it) to doc(it) } },
            gravarLocal = { docs -> gravar(docs.map(de)) },
            apagarLocal = apagar
        )

        return listOf(
            colecao("contas", setOf("saldo"), { contas.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::contaFromDoc,
                { contas.upsertContas(it) }, { ids -> contas.excluirContasPorIds(ids.map(String::toInt)) }),
            colecao("cartoes", setOf("limiteDisponivel"), { cartoes.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::cartaoFromDoc,
                { cartoes.inserirTodas(it) }, { ids -> cartoes.excluirPorIds(ids.map(String::toInt)) }),
            colecao("categorias", emptySet(), { categorias.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::categoriaFromDoc,
                { categorias.inserirTodas(it) }, { ids -> categorias.excluirPorIds(ids.map(String::toInt)) }),
            colecao("despesas", setOf("mes", "ano"), { despesas.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::despesaFromDoc,
                { despesas.inserirTodas(it) }, { ids -> despesas.excluirPorIds(ids.map(String::toLong)) }),
            colecao("despesasFixas", emptySet(), { fixas.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::fixaFromDoc,
                { fixas.inserirTodas(it) }, { ids -> fixas.excluirPorIds(ids.map(String::toInt)) }),
            colecao("orcamentos", emptySet(), { orcamentos.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::orcamentoFromDoc,
                { orcamentos.inserirTodas(it) }, { ids -> orcamentos.excluirPorIds(ids.map(String::toInt)) }),
            colecao("metas", emptySet(), { metas.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::metaFromDoc,
                { metas.inserirTodas(it) }, { ids -> metas.excluirPorIds(ids.map(String::toInt)) }),
            colecao("investimentos", emptySet(), { investimentos.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::investimentoFromDoc,
                { investimentos.inserirTodas(it) }, { ids -> investimentos.excluirPorIds(ids.map(String::toInt)) }),
            colecao("transferenciasAgendadas", emptySet(), { contas.obterAgendamentosStatic() }, { it.id.toString() }, ::toDoc, ::agendamentoFromDoc,
                { contas.upsertAgendamentos(it) }, { ids -> contas.excluirAgendamentosPorIds(ids.map(String::toInt)) }),
            colecao("patrimonio", emptySet(), { patrimonio.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::patrimonioFromDoc,
                { patrimonio.inserirTodas(it) }, { ids -> patrimonio.excluirPorIds(ids.map(String::toInt)) }),
            colecao("lixeira", emptySet(), { lixeira.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::lixeiraFromDoc,
                { lixeira.inserirTodas(it) }, { ids -> lixeira.excluirPorIds(ids.map(String::toInt)) }),
            colecao("transacoes", emptySet(), { transacoes.obterTodasStatic() }, { it.id.toString() }, ::toDoc, ::transacaoFromDoc,
                { transacoes.inserirTodas(it) }, { ids -> transacoes.excluirPorIds(ids.map(String::toInt)) })
        )
    }
}
