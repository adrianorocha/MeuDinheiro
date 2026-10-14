/**
 * R67 - papéis e permissões da conta compartilhada. Função pura; a MESMA matriz vive em
 * `app/.../domain/Permissoes.kt` e em `portal/firestore.rules` / `portal/storage.rules` (`papelDe`, `podeLer`, `podeEditar`, `ehAdmin`).
 * A UI usa isto só para esconder/bloquear botões: quem decide de verdade são as regras do servidor.
 */

export type PapelMembro = "LEITOR" | "EDITOR" | "ADMIN";
/** DONO = quem é dono dos dados (uid == raiz); ADMIN implícito, único que concede ADMIN. */
export type Papel = PapelMembro | "DONO";

export const PAPEIS_ATRIBUIVEIS: readonly PapelMembro[] = ["LEITOR", "EDITOR", "ADMIN"];

export const ROTULO_PAPEL: Record<Papel, string> = {
  LEITOR: "Leitor",
  EDITOR: "Editor",
  ADMIN: "Administrador",
  DONO: "Dono",
};

export const DESCRICAO_PAPEL: Record<PapelMembro, string> = {
  LEITOR: "Só consulta: não cria nem altera nada.",
  EDITOR: "Cria e edita lançamentos e itens de rotina; não exclui definitivamente nem altera contas, cartões, orçamentos e metas.",
  ADMIN: "Tudo, inclusive gerenciar membros e convites (menos mexer em outro administrador ou no dono).",
};

export type Acao =
  | "VER"
  | "LANCAMENTO_CRIAR"
  | "LANCAMENTO_EDITAR"
  | "LANCAMENTO_EXCLUIR"
  | "ANEXO_VER"
  | "ANEXO_ADICIONAR"
  | "ANEXO_EXCLUIR"
  | "ROTINA_EDITAR"
  | "ESTRUTURA_EDITAR"
  | "LIXEIRA_RESTAURAR"
  | "LIXEIRA_ESVAZIAR"
  | "MEMBROS_VER"
  | "MEMBROS_GERENCIAR"
  | "CONVITE_GERAR"
  | "ADMIN_CONCEDER"
  | "CONTA_SAIR"
  | "PROPRIEDADE_TRANSFERIR";

export const TODAS_ACOES: readonly Acao[] = [
  "VER",
  "LANCAMENTO_CRIAR",
  "LANCAMENTO_EDITAR",
  "LANCAMENTO_EXCLUIR",
  "ANEXO_VER",
  "ANEXO_ADICIONAR",
  "ANEXO_EXCLUIR",
  "ROTINA_EDITAR",
  "ESTRUTURA_EDITAR",
  "LIXEIRA_RESTAURAR",
  "LIXEIRA_ESVAZIAR",
  "MEMBROS_VER",
  "MEMBROS_GERENCIAR",
  "CONVITE_GERAR",
  "ADMIN_CONCEDER",
  "CONTA_SAIR",
  "PROPRIEDADE_TRANSFERIR",
];

const ORDEM: Record<Papel, number> = { LEITOR: 1, EDITOR: 2, ADMIN: 3, DONO: 4 };

/** Papel mínimo por ação (null = ninguém / tratada à parte). */
const MINIMO: Record<Acao, Papel | null> = {
  VER: "LEITOR",
  ANEXO_VER: "LEITOR",
  MEMBROS_VER: "LEITOR",
  LANCAMENTO_CRIAR: "EDITOR",
  LANCAMENTO_EDITAR: "EDITOR",
  LANCAMENTO_EXCLUIR: "EDITOR",
  ANEXO_ADICIONAR: "EDITOR",
  ROTINA_EDITAR: "EDITOR",
  ANEXO_EXCLUIR: "ADMIN",
  ESTRUTURA_EDITAR: "ADMIN",
  LIXEIRA_RESTAURAR: "ADMIN",
  LIXEIRA_ESVAZIAR: "ADMIN",
  MEMBROS_GERENCIAR: "ADMIN",
  CONVITE_GERAR: "ADMIN",
  ADMIN_CONCEDER: "DONO",
  CONTA_SAIR: null,
  PROPRIEDADE_TRANSFERIR: null,
};

/** `papel` nulo/indefinido = sem acesso (não é membro): nega tudo. */
export function podeExecutar(papel: Papel | null | undefined, acao: Acao): boolean {
  if (!papel) return false;
  if (acao === "CONTA_SAIR") return papel !== "DONO"; // o dono não "sai" da própria conta
  if (acao === "PROPRIEDADE_TRANSFERIR") return false; // não existe transferência de propriedade
  const min = MINIMO[acao];
  return min !== null && ORDEM[papel] >= ORDEM[min];
}

/**
 * Papel gravado no documento de membro. Ausente/vazio (membro do R32, anterior ao R67) = EDITOR; valor desconhecido = LEITOR
 * (o mais restritivo); "DONO" nunca vem de documento.
 */
export function papelDoDocumento(bruto: unknown): PapelMembro {
  const t = typeof bruto === "string" ? bruto.trim().toUpperCase() : "";
  if (t === "") return "EDITOR";
  if (t === "LEITOR" || t === "EDITOR" || t === "ADMIN") return t;
  return "LEITOR";
}

/** Papéis que `meu` pode gravar em convites e membros novos (ADMIN não cria outro ADMIN). */
export function papeisConcediveis(meu: Papel | null | undefined): PapelMembro[] {
  if (meu === "DONO") return ["LEITOR", "EDITOR", "ADMIN"];
  if (meu === "ADMIN") return ["LEITOR", "EDITOR"];
  return [];
}

/** Pode alterar o papel de / remover um membro cujo papel é `alvo`? Só o dono mexe em ADMIN. */
export function podeGerenciarMembro(meu: Papel | null | undefined, alvo: Papel): boolean {
  if (meu === "DONO") return alvo !== "DONO";
  if (meu === "ADMIN") return alvo === "LEITOR" || alvo === "EDITOR";
  return false;
}
