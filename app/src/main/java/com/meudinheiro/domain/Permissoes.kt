package com.meudinheiro.domain

/**
 * R67 — papéis e permissões da conta compartilhada. Função pura; a MESMA matriz vive em `portal/src/lib/finance/permissoes.ts`
 * e em `portal/firestore.rules` / `portal/storage.rules` (funções `papelDe`, `podeLer`, `podeEditar`, `ehAdmin`).
 * A UI usa isto só para esconder/bloquear botões: quem decide de verdade são as regras do servidor.
 */
enum class Papel {
    LEITOR, EDITOR, ADMIN,

    /** Dono dos dados (uid == raiz). Sempre ADMIN implícito; único que concede ADMIN e que não pode sair. */
    DONO;

    companion object {
        /**
         * Papel gravado no documento de membro. Ausente/vazio (membro do R32, anterior ao R67) = EDITOR (o antigo "lê e escreve"
         * sem a exclusão definitiva). Valor desconhecido = LEITOR (o mais restritivo). DONO nunca vem de documento.
         */
        fun doDocumento(bruto: String?): Papel {
            val t = bruto?.trim()?.uppercase().orEmpty()
            if (t.isEmpty()) return EDITOR
            return when (t) {
                "LEITOR" -> LEITOR
                "EDITOR" -> EDITOR
                "ADMIN" -> ADMIN
                else -> LEITOR
            }
        }

        /** Papéis que podem ser gravados em convite/membro. */
        val ATRIBUIVEIS = listOf(LEITOR, EDITOR, ADMIN)
    }

    val rotulo: String
        get() = when (this) {
            LEITOR -> "Leitor"
            EDITOR -> "Editor"
            ADMIN -> "Administrador"
            DONO -> "Dono"
        }
}

enum class Acao {
    VER, LANCAMENTO_CRIAR, LANCAMENTO_EDITAR, LANCAMENTO_EXCLUIR,
    ANEXO_VER, ANEXO_ADICIONAR, ANEXO_EXCLUIR,
    ROTINA_EDITAR, ESTRUTURA_EDITAR, LIXEIRA_RESTAURAR, LIXEIRA_ESVAZIAR,
    MEMBROS_VER, MEMBROS_GERENCIAR, CONVITE_GERAR, ADMIN_CONCEDER,
    CONTA_SAIR, PROPRIEDADE_TRANSFERIR
}

object Permissoes {
    private fun ordem(p: Papel) = when (p) {
        Papel.LEITOR -> 1
        Papel.EDITOR -> 2
        Papel.ADMIN -> 3
        Papel.DONO -> 4
    }

    /** Papel mínimo por ação (null = ninguém). */
    private fun minimo(a: Acao): Papel? = when (a) {
        Acao.VER, Acao.ANEXO_VER, Acao.MEMBROS_VER -> Papel.LEITOR
        Acao.LANCAMENTO_CRIAR, Acao.LANCAMENTO_EDITAR, Acao.LANCAMENTO_EXCLUIR, Acao.ANEXO_ADICIONAR, Acao.ROTINA_EDITAR -> Papel.EDITOR
        Acao.ANEXO_EXCLUIR, Acao.ESTRUTURA_EDITAR, Acao.LIXEIRA_RESTAURAR, Acao.LIXEIRA_ESVAZIAR,
        Acao.MEMBROS_GERENCIAR, Acao.CONVITE_GERAR -> Papel.ADMIN
        Acao.ADMIN_CONCEDER -> Papel.DONO
        Acao.CONTA_SAIR, Acao.PROPRIEDADE_TRANSFERIR -> null // tratadas à parte
    }

    /** `papel` nulo = sem acesso (não é membro): nega tudo. */
    fun podeExecutar(papel: Papel?, acao: Acao): Boolean {
        if (papel == null) return false
        return when (acao) {
            Acao.CONTA_SAIR -> papel != Papel.DONO          // o dono não "sai" da própria conta
            Acao.PROPRIEDADE_TRANSFERIR -> false            // não existe transferência de propriedade
            else -> ordem(papel) >= ordem(minimo(acao)!!)
        }
    }

    /** Papéis que [meu] pode gravar em convites e membros novos (ADMIN não cria outro ADMIN). */
    fun papeisConcediveis(meu: Papel?): List<Papel> = when (meu) {
        Papel.DONO -> listOf(Papel.LEITOR, Papel.EDITOR, Papel.ADMIN)
        Papel.ADMIN -> listOf(Papel.LEITOR, Papel.EDITOR)
        else -> emptyList()
    }

    /** Pode alterar o papel de / remover um membro cujo papel é [alvo]? Só o dono mexe em ADMIN. */
    fun podeGerenciarMembro(meu: Papel?, alvo: Papel): Boolean = when (meu) {
        Papel.DONO -> alvo != Papel.DONO
        Papel.ADMIN -> alvo == Papel.LEITOR || alvo == Papel.EDITOR
        else -> false
    }
}
