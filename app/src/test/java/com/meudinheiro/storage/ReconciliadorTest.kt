package com.meudinheiro.storage

import com.meudinheiro.storage.Reconciliador.Acao
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconciliadorTest {

    private fun decidir(
        local: String? = null,
        nuvem: String? = null,
        historico: String? = null,
        enviado: String? = null,
        pendente: Boolean = false,
        confirmada: Boolean = true,
        modo: ModoSync = ModoSync.MESCLAR
    ) = Reconciliador.decidir(local, nuvem, historico, enviado, pendente, confirmada, modo)

    // ----------------------------------------------------------- mesclar

    @Test fun `novo no celular e enviado`() = assertEquals(Acao.ENVIAR, decidir(local = "a"))

    @Test fun `novo na nuvem e baixado`() = assertEquals(Acao.PUXAR, decidir(nuvem = "a"))

    @Test fun `iguais sem historico apenas carimba, com historico nao faz nada`() {
        assertEquals(Acao.CARIMBAR, decidir(local = "a", nuvem = "a"))
        assertEquals(Acao.NADA, decidir(local = "a", nuvem = "a", historico = "a"))
        assertEquals(Acao.CARIMBAR, decidir(local = "b", nuvem = "b", historico = "a"))
    }

    @Test fun `so a nuvem mudou baixa`() = assertEquals(Acao.PUXAR, decidir(local = "a", nuvem = "b", historico = "a"))

    @Test fun `so o celular mudou envia`() = assertEquals(Acao.ENVIAR, decidir(local = "b", nuvem = "a", historico = "a"))

    @Test fun `conflito (os dois mudaram) a nuvem prevalece`() =
        assertEquals(Acao.PUXAR, decidir(local = "x", nuvem = "y", historico = "a"))

    @Test fun `diferentes sem historico (primeira conexao) a nuvem prevalece`() =
        assertEquals(Acao.PUXAR, decidir(local = "x", nuvem = "y"))

    @Test fun `excluido na nuvem apaga no celular`() =
        assertEquals(Acao.APAGAR_LOCAL, decidir(local = "a", historico = "a"))

    @Test fun `excluido no celular apaga na nuvem`() =
        assertEquals(Acao.APAGAR_REMOTO, decidir(nuvem = "a", historico = "a"))

    @Test fun `historico orfao e esquecido`() {
        assertEquals(Acao.ESQUECER, decidir(historico = "a"))
        assertEquals(Acao.NADA, decidir())
    }

    // ---------------------------------------------------- proteção de dados

    @Test fun `cache vazio offline nao apaga dados do celular`() {
        // Nuvem "vazia" porque o servidor ainda não respondeu: ausência não prova exclusão.
        assertEquals(Acao.NADA, decidir(local = "a", historico = "a", confirmada = false))
    }

    @Test fun `documento com escrita pendente e ignorado`() {
        assertEquals(Acao.NADA, decidir(local = "b", nuvem = "a", historico = "a", pendente = true))
        assertEquals(Acao.NADA, decidir(local = "a", historico = "a", pendente = true))
    }

    @Test fun `edicao feita depois do envio e reenviada em vez de perdida`() {
        // enviamos "a"; antes do servidor confirmar o usuário editou para "b"; chega o ack com "a".
        assertEquals(Acao.ENVIAR, decidir(local = "b", nuvem = "a", enviado = "a"))
    }

    @Test fun `exclusao feita depois do envio apaga na nuvem em vez de ressuscitar`() {
        assertEquals(Acao.APAGAR_REMOTO, decidir(nuvem = "a", enviado = "a"))
    }

    // ------------------------------------------------------ modos forçados

    @Test fun `celular vence envia o que difere e apaga o que so existe na nuvem`() {
        assertEquals(Acao.ENVIAR, decidir(local = "x", nuvem = "y", historico = "y", modo = ModoSync.CELULAR_VENCE))
        assertEquals(Acao.ENVIAR, decidir(local = "x", modo = ModoSync.CELULAR_VENCE))
        assertEquals(Acao.APAGAR_REMOTO, decidir(nuvem = "y", historico = "y", modo = ModoSync.CELULAR_VENCE))
        assertEquals(Acao.APAGAR_REMOTO, decidir(nuvem = "y", modo = ModoSync.CELULAR_VENCE))
    }

    @Test fun `nuvem vence baixa o que difere e apaga o que so existe no celular`() {
        assertEquals(Acao.PUXAR, decidir(local = "x", nuvem = "y", historico = "x", modo = ModoSync.NUVEM_VENCE))
        assertEquals(Acao.PUXAR, decidir(nuvem = "y", modo = ModoSync.NUVEM_VENCE))
        assertEquals(Acao.APAGAR_LOCAL, decidir(local = "x", modo = ModoSync.NUVEM_VENCE))
    }

    @Test fun `nuvem vence nao apaga nada local enquanto o servidor nao confirmar`() {
        assertEquals(Acao.NADA, decidir(local = "x", modo = ModoSync.NUVEM_VENCE, confirmada = false))
    }
}
