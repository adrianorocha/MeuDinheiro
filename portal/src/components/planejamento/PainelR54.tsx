"use client";

import { useMemo, useState } from "react";
import { Input, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, ProgressBar } from "@/components/ui/Misc";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { MASCARA, formatBRL } from "@/lib/format";
import { decimosParaTexto } from "@/lib/finance/centavos";
import { saldoTotalContas } from "@/lib/finance/calc";
import { mesAnoDe } from "@/lib/finance/dates";
import { toCents } from "@/lib/finance/money";
import {
  CONFIG_PADRAO,
  chaveCategoria,
  metasDeReservaCentavos,
  painel503020,
  reservaEssenciais,
  type GrupoPainel,
  type GrupoResultado,
} from "@/lib/finance/painel503020";
import { useAgora, useDataset } from "@/lib/hooks";
import { usePainelConfig } from "@/lib/painelConfig";
import { useSession } from "@/lib/session";

const NOMES: Record<GrupoPainel, string> = { NECESSIDADES: "Necessidades", DESEJOS: "Desejos", POUPANCA: "Poupança e dívidas" };

function useMoeda() {
  const privado = useSession((s) => s.privado);
  return (cents: number) => (privado ? MASCARA : formatBRL(cents / 100));
}

function LinhaGrupo({ g }: { g: GrupoResultado }) {
  const moeda = useMoeda();
  const nome = NOMES[g.grupo];
  const pct = g.percentualDecimos;
  const tom = g.status === "OK" ? "pos" : g.status === "SEM_RECEITA" ? "neutro" : "warn";
  const rotuloStatus = g.status === "OK" ? "Dentro da meta" : g.status === "ACIMA" ? "Acima da meta" : g.status === "ABAIXO" ? "Abaixo da meta" : "Sem receita";
  const alvo = `${g.grupo === "POUPANCA" ? "pelo menos" : "até"} ${g.metaPercentual}%`;
  return (
    <li>
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-medium">
          {nome} <span className="text-xs text-muted">({alvo})</span>
        </span>
        <span className="flex items-center gap-2">
          <span className="tabular">{moeda(g.totalCentavos)}</span>
          {pct !== null && <span className="tabular text-muted">{decimosParaTexto(pct)}%</span>}
          <Badge tom={tom}>{rotuloStatus}</Badge>
        </span>
      </div>
      <ProgressBar
        razao={pct === null ? 0 : pct / 1000 / (g.metaPercentual > 0 ? g.metaPercentual / 100 : 1)}
        tom={g.status === "OK" ? "pos" : g.status === "SEM_RECEITA" ? "primary" : "warn"}
        rotulo={`${nome}: ${pct === null ? "sem receita" : `${decimosParaTexto(pct)}% da receita`}, ${alvo}`}
      />
      {g.categorias.length > 0 && (
        <p className="mt-1 text-xs text-muted">
          {g.categorias
            .slice(0, 4)
            .map((c) => `${c.nome} ${moeda(c.centavos)}`)
            .join(" · ")}
        </p>
      )}
    </li>
  );
}

/** R54 - painel 50-30-20 por categoria + reserva de emergência (despesas essenciais). */
export function PainelR54() {
  const ds = useDataset();
  const agora = useAgora();
  const moeda = useMoeda();
  const privado = useSession((s) => s.privado);
  const [ref, setRef] = useState(() => mesAnoDe(agora));
  const [config, salvar] = usePainelConfig();
  const [editando, setEditando] = useState(false);
  const p = useMemo(() => painel503020(ds.despesas, ref.mes, ref.ano, config), [ds.despesas, ref, config]);
  const reserva = useMemo(
    () => reservaEssenciais(ds.despesas, toCents(saldoTotalContas(ds)), metasDeReservaCentavos(ds.metas), agora, config),
    [ds, agora, config],
  );

  const setMeta = (campo: "necessidades" | "desejos" | "poupanca", v: string) => {
    const n = Number(v);
    if (Number.isInteger(n) && n >= 0 && n <= 100) salvar({ ...config, metas: { ...config.metas, [campo]: n } });
  };
  const classificar = (nome: string, grupo: string) => {
    const overrides = { ...config.overrides };
    if (grupo === "") delete overrides[chaveCategoria(nome)];
    else overrides[chaveCategoria(nome)] = grupo as GrupoPainel;
    salvar({ ...config, overrides });
  };

  const tomRes = reserva.status === "OK" ? "pos" : reserva.status === "ATENCAO" ? "warn" : reserva.status === "CRITICO" ? "neg" : "neutro";
  const rotRes = { OK: "Reserva completa", ATENCAO: "Atenção", CRITICO: "Crítico", SEM_DADOS: "Sem dados" }[reserva.status];

  return (
    <Card aria-labelledby="t-r54" className="lg:col-span-2">
      <CardTitulo
        id="t-r54"
        acao={
          <div className="flex items-center gap-2">
            <MonthPicker mes={ref.mes} ano={ref.ano} onChange={setRef} />
            <button type="button" className="text-xs font-medium text-primary underline-offset-2 hover:underline" aria-expanded={editando} onClick={() => setEditando((v) => !v)}>
              {editando ? "Fechar ajustes" : "Ajustar metas"}
            </button>
          </div>
        }
      >
        Painel 50-30-20 e reserva de emergência
      </CardTitulo>

      {p.receitaCentavos <= 0 ? (
        <p className="mb-3 text-sm text-muted">Sem receita realizada neste mês: os percentuais não podem ser calculados.</p>
      ) : (
        <p className="mb-3 text-sm text-muted">
          Sobre a receita realizada de <span className="tabular font-medium text-fg">{moeda(p.receitaCentavos)}</span>. Fatura de cartão paga não é contada de novo: as compras já entram nas categorias.
        </p>
      )}

      <ul className="space-y-4">
        <LinhaGrupo g={p.necessidades} />
        <LinhaGrupo g={p.desejos} />
        <LinhaGrupo g={p.poupanca} />
      </ul>

      {p.dicas.length > 0 && (
        <ul className="mt-4 space-y-1 rounded-xl bg-surface-2 px-3 py-2 text-sm" aria-label="Dicas">
          {p.dicas.map((t) => (
            <li key={t}>{privado ? t.replace(/R\$ [\d.]+,\d{2}/g, MASCARA) : t}</li>
          ))}
        </ul>
      )}

      {p.naoClassificadas.length > 0 && (
        <div className="mt-4">
          <h3 className="mb-2 text-sm font-semibold">Categorias sem classificação</h3>
          <ul className="grid gap-3 sm:grid-cols-2">
            {p.naoClassificadas.map((c) => (
              <li key={c.nome} className="flex items-end gap-2">
                <Select rotulo={`${c.nome} (${moeda(c.centavos)})`} wrapperClassName="flex-1" value="" onChange={(e) => classificar(c.nome, e.target.value)}>
                  <option value="">Escolher grupo…</option>
                  <option value="NECESSIDADES">Necessidades</option>
                  <option value="DESEJOS">Desejos</option>
                  <option value="POUPANCA">Poupança e dívidas</option>
                </Select>
              </li>
            ))}
          </ul>
        </div>
      )}

      {editando && (
        <div className="mt-4 space-y-4 rounded-xl border border-line p-3">
          <div className="grid gap-3 sm:grid-cols-4">
            <Input rotulo="Necessidades (% máx.)" type="number" min={0} max={100} value={config.metas.necessidades} onChange={(e) => setMeta("necessidades", e.target.value)} />
            <Input rotulo="Desejos (% máx.)" type="number" min={0} max={100} value={config.metas.desejos} onChange={(e) => setMeta("desejos", e.target.value)} />
            <Input rotulo="Poupança (% mín.)" type="number" min={0} max={100} value={config.metas.poupanca} onChange={(e) => setMeta("poupanca", e.target.value)} />
            <Input
              rotulo="Meta da reserva (meses)"
              type="number"
              min={1}
              max={36}
              value={config.mesesReserva}
              onChange={(e) => {
                const n = Number(e.target.value);
                if (Number.isInteger(n) && n >= 1 && n <= 36) salvar({ ...config, mesesReserva: n });
              }}
            />
          </div>
          <div>
            <h3 className="mb-2 text-sm font-semibold">Classificação das categorias do mês</h3>
            <ul className="grid gap-3 sm:grid-cols-2">
              {[...p.necessidades.categorias, ...p.desejos.categorias, ...p.poupanca.categorias.filter((c) => !c.nome.startsWith("Metas (")), ...p.naoClassificadas].map((c) => {
                const k = chaveCategoria(c.nome);
                return (
                  <li key={k}>
                    <Select
                      rotulo={`${c.nome} (${moeda(c.centavos)})`}
                      value={config.overrides[k] ?? ""}
                      onChange={(e) => classificar(c.nome, e.target.value)}
                    >
                      <option value="">Automático (pelo nome)</option>
                      <option value="NECESSIDADES">Necessidades</option>
                      <option value="DESEJOS">Desejos</option>
                      <option value="POUPANCA">Poupança e dívidas</option>
                    </Select>
                  </li>
                );
              })}
            </ul>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" className="text-sm font-medium text-primary hover:underline" onClick={() => salvar(CONFIG_PADRAO)}>
              Restaurar padrão (50-30-20, 6 meses, classificação automática)
            </button>
          </div>
          <p className="text-xs text-muted">Estes ajustes ficam apenas neste navegador: não sincronizam com o app nem com outros aparelhos nesta versão.</p>
        </div>
      )}

      <div className="mt-6 border-t border-line pt-4">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">Reserva de emergência (despesas essenciais)</h3>
          <Badge tom={tomRes}>{rotRes}</Badge>
        </div>
        {reserva.mediaEssenciaisCentavos === null || reserva.mesesCobertos === null || reserva.metaCentavos === null || reserva.faltanteCentavos === null ? (
          <p className="text-sm text-muted">Registre despesas essenciais (moradia, mercado, saúde, transporte…) nos 3 meses anteriores para calcular a cobertura.</p>
        ) : (
          <>
            <p className="text-sm">
              Você cobre <strong className="tabular">{reserva.mesesCobertos.toFixed(1).replace(".", ",")} meses</strong> de despesas essenciais (meta: {reserva.mesesMeta}).
            </p>
            <div className="mt-2">
              <ProgressBar razao={reserva.mesesCobertos / reserva.mesesMeta} tom={tomRes === "neutro" ? "primary" : tomRes} rotulo="Cobertura da reserva em meses" />
            </div>
            <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
              <div>
                <dt className="text-xs text-muted">Essenciais por mês (média de 3)</dt>
                <dd className="tabular font-semibold">{moeda(reserva.mediaEssenciaisCentavos)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Contas + metas de reserva</dt>
                <dd className="tabular font-semibold">{moeda(reserva.liquidezCentavos)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Meta ({reserva.mesesMeta} meses)</dt>
                <dd className="tabular font-semibold">{moeda(reserva.metaCentavos)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Falta</dt>
                <dd className="tabular font-semibold">{moeda(reserva.faltanteCentavos)}</dd>
              </div>
            </dl>
            <p className="mt-2 text-xs text-muted">Conta o saldo das contas e as metas cujo nome tem &quot;reserva&quot; ou &quot;emergência&quot;. Investimentos não entram aqui.</p>
          </>
        )}
      </div>
    </Card>
  );
}
