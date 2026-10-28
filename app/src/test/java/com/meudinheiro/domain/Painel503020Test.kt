package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Painel503020.CategoriaValor
import com.meudinheiro.domain.Painel503020.Grupo
import com.meudinheiro.domain.Painel503020.StatusGrupo
import com.meudinheiro.domain.Painel503020.StatusReserva
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** Mesmos cenários e números de portal/src/lib/finance/painel503020.test.ts. */
class Painel503020Test {

    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis

    private var seq = 9000L
    private fun d(
        data: Long, descricao: String, categoria: String, valor: Double,
        tipo: TipoDespesa = TipoDespesa.DEBITO, natureza: String = Natureza.NORMAL, pago: Boolean = true, cartaoId: Int? = null
    ) = Despesa(
        id = seq++, descricao = descricao, valor = valor, data = Date(data), categoria = categoria, conta = "111", pic = "p",
        tipo = tipo, mes = Financas.mesDe(data), ano = Financas.anoDe(data), cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private val chavesR50 = listOf(
        "mercado", "padaria", "cafe", "pizza", "delivery", "bar", "sorvete", "fastfood", "acougue", "hortifruti",
        "aluguel", "condominio", "luz", "agua", "gas", "internet", "telefone", "moveis", "reforma", "ferramentas", "jardim", "limpeza", "eletrodomesticos",
        "onibus", "taxi", "uber", "estacionamento", "pedagio", "manutencao_carro", "bicicleta", "metro",
        "farmacia", "dentista", "exercicio", "beleza", "cabelo", "terapia", "consulta", "oculos",
        "escola", "livros", "cursos", "material_escolar",
        "festa", "musica", "esporte", "camera", "streaming", "shows", "parque",
        "roupas", "calcados", "eletronicos", "acessorios", "compras_online",
        "pet", "veterinario", "crianca", "familia", "brinquedos",
        "investimento", "emprestimo", "juros", "escritorio",
        "assinatura", "lavanderia", "correios", "cartorio", "advogado", "contador", "seguro", "nuvem", "aplicativos",
        "viagem", "hotel", "aviao", "praia", "passeios", "camping",
        "presente", "doacao", "igreja", "flores", "aniversario",
        "imposto", "ipva", "iptu", "taxas_bancarias", "multa", "irpf",
        "favorito", "imprevisto"
    )

    // ----------------------------------------------------------------- classificação

    @Test fun `casa o nome sem acento e sem diferenciar maiusculas`() {
        val c = Painel503020::classificar
        assertEquals(Grupo.NECESSIDADES, c("Supermercado", emptyMap()))
        assertEquals(Grupo.NECESSIDADES, c("ALIMENTAÇÃO", emptyMap()))
        assertEquals(Grupo.DESEJOS, c("Restaurantes", emptyMap()))
        assertEquals(Grupo.DESEJOS, c("Lazer", emptyMap()))
        assertEquals(Grupo.POUPANCA, c("Reserva", emptyMap()))
        assertEquals(Grupo.NECESSIDADES, c("Taxas bancárias", emptyMap()))
        assertEquals(Grupo.NECESSIDADES, c("manutencao_carro", emptyMap()))
        assertEquals(Grupo.DESEJOS, c("  Streaming ", emptyMap()))
    }

    @Test fun `todas as padrao e as chaves de icones R50 de despesa tem grupo`() {
        for (nome in listOf("Combustível", "Alimentação", "Transporte", "Compras", "Cinema", "Saúde", "Educação", "Oficina", "Supermercado", "Academia", "Jogos", "Bebidas", "Lanche", "Reserva")) {
            assertNotNull(nome, Painel503020.classificar(nome))
        }
        assertNull(Painel503020.classificar("Salário")) // receita
        for (k in chavesR50) assertNotNull(k, Painel503020.classificar(k))
    }

    @Test fun `palavra isolada so vale sem conflito e sem match nao classifica`() {
        assertEquals(Grupo.NECESSIDADES, Painel503020.classificar("Pet shop da esquina"))
        assertNull(Painel503020.classificar("Casa de praia")) // casa x praia
        assertNull(Painel503020.classificar("Outros"))
        assertNull(Painel503020.classificar(""))
        assertNull(Painel503020.classificar("Coisas diversas"))
    }

    @Test fun `sobrescrita do usuario vence o mapa padrao`() {
        val ov = mapOf("outros" to Grupo.DESEJOS, "lazer" to Grupo.NECESSIDADES)
        assertEquals(Grupo.DESEJOS, Painel503020.classificar("Outros", ov))
        assertEquals(Grupo.NECESSIDADES, Painel503020.classificar("LAZER", ov))
    }

    @Test fun `config invalida volta ao padrao e o texto ida e volta`() {
        assertEquals(Painel503020.CONFIG_PADRAO, Painel503020.desserializar(null))
        assertEquals(Painel503020.CONFIG_PADRAO, Painel503020.desserializar("lixo\nmetas\tx\ty"))
        val c = Painel503020.normalizarConfig(40, 200, null, 99, mapOf("Outros" to Grupo.DESEJOS))
        assertEquals(Painel503020.Metas(40, 30, 20), c.metas)
        assertEquals(6, c.mesesReserva)
        assertEquals(mapOf("outros" to Grupo.DESEJOS), c.overrides)
        val cfg = Painel503020.normalizarConfig(40, 30, 30, 3, mapOf("coisas diversas" to Grupo.NECESSIDADES))
        assertEquals(cfg, Painel503020.desserializar(Painel503020.serializar(cfg)))
    }

    // ----------------------------------------------------------------- painel do mês

    private val despesas = listOf(
        d(ms(2026, 10, 2), "Salário", "Salário", 5000.0, TipoDespesa.CREDITO),
        d(ms(2026, 10, 3), "Freela", "Freelance", 1000.0, TipoDespesa.CREDITO),
        d(ms(2026, 10, 4), "A receber", "Salário", 500.0, TipoDespesa.CREDITO, pago = false),
        d(ms(2026, 10, 5), "Aluguel", "Moradia", 1500.0),
        d(ms(2026, 10, 6), "Mercado", "Supermercado", 800.0),
        d(ms(2026, 10, 7), "Conta de luz", "Luz", 200.0),
        d(ms(2026, 10, 8), "Almoço", "Restaurantes", 400.0),
        d(ms(2026, 10, 9), "Estorno", "Restaurantes", 50.0, TipoDespesa.CREDITO, pago = false, cartaoId = 10),
        d(ms(2026, 10, 10), "Netflix", "Streaming", 100.0),
        d(ms(2026, 10, 11), "Cinema", "Lazer", 300.0),
        d(ms(2026, 10, 12), "CDB", "Investimentos", 600.0),
        d(ms(2026, 10, 13), "Ração", "Pet shop da esquina", 150.0),
        d(ms(2026, 10, 14), "Diversos", "Coisas Diversas", 120.0),
        d(ms(2026, 10, 15), "Aporte: Viagem", "Meta", 500.0, natureza = Natureza.APORTE_META),
        d(ms(2026, 10, 16), "Resgate: Viagem", "Meta", 100.0, TipoDespesa.CREDITO, natureza = Natureza.RESGATE_META),
        d(ms(2026, 10, 17), "Fatura cartão", "Cartão", 1200.0, natureza = Natureza.PAGAMENTO_FATURA),
        d(ms(2026, 10, 18), "Transf", "Transferência", 300.0, natureza = Natureza.TRANSFERENCIA)
    )
    private val p = Painel503020.painel(despesas, 10, 2026)

    @Test fun `classifica e fecha as somas em centavos`() {
        assertEquals(600000L, p.receitaCentavos)
        assertEquals(265000L, p.necessidades.totalCentavos)
        assertEquals(75000L, p.desejos.totalCentavos) // estorno de cartão abate Restaurantes (400 - 50)
        assertEquals(60000L, p.poupancaCategoriasCentavos)
        assertEquals(40000L, p.poupancaMetasCentavos)
        assertEquals(100000L, p.poupanca.totalCentavos)
        assertEquals(12000L, p.naoClassificadoCentavos)
        assertEquals(412000L, p.despesasCentavos)
        val kpi = Dinheiro.centavos(Financas.kpisPeriodo(despesas, Financas.inicioDoMes(10, 2026), Financas.fimDoMes(10, 2026)).despesasTotal)
        assertEquals(kpi, p.necessidades.totalCentavos + p.desejos.totalCentavos + p.poupancaCategoriasCentavos + p.naoClassificadoCentavos)
    }

    @Test fun `percentuais em decimos e status contra 50-30-20`() {
        assertEquals(442L, p.necessidades.percentualDecimos)
        assertEquals(125L, p.desejos.percentualDecimos)
        assertEquals(167L, p.poupanca.percentualDecimos)
        assertEquals(listOf(StatusGrupo.OK, StatusGrupo.OK, StatusGrupo.ABAIXO), listOf(p.necessidades.status, p.desejos.status, p.poupanca.status))
        assertEquals(listOf("Moradia", "Supermercado", "Luz", "Pet shop da esquina"), p.necessidades.categorias.map { it.nome })
    }

    @Test fun `lista a categoria sem classificacao e da dicas`() {
        assertEquals(listOf(CategoriaValor("Coisas Diversas", 12000)), p.naoClassificadas)
        assertEquals(
            listOf(
                "Poupança abaixo de 20% da receita: faltam R$ 200,00 para chegar à meta. Automatize um aporte logo após receber.",
                "1 categoria(s) sem classificação (R$ 120,00): Coisas Diversas. Classifique-as para os percentuais ficarem completos."
            ),
            p.dicas
        )
    }

    @Test fun `sobrescrita e metas personalizadas mudam o resultado`() {
        val cfg = Painel503020.normalizarConfig(40, 30, 20, null, mapOf("coisas diversas" to Grupo.DESEJOS, "streaming" to Grupo.NECESSIDADES))
        val q = Painel503020.painel(despesas, 10, 2026, cfg)
        assertEquals(emptyList<CategoriaValor>(), q.naoClassificadas)
        assertEquals(75000L + 12000 - 10000, q.desejos.totalCentavos)
        assertEquals(275000L, q.necessidades.totalCentavos)
        assertEquals(StatusGrupo.ACIMA, q.necessidades.status) // 45,8% > 40%
        assertEquals(40, q.necessidades.metaPercentual)
    }

    @Test fun `mes sem receita nao divide por zero`() {
        val q = Painel503020.painel(listOf(d(ms(2026, 10, 5), "Mercado", "Supermercado", 800.0)), 10, 2026)
        assertEquals(0L, q.receitaCentavos)
        assertNull(q.necessidades.percentualDecimos)
        assertEquals(StatusGrupo.SEM_RECEITA, q.necessidades.status)
        assertEquals("Sem receita realizada neste mês: registre (ou marque como recebidas) suas entradas para ver os percentuais.", q.dicas[0])
    }

    @Test fun `resgate maior que aporte nao deixa a poupanca negativa`() {
        val q = Painel503020.painel(
            listOf(
                d(ms(2026, 10, 2), "Salário", "Salário", 1000.0, TipoDespesa.CREDITO),
                d(ms(2026, 10, 3), "Resgate", "Meta", 300.0, TipoDespesa.CREDITO, natureza = Natureza.RESGATE_META)
            ),
            10, 2026
        )
        assertEquals(-30000L, q.poupancaMetasCentavos)
        assertEquals(0L, q.poupanca.totalCentavos)
    }

    // ----------------------------------------------------------------- reserva de emergência

    private val hoje = ms(2026, 10, 15)
    private val historico = listOf(
        d(ms(2026, 7, 5), "Aluguel", "Moradia", 1500.0),
        d(ms(2026, 7, 6), "Mercado", "Supermercado", 500.0),
        d(ms(2026, 7, 7), "Cinema", "Lazer", 300.0),
        d(ms(2026, 8, 5), "Aluguel", "Moradia", 1500.0),
        d(ms(2026, 8, 6), "Mercado", "Supermercado", 700.0),
        d(ms(2026, 9, 5), "Aluguel", "Moradia", 1500.0),
        d(ms(2026, 9, 6), "Jantar", "Restaurantes", 100.0),
        d(ms(2026, 10, 1), "Mês atual não conta", "Supermercado", 9999.0)
    )
    private val metas = listOf(
        Meta(1, "Reserva de emergência", 10000.0, 1900.0),
        Meta(2, "Viagem", 10000.0, 5000.0)
    )

    @Test fun `meses de cobertura usa contas e metas de reserva sobre a media das essenciais`() {
        assertEquals(190000L, Painel503020.metasDeReservaCentavos(metas))
        val r = Painel503020.reservaEssenciais(historico, 800000, Painel503020.metasDeReservaCentavos(metas), hoje)
        assertEquals(190000L, r.mediaEssenciaisCentavos)
        assertEquals(990000L, r.liquidezCentavos)
        assertEquals(521L, Math.round(r.mesesCobertos!! * 100))
        assertEquals(1140000L, r.metaCentavos)
        assertEquals(150000L, r.faltanteCentavos)
        assertEquals(StatusReserva.ATENCAO, r.status)
    }

    @Test fun `critico abaixo de metade da meta e ok com meta menor`() {
        assertEquals(StatusReserva.CRITICO, Painel503020.reservaEssenciais(historico, 400000, 0, hoje).status)
        val ok = Painel503020.reservaEssenciais(historico, 800000, 190000, hoje, Painel503020.normalizarConfig(null, null, null, 3, emptyMap()))
        assertEquals(StatusReserva.OK, ok.status)
        assertEquals(0L, ok.faltanteCentavos)
        assertEquals(570000L, ok.metaCentavos)
    }

    @Test fun `sem lancamentos nos 3 meses anteriores nao ha dados`() {
        val r = Painel503020.reservaEssenciais(emptyList(), 500000, 0, hoje)
        assertEquals(StatusReserva.SEM_DADOS, r.status)
        assertNull(r.mesesCobertos)
        assertNull(r.metaCentavos)
        val s = Painel503020.reservaEssenciais(listOf(d(ms(2026, 9, 5), "Cinema", "Lazer", 50.0)), 500000, 0, hoje)
        assertEquals(StatusReserva.SEM_DADOS, s.status)
    }
}
