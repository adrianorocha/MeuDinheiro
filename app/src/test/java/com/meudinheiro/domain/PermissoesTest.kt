package com.meudinheiro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R67 — matriz papel × ação. O MESMO quadro está em `portal/src/lib/finance/permissoes.test.ts`. */
class PermissoesTest {

    // Colunas: LEITOR, EDITOR, ADMIN, DONO (1 = pode).
    private val matriz = listOf(
        "VER" to "1111",
        "LANCAMENTO_CRIAR" to "0111", "LANCAMENTO_EDITAR" to "0111", "LANCAMENTO_EXCLUIR" to "0111",
        "ANEXO_VER" to "1111", "ANEXO_ADICIONAR" to "0111", "ANEXO_EXCLUIR" to "0011",
        "ROTINA_EDITAR" to "0111", "ESTRUTURA_EDITAR" to "0011",
        "LIXEIRA_RESTAURAR" to "0011", "LIXEIRA_ESVAZIAR" to "0011",
        "MEMBROS_VER" to "1111", "MEMBROS_GERENCIAR" to "0011", "CONVITE_GERAR" to "0011",
        "ADMIN_CONCEDER" to "0001",
        "CONTA_SAIR" to "1110", "PROPRIEDADE_TRANSFERIR" to "0000"
    )

    @Test fun `matriz completa papel x acao`() {
        assertEquals("a matriz cobre todas as ações", Acao.entries.map { it.name }.toSet(), matriz.map { it.first }.toSet())
        val papeis = listOf(Papel.LEITOR, Papel.EDITOR, Papel.ADMIN, Papel.DONO)
        for ((acao, linha) in matriz) {
            papeis.forEachIndexed { i, p ->
                assertEquals("$p x $acao", linha[i] == '1', Permissoes.podeExecutar(p, Acao.valueOf(acao)))
            }
        }
    }

    @Test fun `sem papel nega tudo`() {
        Acao.entries.forEach { assertFalse(Permissoes.podeExecutar(null, it)) }
    }

    @Test fun `papel do documento - ausente e EDITOR, desconhecido e LEITOR, DONO nunca vem de documento`() {
        assertEquals(Papel.EDITOR, Papel.doDocumento(null))
        assertEquals(Papel.EDITOR, Papel.doDocumento(" "))
        assertEquals(Papel.ADMIN, Papel.doDocumento("admin"))
        assertEquals(Papel.LEITOR, Papel.doDocumento("SUPERUSUARIO"))
        assertEquals(Papel.LEITOR, Papel.doDocumento("DONO"))
    }

    @Test fun `papeis concediveis - ADMIN nao cria outro ADMIN`() {
        assertEquals(listOf(Papel.LEITOR, Papel.EDITOR, Papel.ADMIN), Permissoes.papeisConcediveis(Papel.DONO))
        assertEquals(listOf(Papel.LEITOR, Papel.EDITOR), Permissoes.papeisConcediveis(Papel.ADMIN))
        assertTrue(Permissoes.papeisConcediveis(Papel.EDITOR).isEmpty())
        assertTrue(Permissoes.papeisConcediveis(null).isEmpty())
    }

    @Test fun `gerenciar membro - so o dono mexe em ADMIN e ninguem mexe no dono`() {
        assertTrue(Permissoes.podeGerenciarMembro(Papel.DONO, Papel.ADMIN))
        assertFalse(Permissoes.podeGerenciarMembro(Papel.DONO, Papel.DONO))
        assertTrue(Permissoes.podeGerenciarMembro(Papel.ADMIN, Papel.EDITOR))
        assertFalse(Permissoes.podeGerenciarMembro(Papel.ADMIN, Papel.ADMIN))
        assertFalse(Permissoes.podeGerenciarMembro(Papel.EDITOR, Papel.LEITOR))
        assertFalse(Permissoes.podeGerenciarMembro(null, Papel.LEITOR))
    }
}
