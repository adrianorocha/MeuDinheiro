"use client";

import { Check, Circle, Copy, FastForward, Pencil, Repeat, Trash2, Unlink } from "lucide-react";
import { useState } from "react";
import { Input, Select } from "@/components/ui/Field";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import type { UnidadeRepeticao } from "@/lib/finance/operations";
import { Money } from "@/components/ui/Money";
import { Badge } from "@/components/ui/Misc";
import { IconButton } from "@/components/ui/Button";
import { PicBadge } from "@/components/ui/PicIcon";
import { cartaoIdDe, estaAtrasada } from "@/lib/finance/calc";
import type { Despesa } from "@/lib/finance/types";
import { formatData } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";
import { AntecipacaoModal } from "./AntecipacaoModal";
import { LancamentoModal } from "./LancamentoModal";

const ROTULO_NATUREZA: Partial<Record<Despesa["natureza"], string>> = {
  SALDO_INICIAL: "Saldo inicial",
  TRANSFERENCIA: "Transferência",
  APORTE_META: "Aporte em meta",
  RESGATE_META: "Resgate de meta",
  PAGAMENTO_FATURA: "Pagamento de fatura",
  AJUSTE: "Ajuste",
};

const EDITAVEL = new Set<Despesa["natureza"]>(["NORMAL", "SALDO_INICIAL", "AJUSTE"]);
const ALTERNAVEL = new Set<Despesa["natureza"]>(["NORMAL", "AJUSTE"]);

interface Props {
  itens: readonly Despesa[];
  /** Esconde o nome da conta/cartão (útil quando o contexto já o informa). */
  semOrigem?: boolean;
}

export function ListaLancamentos({ itens, semOrigem }: Props) {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const [editando, setEditando] = useState<Despesa | null>(null);
  const [excluindo, setExcluindo] = useState<Despesa | null>(null);
  const [repetindo, setRepetindo] = useState<Despesa | null>(null);
  const [antecipando, setAntecipando] = useState<Despesa | null>(null);
  const [repN, setRepN] = useState("3");
  const [repIntervalo, setRepIntervalo] = useState("1");
  const [repUnidade, setRepUnidade] = useState<UnidadeRepeticao>("MESES");
  const [repErro, setRepErro] = useState<string | null>(null);

  function desconciliar(d: Despesa) {
    const r = acoes.desfazerConciliacao(d.id);
    avisar(r.ok ? "sucesso" : "erro", r.ok ? "Conciliação desfeita (valor e data já mesclados não são revertidos)." : r.erro);
  }

  function duplicar(d: Despesa) {
    const r = acoes.duplicarLancamento(d.id);
    avisar(r.ok ? "sucesso" : "erro", r.ok ? "Lançamento duplicado como pendente, com a data de hoje." : r.erro);
  }

  function repetir(e: React.FormEvent) {
    e.preventDefault();
    if (!repetindo) return;
    const r = acoes.repetirLancamento(repetindo.id, { n: Number(repN), intervalo: Number(repIntervalo), unidade: repUnidade });
    if (!r.ok) return setRepErro(r.erro);
    avisar("sucesso", `${r.criados.length} lançamento(s) criado(s).`);
    setRepetindo(null);
  }

  const nomeOrigem = (d: Despesa): string => {
    const cid = cartaoIdDe(d);
    if (cid !== null) {
      const c = ds.cartoes.find((x) => x.id === cid);
      return c ? `Cartão ${c.nome}` : "Cartão";
    }
    const conta = ds.contas.find((c) => c.conta === d.conta);
    return conta ? `${conta.banco} · ${conta.conta}` : d.conta;
  };

  const picDe = (d: Despesa): string =>
    ds.categorias.find((c) => c.nome.trim().toLowerCase() === d.categoria.trim().toLowerCase())?.pic || d.pic;

  function alternar(d: Despesa) {
    const r = acoes.alternarPago(d.id);
    if (!r.ok) avisar("erro", r.erro);
  }

  /** Natureza bloqueada: a regra pura devolve a mensagem; senão pede confirmação. */
  function pedirExclusao(d: Despesa) {
    if (d.natureza === "PAGAMENTO_FATURA" || d.natureza === "APORTE_META" || d.natureza === "RESGATE_META") {
      const r = acoes.removerLancamento(d.id);
      if (!r.ok) avisar("erro", r.erro);
      return;
    }
    setExcluindo(d);
  }

  function excluir(grupo: boolean) {
    if (!excluindo) return;
    const r = acoes.removerLancamento(excluindo.id, { grupoParcelas: grupo });
    if (r.ok) avisar("sucesso", r.removidos > 1 ? `${r.removidos} lançamentos excluídos.` : "Lançamento excluído.");
    else avisar("erro", r.erro);
    setExcluindo(null);
  }

  const ehParcela = (d: Despesa) => Boolean(d.grupoId?.startsWith("parc:"));

  return (
    <>
      <ul className="divide-y divide-line">
        {itens.map((d) => {
          const atrasada = estaAtrasada(d, agora);
          const entrada = d.tipo === "CREDITO";
          const rotuloNat = ROTULO_NATUREZA[d.natureza];
          return (
            <li key={d.id} className="flex items-center gap-3 py-3">
              <PicBadge pic={picDe(d)} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{d.descricao}</p>
                <p className="truncate text-xs text-muted">
                  {formatData(d.data)} · {d.categoria}
                  {!semOrigem && ` · ${nomeOrigem(d)}`}
                  {d.autor && ` · por ${d.autor}`}
                  {d.moedaOriginal !== "BRL" && ` · ${d.moedaOriginal} ${d.valorOriginal.toFixed(2).replace(".", ",")} @ ${String(d.cotacaoNaData).replace(".", ",")}`}
                </p>
                <div className="mt-1 flex flex-wrap gap-1">
                  {rotuloNat && <Badge tom="primary">{rotuloNat}</Badge>}
                  {d.conciliadoEm != null && <Badge tom="pos">✓ conciliado</Badge>}
                  {cartaoIdDe(d) !== null && <Badge>Fatura</Badge>}
                  {!d.pago && <Badge tom={atrasada ? "neg" : "warn"}>{atrasada ? "Atrasada" : "Pendente"}</Badge>}
                </div>
              </div>
              <Money valor={entrada ? d.valor : -d.valor} tom="auto" className="text-sm font-semibold" />
              <div className="flex shrink-0 items-center">
                {ALTERNAVEL.has(d.natureza) && (
                  <IconButton rotulo={d.pago ? "Marcar como pendente" : "Marcar como pago"} aria-pressed={d.pago} onClick={() => alternar(d)}>
                    {d.pago ? <Check size={18} className="text-pos" aria-hidden /> : <Circle size={18} aria-hidden />}
                  </IconButton>
                )}
                {d.natureza === "NORMAL" && (
                  <>
                    {d.conciliadoEm != null && (
                      <IconButton rotulo={`Desfazer conciliação de ${d.descricao}`} onClick={() => desconciliar(d)}>
                        <Unlink size={16} aria-hidden />
                      </IconButton>
                    )}
                    {!d.pago && d.tipo === "DEBITO" && cartaoIdDe(d) === null && (
                      <IconButton rotulo={`Antecipar pagamento de ${d.descricao}`} onClick={() => setAntecipando(d)}>
                        <FastForward size={16} aria-hidden />
                      </IconButton>
                    )}
                    <IconButton rotulo={`Duplicar ${d.descricao}`} onClick={() => duplicar(d)}>
                      <Copy size={16} aria-hidden />
                    </IconButton>
                    <IconButton rotulo={`Repetir ${d.descricao}`} onClick={() => { setRepErro(null); setRepetindo(d); }}>
                      <Repeat size={16} aria-hidden />
                    </IconButton>
                  </>
                )}
                {EDITAVEL.has(d.natureza) && (
                  <IconButton rotulo={`Editar ${d.descricao}`} onClick={() => setEditando(d)}>
                    <Pencil size={16} aria-hidden />
                  </IconButton>
                )}
                <IconButton rotulo={`Excluir ${d.descricao}`} onClick={() => pedirExclusao(d)}>
                  <Trash2 size={16} aria-hidden />
                </IconButton>
              </div>
            </li>
          );
        })}
      </ul>
      <Modal aberto={repetindo !== null} onFechar={() => setRepetindo(null)} titulo={`Repetir: ${repetindo?.descricao ?? ""}`}>
        <form onSubmit={repetir} className="flex flex-col gap-4" noValidate>
          <div className="grid grid-cols-3 gap-3">
            <Input rotulo="Repetições" type="number" min={1} max={120} value={repN} onChange={(e) => setRepN(e.target.value)} />
            <Input rotulo="A cada" type="number" min={1} value={repIntervalo} onChange={(e) => setRepIntervalo(e.target.value)} />
            <Select rotulo="Unidade" value={repUnidade} onChange={(e) => setRepUnidade(e.target.value as UnidadeRepeticao)}>
              <option value="DIAS">dia(s)</option>
              <option value="SEMANAS">semana(s)</option>
              <option value="MESES">mês(es)</option>
            </Select>
          </div>
          <p className="text-xs text-muted">Cria cópias pendentes a partir da data do lançamento. Compras de cartão consomem limite.</p>
          {repErro && <p role="alert" className="text-sm text-neg">{repErro}</p>}
          <RodapeForm onCancelar={() => setRepetindo(null)} rotuloEnviar="Repetir" />
        </form>
      </Modal>
      <AntecipacaoModal aberto={antecipando !== null} onFechar={() => setAntecipando(null)} base={antecipando} />
      <LancamentoModal aberto={editando !== null} onFechar={() => setEditando(null)} editar={editando} />
      <Confirmar
        aberto={excluindo !== null}
        titulo="Excluir lançamento"
        perigo
        rotuloConfirmar="Excluir"
        mensagem={
          excluindo?.natureza === "TRANSFERENCIA"
            ? "Esta transferência será excluída junto com o lançamento par na outra conta."
            : `Excluir "${excluindo?.descricao}"? Os saldos e limites serão recalculados.`
        }
        onConfirmar={() => excluir(false)}
        onCancelar={() => setExcluindo(null)}
      >
        {excluindo && ehParcela(excluindo) && (
          <button type="button" onClick={() => excluir(true)} className="mt-3 text-sm font-medium text-neg underline">
            Excluir todas as parcelas deste parcelamento
          </button>
        )}
      </Confirmar>
    </>
  );
}
