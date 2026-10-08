import type { Categoria } from "./finance/types";

export interface CategoriaPadrao {
  nome: string;
  pic: string;
}

/** Categorias padrão do app Android (mesmos nomes e `pic`). */
export const CATEGORIAS_PADRAO: readonly CategoriaPadrao[] = [
  { nome: "Combustível", pic: "fuel" },
  { nome: "Alimentação", pic: "restaurant" },
  { nome: "Transporte", pic: "transport" },
  { nome: "Compras", pic: "shopping" },
  { nome: "Cinema", pic: "cinema" },
  { nome: "Saúde", pic: "health" },
  { nome: "Educação", pic: "education" },
  { nome: "Salário", pic: "salary" },
  { nome: "Oficina", pic: "repair_car" },
  { nome: "Supermercado", pic: "supermarket" },
  { nome: "Academia", pic: "gym" },
  { nome: "Jogos", pic: "games" },
  { nome: "Bebidas", pic: "drink" },
  { nome: "Lanche", pic: "lunch" },
  { nome: "Reserva", pic: "reserva" },
];

export const BANCOS: readonly { nome: string; pic: string }[] = [
  { nome: "Banco do Brasil", pic: "banco_do_brasil" },
  { nome: "Bradesco", pic: "bradesco" },
  { nome: "Santander", pic: "santander" },
  { nome: "Caixa Econômica", pic: "caixa_economica" },
  { nome: "Itaú", pic: "itau" },
  { nome: "Nubank", pic: "nubank" },
  { nome: "C6", pic: "c6" },
  { nome: "MercadoPago", pic: "mercado_pago" },
  { nome: "Sicoob", pic: "sicoob" },
  { nome: "Banco Inter", pic: "banco_inter" },
  { nome: "Outro", pic: "bank" },
];

export const TIPOS_INVESTIMENTO = ["Renda Fixa", "Ações", "FIIs", "Cripto", "Fundos", "Outros"] as const;

export const TIPOS_CARTAO = ["CRÉDITO", "DÉBITO", "MÚLTIPLO"] as const;

export const MOEDAS = ["BRL", "USD", "EUR", "GBP", "ARS"] as const;

/** Categorias padrão ainda ausentes (comparação sem diferenciar maiúsculas/espaços). */
export function categoriasFaltantes(existentes: readonly Categoria[]): CategoriaPadrao[] {
  const nomes = new Set(existentes.map((c) => c.nome.trim().toLowerCase()));
  return CATEGORIAS_PADRAO.filter((c) => !nomes.has(c.nome.toLowerCase()));
}

/**
 * Categorias para escolher nos formulários: as padrão do app (que no Android são fixas no código e NÃO
 * são sincronizadas) somadas às personalizadas da nuvem/backup. Sem duplicar nomes.
 */
export function categoriasDisponiveis(personalizadas: readonly Categoria[]): CategoriaPadrao[] {
  const mapa = new Map<string, CategoriaPadrao>();
  for (const c of CATEGORIAS_PADRAO) mapa.set(c.nome.trim().toLowerCase(), c);
  for (const c of personalizadas) mapa.set(c.nome.trim().toLowerCase(), { nome: c.nome, pic: c.pic });
  return [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
}

/** `pic` (ícone) da categoria pelo nome, incluindo as padrão do app. */
export function picDaCategoria(nome: string, personalizadas: readonly Categoria[]): string | undefined {
  const alvo = nome.trim().toLowerCase();
  return categoriasDisponiveis(personalizadas).find((c) => c.nome.trim().toLowerCase() === alvo)?.pic;
}
