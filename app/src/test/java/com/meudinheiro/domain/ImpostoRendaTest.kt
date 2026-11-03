package com.meudinheiro.domain

import com.meudinheiro.data.Anexo
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Divida
import com.meudinheiro.data.Investimento
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.ImpostoRenda.Classe
import com.meudinheiro.domain.ImpostoRenda.Deducao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R61 — MESMOS cenários e números de portal/src/lib/finance/imposto.test.ts. */
class ImpostoRendaTest {

    private fun dt(ano: Int, mes: Int, dia: Int): Long =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis

    private val agora = dt(2026, 2, 10)
    private var seq = 100L

    private fun d(
        data: Long, desc: String, cat: String, valor: Double, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true,
        natureza: String = Natureza.NORMAL, conta: String = "111", cartaoId: Int? = null
    ) = Despesa(
        id = seq++, descricao = desc, valor = valor, data = Date(data), categoria = cat, conta = conta, pic = "", tipo = tipo,
        mes = 1, ano = 2025, cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private fun rec(data: Long, desc: String, cat: String, valor: Double, pago: Boolean = true, natureza: String = Natureza.NORMAL, conta: String = "111") =
        d(data, desc, cat, valor, TipoDespesa.CREDITO, pago, natureza, conta)

    private fun cenario(): List<Despesa> = listOf(
        rec(dt(2024, 12, 1), "Saldo inicial", "Saldo", 1000.0, natureza = Natureza.SALDO_INICIAL),
        rec(dt(2024, 12, 5), "Salario dez/24", "Salário", 4000.0),
        rec(dt(2025, 1, 5), "Salario jan", "Salário", 5000.0),
        rec(dt(2025, 2, 5), "Salario fev", "Salário", 5000.0),
        rec(dt(2025, 12, 20), "13o", "13º Salário", 2500.0),
        rec(dt(2025, 3, 1), "Reembolso viagem", "Reembolso", 300.0),
        rec(dt(2025, 6, 30), "CDB", "Rendimentos", 120.55),
        rec(dt(2025, 4, 10), "Freela site", "Extra", 800.0),
        rec(dt(2025, 5, 2), "Pix de amigo", "Outros", 50.0),
        rec(dt(2025, 12, 30), "Salario previsto", "Salário", 700.0, pago = false),
        rec(dt(2026, 1, 5), "Salario jan/26", "Salário", 5100.0),
        d(dt(2025, 3, 10), "Dentista", "Saúde", 450.0),
        d(dt(2025, 3, 12), "Farmácia Pague Menos", "Saúde", 80.0),
        d(dt(2025, 5, 1), "Plano", "Plano de Saúde", 600.0),
        d(dt(2025, 2, 10), "Faculdade", "Educação", 3000.0),
        d(dt(2025, 8, 10), "Escola", "Educação", 2000.0),
        d(dt(2025, 7, 1), "PGBL", "Previdência", 700.0),
        d(dt(2025, 12, 15), "Dentista pendente", "Saúde", 200.0, pago = false),
        d(dt(2025, 4, 1), "Mercado", "Supermercado", 500.0),
        d(dt(2025, 9, 9), "Exame", "Saúde", 150.0, cartaoId = 7),
        d(dt(2025, 12, 10), "Transferência", "Transferência", 1000.0, natureza = Natureza.TRANSFERENCIA),
        rec(dt(2025, 12, 10), "Transferência", "Transferência", 1000.0, natureza = Natureza.TRANSFERENCIA, conta = "222"),
        d(dt(2026, 1, 10), "Despesa de 2026", "Supermercado", 300.0)
    )

    private val contas = listOf(
        ContaSaldo(1, 0.0, "Nubank", "", "1", "111", "T"),
        ContaSaldo(2, 0.0, "Itaú", "", "1", "222", "T")
    )
    private val investimentos = listOf(Investimento(1, "CDB Banco X", "Renda Fixa", 1000.0, 1100.5))
    private val metas = listOf(Meta(1, "Viagem", 5000.0, 750.0))
    private val dividas = listOf(
        Divida(1, "Financiamento", "FINANCIAMENTO", "Banco Y", 20000.0, ativa = true),
        Divida(2, "Quitada", "OUTRA", null, 0.0, ativa = false)
    )

    private fun rel(ano: Int = 2025, cfg: ImpostoRenda.Config = ImpostoRenda.Config(), desp: List<Despesa> = cenario(), hoje: Long = agora) =
        ImpostoRenda.relatorio(desp, contas, investimentos, metas, dividas, ano, cfg, hoje)

    // ------------------------------------------------------------ classificação

    @Test fun `salario e tributavel, 13o e PLR sao exclusivos, reembolso emprestimo resgate e aporte ficam fora`() {
        assertEquals(Classe.TRIBUTAVEL, ImpostoRenda.classificarRendimento("Salário"))
        assertEquals(Classe.TRIBUTAVEL, ImpostoRenda.classificarRendimento("Férias"))
        assertEquals(Classe.TRIBUTAVEL, ImpostoRenda.classificarRendimento("Pró-labore"))
        assertEquals(Classe.EXCLUSIVO, ImpostoRenda.classificarRendimento("13º Salário"))
        assertEquals(Classe.EXCLUSIVO, ImpostoRenda.classificarRendimento("Décimo terceiro"))
        assertEquals(Classe.EXCLUSIVO, ImpostoRenda.classificarRendimento("PLR"))
        assertEquals(Classe.FORA, ImpostoRenda.classificarRendimento("Reembolso"))
        assertEquals(Classe.FORA, ImpostoRenda.classificarRendimento("Empréstimo recebido"))
        assertEquals(Classe.FORA, ImpostoRenda.classificarRendimento("Resgate de meta"))
        assertEquals(Classe.FORA, ImpostoRenda.classificarRendimento("Aporte"))
        assertEquals(Classe.INVESTIMENTO, ImpostoRenda.classificarRendimento("Rendimentos"))
        assertEquals(Classe.INVESTIMENTO, ImpostoRenda.classificarRendimento("Dividendos"))
        assertEquals(Classe.ISENTO, ImpostoRenda.classificarRendimento("Herança"))
        assertEquals(Classe.ISENTO, ImpostoRenda.classificarRendimento("Doação recebida"))
        assertEquals(Classe.REVISAR, ImpostoRenda.classificarRendimento("Outros"))
    }

    @Test fun `sem pista na categoria usa a descricao e a escolha do usuario vence`() {
        assertEquals(Classe.TRIBUTAVEL, ImpostoRenda.classificarRendimento("Extra", "Freela site"))
        assertEquals(Classe.REVISAR, ImpostoRenda.classificarRendimento("Outros", "Pix de amigo"))
        assertEquals(Classe.ISENTO, ImpostoRenda.classificarRendimento("Salário", "", mapOf("salario" to Classe.ISENTO)))
        assertEquals(Classe.FORA, ImpostoRenda.classificarRendimento("Outros", "", mapOf("outros" to Classe.FORA)))
    }

    @Test fun `deducoes por nome com sobrescrita e NAO`() {
        assertEquals(Deducao.SAUDE, ImpostoRenda.classificarDeducao("Saúde"))
        assertEquals(Deducao.SAUDE, ImpostoRenda.classificarDeducao("Plano de Saúde"))
        assertEquals(Deducao.SAUDE, ImpostoRenda.classificarDeducao("Dentista"))
        assertEquals(Deducao.EDUCACAO, ImpostoRenda.classificarDeducao("Educação"))
        assertEquals(Deducao.EDUCACAO, ImpostoRenda.classificarDeducao("Faculdade"))
        assertEquals(Deducao.PENSAO, ImpostoRenda.classificarDeducao("Pensão alimentícia"))
        assertEquals(Deducao.PREVIDENCIA, ImpostoRenda.classificarDeducao("PGBL"))
        assertEquals(Deducao.DOACAO, ImpostoRenda.classificarDeducao("Doações"))
        assertNull(ImpostoRenda.classificarDeducao("Supermercado"))
        assertEquals(Deducao.SAUDE, ImpostoRenda.classificarDeducao("Lazer", mapOf("lazer" to Deducao.SAUDE)))
        assertNull(ImpostoRenda.classificarDeducao("Saúde", mapOf("saude" to null)))
    }

    @Test fun `remedio e oculos sao marcados como provavel nao dedutivel`() {
        assertTrue(ImpostoRenda.pareceNaoDedutivel("Farmácia Pague Menos"))
        assertTrue(ImpostoRenda.pareceNaoDedutivel("Óculos novos"))
        assertFalse(ImpostoRenda.pareceNaoDedutivel("Dentista"))
    }

    // ------------------------------------------------------------ relatório do ano

    private fun cl(r: ImpostoRenda.Relatorio, c: Classe) = r.rendimentos.porClasse.first { it.classe == c }

    @Test fun `rendimentos realizados por classe, previsto e outros anos ficam de fora`() {
        val r = rel()
        assertEquals(1_080_000L, cl(r, Classe.TRIBUTAVEL).totalCentavos) // 5000 + 5000 + 800 (freela pela descrição)
        assertEquals(3, cl(r, Classe.TRIBUTAVEL).quantidade)
        assertEquals(250_000L, cl(r, Classe.EXCLUSIVO).totalCentavos)
        assertEquals(12_055L, cl(r, Classe.INVESTIMENTO).totalCentavos)
        assertEquals(30_000L, cl(r, Classe.FORA).totalCentavos) // transferência (natureza) nunca entra
        assertEquals(5_000L, cl(r, Classe.REVISAR).totalCentavos)
        assertEquals(70_000L, r.rendimentos.previstoCentavos)
        assertEquals(1_080_000L + 250_000 + 12_055 + 30_000 + 5_000, r.rendimentos.totalRealizadoCentavos)
        assertTrue(r.checagem.confere)
        assertTrue(r.checagem.deducoesConferem)
    }

    @Test fun `deducoes saude sem limite, educacao por pessoa, PGBL ate 12 por cento do tributavel`() {
        val r = rel()
        fun t(x: Deducao) = r.deducoes.porTipo.first { it.tipo == x }
        assertEquals(45_000L + 60_000 + 15_000, t(Deducao.SAUDE).totalCentavos) // dentista + plano + exame (cartão pago)
        assertNull(t(Deducao.SAUDE).limiteCentavos)
        assertEquals(500_000L, t(Deducao.EDUCACAO).totalCentavos)
        assertEquals(ImpostoRenda.LIMITE_EDUCACAO_PADRAO_CENTAVOS, t(Deducao.EDUCACAO).limiteCentavos)
        assertEquals(356_150L, t(Deducao.EDUCACAO).deduzivelCentavos)
        assertEquals(143_850L, t(Deducao.EDUCACAO).excessoCentavos)
        assertEquals(129_600L, r.deducoes.limitePgblCentavos) // 12% de 10.800,00
        assertEquals(70_000L, t(Deducao.PREVIDENCIA).deduzivelCentavos)
        assertEquals(120_000L + 356_150 + 70_000, r.deducoes.totalDeduzivelCentavos)
        assertEquals(1_080_000L - (120_000 + 356_150 + 70_000), r.indicativoBaseCentavos)
    }

    @Test fun `nao dedutiveis, pendentes e comprovantes`() {
        val r = rel()
        assertEquals(listOf("Farmácia Pague Menos"), r.deducoes.naoDedutiveis.map { it.descricao })
        assertEquals(8_000L, r.deducoes.naoDedutiveisCentavos)
        assertEquals(20_000L, r.deducoes.pendentesCentavos)
        assertEquals(6, r.deducoes.quantidade)
        assertEquals(6, r.deducoes.semComprovante)
    }

    @Test fun `reconhece anexos quando o lancamento tem`() {
        val base = cenario()
        val comAnexo = base.map {
            if (it.descricao == "Dentista") it.copy(anexos = listOf(Anexo("a", "recibo.pdf", "application/pdf", 10, "local:a", 1L))) else it
        }
        val r = rel(desp = comAnexo)
        assertEquals(5, r.deducoes.semComprovante)
        assertTrue(r.deducoes.grupos.flatMap { it.itens }.first { it.descricao == "Dentista" }.comprovante)
    }

    @Test fun `saldo das contas em 31 12 so ate a data`() {
        val r = rel()
        assertEquals(1_044_055L, r.bens.contas.first { it.detalhe == "conta 111" }.centavos)
        assertEquals(100_000L, r.bens.contas.first { it.detalhe == "conta 222" }.centavos)
        assertEquals(1_044_055L + 100_000, r.bens.totalContasCentavos)
        assertEquals(1_044_055L, ImpostoRenda.saldoContaEm(cenario(), "111", ImpostoRenda.fimDoAno(2025)))
        assertEquals(1_044_055L - 30_000 + 510_000, ImpostoRenda.saldoContaEm(cenario(), "111", Calendar.getInstance().apply { clear(); set(2026, 0, 31, 23, 59, 59) }.timeInMillis))
    }

    @Test fun `investimentos metas e dividas ativas, com aviso de saldo atual fora de ano menos 1`() {
        val r = rel()
        assertEquals(110_050L, r.bens.totalInvestimentosCentavos)
        assertEquals(75_000L, r.bens.totalMetasCentavos)
        assertEquals(listOf("Financiamento"), r.bens.dividas.map { it.nome })
        assertEquals(2_000_000L, r.bens.totalDividasCentavos)
        assertNull(r.bens.avisoSaldoAtual)
        assertTrue(rel(ano = 2024).bens.avisoSaldoAtual!!.contains("ATUAL"))
    }

    // ------------------------------------------------------------ configuração do usuário

    @Test fun `classe escolhida muda os totais e a checagem continua fechando`() {
        val cfg = ImpostoRenda.Config.deDoc(mapOf("rendimentos" to mapOf("Outros" to "ISENTO", "reembolso" to "TRIBUTAVEL")))
        val r = rel(cfg = cfg)
        assertEquals(5_000L, cl(r, Classe.ISENTO).totalCentavos)
        assertEquals(1_080_000L + 30_000, cl(r, Classe.TRIBUTAVEL).totalCentavos)
        assertTrue(r.checagem.confere)
    }

    @Test fun `limite de educacao e pessoas configuraveis e categoria marcada manualmente entra`() {
        val cfg = ImpostoRenda.Config.deDoc(
            mapOf("limiteEducacaoCentavos" to 200_000, "pessoasEducacao" to 2, "deducoes" to mapOf("Supermercado" to "DOACAO", "Saúde" to "NAO"))
        )
        val r = rel(cfg = cfg)
        fun t(x: Deducao) = r.deducoes.porTipo.first { it.tipo == x }
        assertEquals(400_000L, t(Deducao.EDUCACAO).limiteCentavos)
        assertEquals(400_000L, t(Deducao.EDUCACAO).deduzivelCentavos)
        assertEquals(50_000L, t(Deducao.DOACAO).totalCentavos)
        assertEquals(60_000L, t(Deducao.SAUDE).totalCentavos) // só "Plano de Saúde"
    }

    @Test fun `PGBL acima de 12 por cento e limitado`() {
        val r = rel(desp = cenario() + d(dt(2025, 9, 1), "PGBL extra", "Previdência", 2000.0))
        val p = r.deducoes.porTipo.first { it.tipo == Deducao.PREVIDENCIA }
        assertEquals(270_000L, p.totalCentavos)
        assertEquals(129_600L, p.deduzivelCentavos)
        assertEquals(140_400L, p.excessoCentavos)
    }

    @Test fun `valores invalidos voltam ao padrao e a config vai e volta do documento`() {
        val c = ImpostoRenda.Config.deDoc(
            mapOf("limiteEducacaoCentavos" to -5, "pessoasEducacao" to 0, "rendimentos" to mapOf("x" to "LIXO"), "deducoes" to mapOf("y" to "LIXO"))
        )
        assertEquals(ImpostoRenda.Config(), c)
        assertEquals(ImpostoRenda.Config(), ImpostoRenda.Config.deDoc(null))
        val cfg = ImpostoRenda.Config(mapOf("outros" to Classe.FORA), mapOf("saude" to null, "lazer" to Deducao.SAUDE), 123_456L, 3)
        assertEquals(cfg, ImpostoRenda.Config.deDoc(cfg.toDoc()))
    }

    // ------------------------------------------------------------ ano sem dados e exportação

    @Test fun `ano sem dados tem zeros e tudo confere`() {
        val r = ImpostoRenda.relatorio(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 2019, ImpostoRenda.Config(), agora)
        assertEquals(0L, r.rendimentos.totalRealizadoCentavos)
        assertEquals(0L, r.deducoes.totalDeduzivelCentavos)
        assertEquals(0L, r.bens.totalContasCentavos)
        assertTrue(r.checagem.confere)
        assertNull(r.bens.avisoSaldoAtual)
    }

    @Test fun `documento tem aviso em destaque, resumo e listas e o CSV usa virgula`() {
        val doc = ImpostoRenda.documento(rel())
        assertTrue(doc.avisos[0].contains("NÃO é a declaração oficial"))
        assertTrue(doc.linhas.size > 10)
        assertTrue(doc.linhas.any { it[0] == "Deduções candidatas" && it[2] == "Dentista" && it[5] == "Não" })
        val csv = ImpostoRenda.csv(doc)
        assertTrue(csv.contains("Saldo das contas em 31/12"))
        assertTrue(csv.contains(";450,00"))
    }
}
