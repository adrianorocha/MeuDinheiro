"use client";

import { useMemo } from "react";
import { Button } from "@/components/ui/Button";
import { Input, Segmentado, Select } from "@/components/ui/Field";
import { Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { janelaPadrao } from "@/lib/conciliacao/matching";
import { diferencaSaldo } from "@/lib/conciliacao/saldo";
import type { ArquivoExtrato, Destino } from "@/lib/conciliacao/tipos";
import { toCents, fromCents } from "@/lib/finance/money";
import type { Dataset } from "@/lib/finance/types";
import { formatData } from "@/lib/format";

/** R36 - sugere conta/cartão pelo ACCTID do OFX. */
export function sugerirDestino(ds: Dataset, arquivo: ArquivoExtrato): Destino | null {
  const acct = (arquivo.acctId ?? "").replace(/\D/g, "");
  if (acct) {
    if (!arquivo.ehCartao) {
      const exata = ds.contas.find((c) => c.conta === arquivo.acctId);
      if (exata) return { tipo: "CONTA", conta: exata.conta };
      const c = ds.contas.find((x) => x.conta.replace(/\D/g, "") === acct);
      if (c) return { tipo: "CONTA", conta: c.conta };
    } else {
      const k = ds.cartoes.find((x) => x.cartaoPrincipalId == null && x.finalCartao && acct.endsWith(x.finalCartao));
      if (k) return { tipo: "CARTAO", cartaoId: k.id };
    }
  }
  if (arquivo.ehCartao && ds.cartoes.length > 0) {
    const f = ds.cartoes.find((x) => x.cartaoPrincipalId == null);
    if (f) return { tipo: "CARTAO", cartaoId: f.id };
  }
  return ds.contas[0] ? { tipo: "CONTA", conta: ds.contas[0].conta } : null;
}

export function rotuloDestino(ds: Dataset, d: Destino): string {
  if (d.tipo === "CONTA") {
    const c = ds.contas.find((x) => x.conta === d.conta);
    return c ? `${c.banco} · ${c.conta}` : d.conta;
  }
  return `Cartão ${ds.cartoes.find((x) => x.id === d.cartaoId)?.nome ?? d.cartaoId}`;
}

interface Props {
  ds: Dataset;
  arquivo: ArquivoExtrato;
  destino: Destino | null;
  janela: number;
  tolerancia: number;
  onDestino: (d: Destino) => void;
  onJanela: (n: number) => void;
  onTolerancia: (n: number) => void;
  onVoltar: () => void;
  onContinuar: () => void;
}

/** Passo 2: destino, opções e resumo do arquivo. */
export function DestinoResumo({ ds, arquivo, destino, janela, tolerancia, onDestino, onJanela, onTolerancia, onVoltar, onContinuar }: Props) {
  const tipo = destino?.tipo ?? "CONTA";
  const resumo = useMemo(() => {
    let ent = 0;
    let sai = 0;
    for (const t of arquivo.transacoes) {
      const c = toCents(t.valor);
      if (c > 0) ent += c;
      else sai -= c;
    }
    return { entradas: fromCents(ent), saidas: fromCents(sai) };
  }, [arquivo.transacoes]);
  const saldo = useMemo(() => (destino && arquivo.saldoFinal ? diferencaSaldo(ds, destino, arquivo.saldoFinal) : null), [ds, destino, arquivo.saldoFinal]);

  return (
    <Card aria-labelledby="t-destino">
      <CardTitulo id="t-destino">2. Destino e opções</CardTitulo>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3">
          <Segmentado
            rotulo="Tipo de destino"
            valor={tipo}
            onChange={(v) => {
              if (v === "CONTA" && ds.contas[0]) onDestino({ tipo: "CONTA", conta: ds.contas[0].conta });
              if (v === "CARTAO" && ds.cartoes[0]) {
                onDestino({ tipo: "CARTAO", cartaoId: ds.cartoes[0].id });
                onJanela(janelaPadrao({ tipo: "CARTAO", cartaoId: ds.cartoes[0].id }));
              }
            }}
            opcoes={[
              { valor: "CONTA", rotulo: "Conta" },
              { valor: "CARTAO", rotulo: "Cartão" },
            ]}
          />
          {destino?.tipo === "CARTAO" ? (
            <Select rotulo="Cartão (físico ou virtual: compara com o grupo)" value={destino.cartaoId} onChange={(e) => onDestino({ tipo: "CARTAO", cartaoId: Number(e.target.value) })}>
              {ds.cartoes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome}
                  {c.cartaoPrincipalId != null ? " (virtual)" : ""}
                </option>
              ))}
            </Select>
          ) : (
            <Select rotulo="Conta" value={destino?.tipo === "CONTA" ? destino.conta : ""} onChange={(e) => onDestino({ tipo: "CONTA", conta: e.target.value })}>
              {ds.contas.map((c) => (
                <option key={c.id} value={c.conta}>
                  {c.banco} · {c.conta}
                </option>
              ))}
            </Select>
          )}
          {arquivo.acctId && <p className="text-xs text-muted">Conta no arquivo: {arquivo.acctId}</p>}
          <div className="grid grid-cols-2 gap-3">
            <Input rotulo="Janela de datas (± dias)" type="number" min={0} max={30} value={janela} onChange={(e) => onJanela(Math.max(0, Math.min(30, Number(e.target.value) || 0)))} />
            <Input rotulo="Tolerância de valor (%)" type="number" min={0} max={20} step={0.5} value={tolerancia} onChange={(e) => onTolerancia(Math.max(0, Math.min(20, Number(e.target.value) || 0)))} dica="Mínimo de R$ 1,00." />
          </div>
        </div>

        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div className="rounded-xl bg-surface-2 px-3 py-2">
            <dt className="text-xs text-muted">Período</dt>
            <dd className="font-semibold">{arquivo.periodo ? `${formatData(arquivo.periodo.inicio)} a ${formatData(arquivo.periodo.fim)}` : "—"}</dd>
          </div>
          <div className="rounded-xl bg-surface-2 px-3 py-2">
            <dt className="text-xs text-muted">Transações</dt>
            <dd className="font-semibold">{arquivo.transacoes.length}</dd>
          </div>
          <div className="rounded-xl bg-surface-2 px-3 py-2">
            <dt className="text-xs text-muted">Entradas</dt>
            <dd className="font-semibold">
              <Money valor={resumo.entradas} />
            </dd>
          </div>
          <div className="rounded-xl bg-surface-2 px-3 py-2">
            <dt className="text-xs text-muted">Saídas</dt>
            <dd className="font-semibold">
              <Money valor={resumo.saidas} />
            </dd>
          </div>
          {saldo && (
            <>
              <div className="rounded-xl bg-surface-2 px-3 py-2">
                <dt className="text-xs text-muted">Saldo final no arquivo</dt>
                <dd className="font-semibold">
                  <Money valor={saldo.saldoBanco} />
                </dd>
              </div>
              <div className={`rounded-xl px-3 py-2 ${saldo.diferenca === 0 ? "bg-pos-soft" : "bg-warn-soft"}`}>
                <dt className="text-xs text-muted">Saldo no app × diferença</dt>
                <dd className="font-semibold">
                  <Money valor={saldo.saldoApp} /> · <Money valor={saldo.diferenca} tom="auto" />
                </dd>
              </div>
            </>
          )}
        </dl>
      </div>
      <div className="mt-4 flex justify-end gap-2">
        <Button onClick={onVoltar}>Voltar</Button>
        <Button variante="primary" disabled={!destino} onClick={onContinuar}>
          Comparar
        </Button>
      </div>
    </Card>
  );
}
