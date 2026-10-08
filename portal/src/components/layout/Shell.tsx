"use client";

import {
  BarChart3,
  Clock,
  CreditCard,
  Eye,
  EyeOff,
  Landmark,
  LayoutDashboard,
  LogOut,
  Lock,
  Menu,
  Moon,
  PieChart,
  PiggyBank,
  Receipt,
  Search,
  Repeat,
  Settings,
  Sun,
  Target,
  GitCompareArrows,
  Trash2,
  Tags,
  TrendingUp,
  Wallet,
  X,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { LancamentoRapido } from "@/components/lancamentos/LancamentoRapido";
import { IconButton } from "@/components/ui/Button";
import { useAlertas } from "@/lib/alertas";
import { alertasOrcamento } from "@/lib/finance/analises";
import { useAgora, useDataset } from "@/lib/hooks";
import { sair } from "@/lib/firebase/auth";
import { useSession } from "@/lib/session";
import { useStore } from "@/lib/store/store";

interface ItemNav {
  href: string;
  rotulo: string;
  icone: LucideIcon;
  /** Aparece na barra inferior do mobile. */
  principal?: boolean;
  badge?: number;
}

const ITENS: ItemNav[] = [
  { href: "/", rotulo: "Início", icone: LayoutDashboard, principal: true },
  { href: "/lancamentos/", rotulo: "Lançamentos", icone: Receipt, principal: true },
  { href: "/contas/", rotulo: "Contas", icone: Landmark, principal: true },
  { href: "/cartoes/", rotulo: "Cartões", icone: CreditCard, principal: true },
  { href: "/orcamentos/", rotulo: "Orçamentos", icone: PieChart },
  { href: "/metas/", rotulo: "Metas", icone: PiggyBank },
  { href: "/investimentos/", rotulo: "Investimentos", icone: TrendingUp },
  { href: "/recorrencias/", rotulo: "Recorrências", icone: Repeat },
  { href: "/pendencias/", rotulo: "Pendências", icone: Clock },
  { href: "/categorias/", rotulo: "Categorias", icone: Tags },
  { href: "/relatorios/", rotulo: "Relatórios", icone: BarChart3 },
  { href: "/planejamento/", rotulo: "Planejamento", icone: Target },
  { href: "/conciliacao/", rotulo: "Conciliação", icone: GitCompareArrows },
  { href: "/lixeira/", rotulo: "Lixeira", icone: Trash2 },
  { href: "/configuracoes/", rotulo: "Configurações", icone: Settings },
];

function ativo(pathname: string, href: string): boolean {
  const p = pathname.endsWith("/") ? pathname : `${pathname}/`;
  return href === "/" ? p === "/" : p.startsWith(href);
}

function LinkNav({ item, pathname, compacto, onClick }: { item: ItemNav; pathname: string; compacto?: boolean; onClick?: () => void }) {
  const on = ativo(pathname, item.href);
  const Icone = item.icone;
  return (
    <Link
      href={item.href}
      onClick={onClick}
      aria-current={on ? "page" : undefined}
      className={
        compacto
          ? `flex min-w-0 flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${on ? "text-primary" : "text-muted"}`
          : `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${on ? "bg-primary-soft text-primary" : "text-muted hover:bg-surface-2 hover:text-fg"}`
      }
    >
      <Icone size={compacto ? 20 : 18} aria-hidden />
      <span className="truncate">{item.rotulo}</span>
      {item.badge ? (
        <span className="ml-auto min-w-5 rounded-full bg-neg px-1.5 text-center text-[11px] font-semibold text-white" aria-label={`${item.badge} alerta(s)`}>
          {item.badge}
        </span>
      ) : null}
    </Link>
  );
}

function Marca() {
  return (
    <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight">
      <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-fg">
        <Wallet size={18} aria-hidden />
      </span>
      Meu Dinheiro
    </Link>
  );
}

function BuscaGlobal() {
  const router = useRouter();
  const [q, setQ] = useState("");
  return (
    <form
      role="search"
      className="mx-2 flex min-w-0 flex-1 justify-end md:justify-start"
      onSubmit={(e) => {
        e.preventDefault();
        if (q.trim()) router.push(`/busca/?q=${encodeURIComponent(q.trim())}`);
      }}
    >
      <label className="relative block w-full max-w-sm">
        <span className="sr-only">Busca global</span>
        <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" aria-hidden />
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Buscar"
          className="h-9 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm placeholder:text-muted/70"
        />
      </label>
    </form>
  );
}

function Acoes() {
  const privado = useSession((s) => s.privado);
  const alternarPrivado = useSession((s) => s.alternarPrivado);
  const tema = useSession((s) => s.tema);
  const definirTema = useSession((s) => s.definirTema);
  const modo = useSession((s) => s.modo);
  const user = useSession((s) => s.user);
  const erroSync = useStore((s) => s.erroSync);
  const router = useRouter();

  const escuroAgora = () =>
    tema === "dark" || (tema === "system" && typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches);

  return (
    <div className="flex items-center gap-1">
      {erroSync && (
        <span role="alert" className="mr-1 max-w-48 truncate rounded-full bg-neg-soft px-2 py-1 text-xs text-neg" title={erroSync}>
          {erroSync}
        </span>
      )}
      <IconButton rotulo={privado ? "Mostrar valores" : "Ocultar valores"} aria-pressed={privado} onClick={alternarPrivado}>
        {privado ? <EyeOff size={18} aria-hidden /> : <Eye size={18} aria-hidden />}
      </IconButton>
      <IconButton rotulo={escuroAgora() ? "Usar tema claro" : "Usar tema escuro"} onClick={() => definirTema(escuroAgora() ? "light" : "dark")}>
        {escuroAgora() ? <Sun size={18} aria-hidden /> : <Moon size={18} aria-hidden />}
      </IconButton>
      <IconButton
        rotulo={modo === "firebase" ? `Sair${user?.email ? ` (${user.email})` : ""}` : "Trocar modo de armazenamento"}
        onClick={async () => {
          if (modo === "firebase") await sair();
          useSession.getState().definirModo(null);
          router.replace("/login/");
        }}
      >
        <LogOut size={18} aria-hidden />
      </IconButton>
    </div>
  );
}

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [maisAberto, setMaisAberto] = useState(false);
  const modo = useSession((s) => s.modo);
  const pinAtivo = useSession((s) => s.pinAtivo);
  const ds = useDataset();
  const agora = useAgora();
  const avisados = useAlertas((s) => s.avisados);
  useEffect(() => useAlertas.getState().carregar(), []);
  const nAlertas = alertasOrcamento(ds, agora, avisados).alertas.length;
  const itens = ITENS.map((i) => (i.href === "/orcamentos/" ? { ...i, badge: nAlertas } : i));
  const principais = itens.filter((i) => i.principal);
  const secundarios = itens.filter((i) => !i.principal);

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[15rem_1fr]">
      <aside className="sticky top-0 hidden h-dvh flex-col gap-4 border-r border-line bg-surface p-4 md:flex" aria-label="Navegação principal">
        <Marca />
        <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto" aria-label="Seções">
          {itens.map((i) => (
            <LinkNav key={i.href} item={i} pathname={pathname} />
          ))}
        </nav>
        <p className="px-2 text-xs text-muted">{modo === "firebase" ? "Modo nuvem (Firebase)" : "Modo local (neste navegador)"}</p>
      </aside>

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-line bg-bg/90 px-4 backdrop-blur md:px-8">
          <div className="md:hidden">
            <Marca />
          </div>
          <BuscaGlobal />
          {pinAtivo && (
            <IconButton rotulo="Bloquear agora" onClick={() => useSession.getState().bloquear()}>
              <Lock size={18} aria-hidden />
            </IconButton>
          )}
          <Acoes />
        </header>
        <main id="conteudo" className="mx-auto w-full max-w-6xl flex-1 px-4 pb-28 pt-5 md:px-8 md:pb-10">
          {children}
        </main>
      </div>

      <LancamentoRapido />
      <nav className="fixed inset-x-0 bottom-0 z-40 flex border-t border-line bg-surface md:hidden" aria-label="Navegação inferior">
        {principais.map((i) => (
          <LinkNav key={i.href} item={i} pathname={pathname} compacto />
        ))}
        <button
          type="button"
          aria-expanded={maisAberto}
          aria-controls="menu-mais"
          onClick={() => setMaisAberto((v) => !v)}
          className={`flex min-w-0 flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${maisAberto ? "text-primary" : "text-muted"}`}
        >
          {maisAberto ? <X size={20} aria-hidden /> : <Menu size={20} aria-hidden />}
          Mais
        </button>
      </nav>
      {maisAberto && (
        <div id="menu-mais" className="fixed inset-x-0 bottom-14 z-40 grid grid-cols-3 gap-1 border-t border-line bg-surface p-3 md:hidden">
          {secundarios.map((i) => (
            <Link
              key={i.href}
              href={i.href}
              onClick={() => setMaisAberto(false)}
              aria-current={ativo(pathname, i.href) ? "page" : undefined}
              className={`flex flex-col items-center gap-1 rounded-lg p-2 text-xs font-medium ${ativo(pathname, i.href) ? "bg-primary-soft text-primary" : "text-muted"}`}
            >
              <i.icone size={20} aria-hidden />
              {i.rotulo}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
