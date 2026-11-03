package com.meudinheiro.domain

import com.meudinheiro.componentes.filtrarGruposIcones
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconesCategoriaTest {

    /** Mesma lista (e ordem) do portal: GRUPOS_PICS em picsCatalogo.ts. O vitest do portal compara com o .kt. */
    private val chavesPortal = listOf(
        "mercado",
        "padaria",
        "cafe",
        "pizza",
        "delivery",
        "bar",
        "sorvete",
        "fastfood",
        "acougue",
        "hortifruti",
        "aluguel",
        "condominio",
        "luz",
        "agua",
        "gas",
        "internet",
        "telefone",
        "moveis",
        "reforma",
        "ferramentas",
        "jardim",
        "limpeza",
        "eletrodomesticos",
        "onibus",
        "taxi",
        "uber",
        "estacionamento",
        "pedagio",
        "manutencao_carro",
        "bicicleta",
        "metro",
        "farmacia",
        "dentista",
        "exercicio",
        "beleza",
        "cabelo",
        "terapia",
        "consulta",
        "oculos",
        "escola",
        "livros",
        "cursos",
        "material_escolar",
        "festa",
        "musica",
        "esporte",
        "camera",
        "streaming",
        "shows",
        "parque",
        "roupas",
        "calcados",
        "eletronicos",
        "acessorios",
        "compras_online",
        "pet",
        "veterinario",
        "crianca",
        "familia",
        "brinquedos",
        "freelance",
        "bonus",
        "investimento",
        "emprestimo",
        "juros",
        "renda_extra",
        "escritorio",
        "assinatura",
        "lavanderia",
        "correios",
        "cartorio",
        "advogado",
        "contador",
        "seguro",
        "nuvem",
        "aplicativos",
        "viagem",
        "hotel",
        "aviao",
        "praia",
        "passeios",
        "camping",
        "presente",
        "doacao",
        "igreja",
        "flores",
        "aniversario",
        "imposto",
        "ipva",
        "iptu",
        "taxas_bancarias",
        "multa",
        "irpf",
        "outros",
        "favorito",
        "imprevisto",
    )

    private val todas get() = IconesCategoria.GRUPOS.flatMap { it.itens }

    @Test
    fun chavesUnicasEIgualAoPortal() {
        val chaves = todas.map { it.chave }
        assertEquals(chaves.size, chaves.toSet().size)
        assertEquals(chavesPortal, chaves)
        assertTrue(chaves.size >= 60)
        assertTrue(chaves.all { Regex("[a-z][a-z0-9_]*").matches(it) })
    }

    @Test
    fun todoItemDeGrupoExisteNoMapaComRotulo() {
        for (i in todas) {
            assertEquals(i, IconesCategoria.MAPA[i.chave])
            assertTrue(i.rotulo.isNotBlank())
        }
        assertEquals(todas.size, IconesCategoria.MAPA.size)
    }

    @Test
    fun semColisaoComOriginaisNemComDrawables() {
        val chaves = todas.map { it.chave }.toSet()
        assertTrue(chaves.intersect(IconesCategoria.CHAVES_ORIGINAIS.toSet()).isEmpty())
        val extras = setOf("game", "ic_savings", "transferencia", "credit_card", "bank")
        assertTrue(chaves.intersect(extras).isEmpty())
        val dir = listOf("src/main/res/drawable", "app/src/main/res/drawable").map(::File).first { it.isDirectory }
        val drawables = dir.listFiles().orEmpty().map { it.name.substringBefore('.') }.toSet()
        assertTrue(drawables.isNotEmpty())
        assertEquals(emptySet<String>(), chaves.intersect(drawables))
    }

    @Test
    fun resolucaoToleraDesconhecidos() {
        assertNull(IconesCategoria.porChave(null))
        assertNull(IconesCategoria.porChave(""))
        assertNull(IconesCategoria.porChave("ic_default"))
        assertNull(IconesCategoria.porChave("fuel"))
        assertNotNull(IconesCategoria.porChave("Mercado"))
    }

    @Test
    fun buscaFiltraPorRotuloSemAcento() {
        assertEquals(IconesCategoria.GRUPOS, filtrarGruposIcones(IconesCategoria.GRUPOS, "  "))
        val r = filtrarGruposIcones(IconesCategoria.GRUPOS, "cafe")
        assertTrue(r.flatMap { it.itens }.any { it.chave == "cafe" })
        assertTrue(filtrarGruposIcones(IconesCategoria.GRUPOS, "zzzzqq").isEmpty())
    }
}
