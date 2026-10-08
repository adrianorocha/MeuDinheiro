"use client";

import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatBRL, MASCARA } from "@/lib/format";
import { useSession } from "@/lib/session";

export const CORES_SERIE = ["var(--c1)", "var(--c2)", "var(--c3)", "var(--c4)", "var(--c5)", "var(--c6)", "var(--c7)", "var(--c8)"];

function useFmt() {
  const privado = useSession((s) => s.privado);
  return (v: unknown) => (privado ? MASCARA : formatBRL(Number(v)));
}

const ESTILO_TOOLTIP = {
  background: "var(--surface)",
  border: "1px solid var(--line)",
  borderRadius: 10,
  color: "var(--fg)",
  fontSize: 12,
};

const EIXO = { fill: "var(--muted)", fontSize: 11 };

export interface Fatia {
  nome: string;
  valor: number;
}

export function GraficoPizza({ dados, rotulo }: { dados: Fatia[]; rotulo: string }) {
  const fmt = useFmt();
  const total = dados.reduce((a, d) => a + d.valor, 0);
  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row">
      <div className="h-48 w-48 shrink-0" role="img" aria-label={rotulo}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={dados} dataKey="valor" nameKey="nome" innerRadius={48} outerRadius={84} paddingAngle={2} stroke="var(--surface)" isAnimationActive={false}>
              {dados.map((d, i) => (
                <Cell key={d.nome} fill={CORES_SERIE[i % CORES_SERIE.length]} />
              ))}
            </Pie>
            <Tooltip formatter={(v) => fmt(v)} contentStyle={ESTILO_TOOLTIP} itemStyle={{ color: "var(--fg)" }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="w-full min-w-0 flex-1 space-y-1.5 text-sm">
        {dados.map((d, i) => (
          <li key={d.nome} className="flex items-center gap-2">
            <span className="size-2.5 shrink-0 rounded-full" style={{ background: CORES_SERIE[i % CORES_SERIE.length] }} aria-hidden />
            <span className="min-w-0 flex-1 truncate">{d.nome}</span>
            <span className="tabular text-muted">{fmt(d.valor)}</span>
            <span className="tabular w-10 text-right text-xs text-muted">{total > 0 ? `${Math.round((d.valor / total) * 100)}%` : ""}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function GraficoPatrimonio({ dados, rotulo }: { dados: { rotulo: string; valor: number }[]; rotulo: string }) {
  const fmt = useFmt();
  return (
    <div className="h-56 w-full" role="img" aria-label={rotulo}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={dados} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <defs>
            <linearGradient id="grad-patr" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--c1)" stopOpacity={0.35} />
              <stop offset="100%" stopColor="var(--c1)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="rotulo" tick={EIXO} tickLine={false} axisLine={{ stroke: "var(--line)" }} />
          <YAxis tick={EIXO} tickLine={false} axisLine={false} width={48} tickFormatter={(v) => `${Math.round(Number(v) / 1000)}k`} hide={false} />
          <Tooltip formatter={(v) => fmt(v)} contentStyle={ESTILO_TOOLTIP} labelStyle={{ color: "var(--muted)" }} />
          <Area type="monotone" dataKey="valor" name="Patrimônio" stroke="var(--c1)" strokeWidth={2} fill="url(#grad-patr)" isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function GraficoBarrasMensais({ dados, rotulo }: { dados: { rotulo: string; receitas: number; despesas: number }[]; rotulo: string }) {
  const fmt = useFmt();
  return (
    <div className="h-64 w-full" role="img" aria-label={rotulo}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="rotulo" tick={EIXO} tickLine={false} axisLine={{ stroke: "var(--line)" }} />
          <YAxis tick={EIXO} tickLine={false} axisLine={false} width={48} tickFormatter={(v) => `${Math.round(Number(v) / 1000)}k`} />
          <Tooltip formatter={(v) => fmt(v)} contentStyle={ESTILO_TOOLTIP} labelStyle={{ color: "var(--muted)" }} cursor={{ fill: "var(--surface-2)" }} />
          <Legend wrapperStyle={{ fontSize: 12, color: "var(--muted)" }} />
          <Bar dataKey="receitas" name="Receitas" fill="var(--c1)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
          <Bar dataKey="despesas" name="Despesas" fill="var(--c4)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function GraficoBarrasSimples({ dados, rotulo, nome }: { dados: { rotulo: string; valor: number }[]; rotulo: string; nome: string }) {
  const fmt = useFmt();
  return (
    <div className="h-56 w-full" role="img" aria-label={rotulo}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="rotulo" tick={EIXO} tickLine={false} axisLine={{ stroke: "var(--line)" }} />
          <YAxis tick={EIXO} tickLine={false} axisLine={false} width={48} tickFormatter={(v) => `${Math.round(Number(v) / 1000)}k`} />
          <Tooltip formatter={(v) => fmt(v)} contentStyle={ESTILO_TOOLTIP} labelStyle={{ color: "var(--muted)" }} cursor={{ fill: "var(--surface-2)" }} />
          <Bar dataKey="valor" name={nome} fill="var(--c2)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
