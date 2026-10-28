import {
  BarChart3,
  CalendarDays,
  Clock,
  CreditCard,
  DatabaseBackup,
  FileText,
  FolderKanban,
  GitCompareArrows,
  Landmark,
  LayoutDashboard,
  ListChecks,
  PieChart,
  PiggyBank,
  Receipt,
  Repeat,
  Search,
  Settings,
  Sparkles,
  Tags,
  Target,
  Trash2,
  TrendingDown,
  TrendingUp,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react";

/** Cor de destaque de cada tela (usa os tokens --c1..--c8, que já têm versão clara e escura). */
export type Matiz = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8;

export interface Rota {
  href: string;
  rotulo: string;
  icone: LucideIcon;
  matiz: Matiz;
  grupo: string;
  /** Aparece na barra inferior do mobile. */
  principal?: boolean;
}

export const ROTAS: Rota[] = [
  { href: "/", rotulo: "Início", icone: LayoutDashboard, matiz: 1, grupo: "Visão geral", principal: true },
  { href: "/resumo/", rotulo: "Resumo", icone: Sparkles, matiz: 4, grupo: "Visão geral" },
  { href: "/calendario/", rotulo: "Calendário", icone: CalendarDays, matiz: 2, grupo: "Visão geral" },
  { href: "/lancamentos/", rotulo: "Lançamentos", icone: Receipt, matiz: 3, grupo: "Movimentação", principal: true },
  { href: "/contas/", rotulo: "Contas", icone: Landmark, matiz: 2, grupo: "Movimentação", principal: true },
  { href: "/cartoes/", rotulo: "Cartões", icone: CreditCard, matiz: 7, grupo: "Movimentação", principal: true },
  { href: "/pendencias/", rotulo: "Pendências", icone: Clock, matiz: 4, grupo: "Movimentação" },
  { href: "/recorrencias/", rotulo: "Recorrências", icone: Repeat, matiz: 6, grupo: "Movimentação" },
  { href: "/conciliacao/", rotulo: "Conciliação", icone: GitCompareArrows, matiz: 1, grupo: "Movimentação" },
  { href: "/orcamentos/", rotulo: "Orçamentos", icone: PieChart, matiz: 5, grupo: "Planejar" },
  { href: "/metas/", rotulo: "Metas", icone: PiggyBank, matiz: 6, grupo: "Planejar" },
  { href: "/planejamento/", rotulo: "Planejamento", icone: Target, matiz: 3, grupo: "Planejar" },
  { href: "/investimentos/", rotulo: "Investimentos", icone: TrendingUp, matiz: 1, grupo: "Planejar" },
  { href: "/dividas/", rotulo: "Dívidas", icone: TrendingDown, matiz: 5, grupo: "Planejar" },
  { href: "/projetos/", rotulo: "Projetos", icone: FolderKanban, matiz: 7, grupo: "Planejar" },
  { href: "/relatorios/", rotulo: "Relatórios", icone: BarChart3, matiz: 2, grupo: "Análises" },
  { href: "/imposto-de-renda/", rotulo: "Imposto de Renda", icone: FileText, matiz: 8, grupo: "Análises" },
  { href: "/categorias/", rotulo: "Categorias", icone: Tags, matiz: 4, grupo: "Organização" },
  { href: "/regras/", rotulo: "Regras", icone: ListChecks, matiz: 3, grupo: "Organização" },
  { href: "/pessoas/", rotulo: "Pessoas", icone: Users, matiz: 5, grupo: "Organização" },
  { href: "/membros/", rotulo: "Membros", icone: UserCog, matiz: 7, grupo: "Organização" },
  { href: "/lixeira/", rotulo: "Lixeira", icone: Trash2, matiz: 8, grupo: "Sistema" },
  { href: "/backups/", rotulo: "Backups", icone: DatabaseBackup, matiz: 2, grupo: "Sistema" },
  { href: "/configuracoes/", rotulo: "Configurações", icone: Settings, matiz: 8, grupo: "Sistema" },
];

const EXTRAS: Rota[] = [{ href: "/busca/", rotulo: "Busca", icone: Search, matiz: 2, grupo: "Visão geral" }];

/** Rota da tela atual (maior prefixo que casa). */
export function rotaDe(pathname: string): Rota | null {
  const p = pathname.endsWith("/") ? pathname : `${pathname}/`;
  if (p === "/") return ROTAS[0];
  return [...ROTAS, ...EXTRAS].filter((r) => r.href !== "/" && p.startsWith(r.href)).sort((a, b) => b.href.length - a.href.length)[0] ?? null;
}

/** Estilo do "azulejo" de ícone: fundo translúcido e ícone na cor do matiz. */
export function estiloMatiz(m: Matiz): { background: string; color: string } {
  return { background: `color-mix(in srgb, var(--c${m}) 16%, transparent)`, color: `var(--c${m})` };
}
