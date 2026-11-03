export type Tipo = "DEBITO" | "CREDITO";

/** Visão mínima de `users/{uid}/despesas/{id}` (mesmos campos do contrato; `data` em epoch ms). */
export interface DespesaDoc {
  id: string;
  descricao: string;
  valor: number;
  data: number;
  categoria: string;
  tipo: Tipo;
  cartaoId: number | null;
  pago: boolean;
  natureza: string;
}

export interface OrcamentoDoc {
  categoria: string;
  valorLimite: number;
}

export type TipoPush = "VENCIMENTOS" | "ORCAMENTO" | "RESUMO_SEMANAL" | "CONVITE";

export interface PrefsPush {
  vencimentos?: boolean;
  orcamento?: boolean;
  resumoSemanal?: boolean;
  convites?: boolean;
}

export interface Dispositivo {
  /** id do documento em `users/{uid}/dispositivos` */
  id: string;
  /** dono do dispositivo (o usuário logado que o registrou) */
  uid: string;
  token: string;
  plataforma: "android" | "web";
  prefs: PrefsPush;
  /** conta de dados em uso (ausente = a própria) */
  raizUid?: string;
  atualizadoEmMs?: number;
}

/** Mensagem lógica (antes de virar FCM). `extra` só leva strings; nunca valores monetários (exceto o resumo semanal, formatado só no app). */
export interface MensagemPush {
  tipo: TipoPush;
  titulo: string;
  corpo: string;
  extra: Record<string, string>;
}

/** Conversão tolerante de um documento do Firestore (dados desconhecidos viram valores seguros). */
export function lerDespesa(id: string, d: Record<string, unknown>): DespesaDoc | null {
  const valor = Number(d.valor);
  const data = Number(d.data);
  if (!Number.isFinite(valor) || !Number.isFinite(data)) return null;
  const cartao = Number(d.cartaoId);
  return {
    id,
    descricao: typeof d.descricao === "string" ? d.descricao : "",
    valor,
    data,
    categoria: typeof d.categoria === "string" ? d.categoria : "",
    tipo: d.tipo === "CREDITO" ? "CREDITO" : "DEBITO",
    cartaoId: Number.isFinite(cartao) && cartao !== 0 ? cartao : null,
    pago: d.pago === true,
    natureza: typeof d.natureza === "string" ? d.natureza : "NORMAL",
  };
}

export function lerOrcamento(d: Record<string, unknown>): OrcamentoDoc | null {
  const limite = Number(d.valorLimite);
  if (typeof d.categoria !== "string" || !d.categoria.trim() || !Number.isFinite(limite)) return null;
  return { categoria: d.categoria, valorLimite: limite };
}
