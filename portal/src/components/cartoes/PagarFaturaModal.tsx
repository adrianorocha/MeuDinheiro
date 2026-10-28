"use client";

import { CalendarPlus, CheckSquare, CircleCheck, ListChecks, Wallet } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { Badge, ErroBox } from "@/components/ui/Misc";
import { faturasEmAberto, liquidoItens, type MesAno } from "@/lib/finance/calc";
import { fromCents, toCents } from "@/lib/finance/money";
import type { Cartao, Conta, Despesa } from "@/lib/finance/types";
import { formatBRL, formatData, formatMesAno } from "@/lib/format";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

interface Props {
  aberto: boolean;
  onFechar: () => void;
  /** cartão principal do grupo */
  cartao: Cartao;
  cartoes: Cartao[];
  despesas: Despesa[];
  conta?: Conta;
  /** fatura exibida na página (itens dela vêm marcados ao abrir) */
  periodo: MesAno;
  /** abre já com "fatura já paga por fora" ligado (R46) */
  jaPagaInicial?: boolean;
}

function CaixaGrupo({ marcado, parcial, rotulo, onChange }: { marcado: boolean; parcial: boolean; rotulo: string; onChange: (v: boolean) => void }) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = parcial && !marcado;
  }, [parcial, marcado]);
  return <input ref={ref} type="checkbox" className="size-4 accent-[var(--primary)]" aria-label={rotulo} checked={marcado} onChange={(e) => onChange(e.target.checked)} />;
}

/** R44 - pagamento seletivo da fatura: todos os itens do mês, itens escolhidos ou antecipação de parcelas futuras. */
export function PagarFaturaModal(props: Props) {
  return (
    <Modal aberto={props.aberto} onFechar={props.onFechar} titulo={props.jaPagaInicial ? "Marcar fatura como paga" : "Pagar fatura"}>
      {props.aberto && <Conteudo {...props} />}
    </Modal>
  );
}

function Conteudo({ onFechar, cartao, cartoes, despesas, conta, periodo, jaPagaInicial = false }: Props) {
  const avisar = useStore((s) => s.avisar);
  const faturas = useMemo(() => faturasEmAberto(cartao, cartoes, despesas), [cartao, cartoes, despesas]);
  const chavePeriodo = `${periodo.ano}-${String(periodo.mes).padStart(2, "0")}`;
  const idsDaFatura = (chave: string) => (faturas.find((f) => f.chave === chave)?.itens ?? []).map((d) => d.id);
  const todos = useMemo(() => faturas.flatMap((f) => f.itens.map((d) => d.id)), [faturas]);
  const [sel, setSel] = useState<Set<number>>(() => new Set(idsDaFatura(chavePeriodo)));
  const [atalho, setAtalho] = useState<"mes" | "itens" | "futuras">("mes");
  const [confirmando, setConfirmando] = useState(false);
  const [jaPaga, setJaPaga] = useState(jaPagaInicial);
  const confirmarRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (confirmando) confirmarRef.current?.focus();
  }, [confirmando]);
  const [erro, setErro] = useState<string | null>(null);

  const itensSel = useMemo(() => faturas.flatMap((f) => f.itens).filter((d) => sel.has(d.id)), [faturas, sel]);
  const liquido = liquidoItens(itensSel);
  const saldoDepois = conta ? fromCents(toCents(conta.saldo) - toCents(Math.max(0, liquido))) : null;
  const negativo = saldoDepois !== null && saldoDepois < 0;
  const mesRotulo = (f: MesAno) => formatMesAno(f.mes, f.ano);
  const chaveNum = (f: { ano: number; mes: number }) => f.ano * 12 + f.mes;

  const definir = (ids: number[], a: typeof atalho) => {
    setSel(new Set(ids));
    setAtalho(a);
    setConfirmando(false);
    setErro(null);
  };
  const alternar = (ids: number[], marcar: boolean) => {
    setSel((s) => {
      const n = new Set(s);
      for (const id of ids) {
        if (marcar) n.add(id);
        else n.delete(id);
      }
      return n;
    });
    setAtalho("itens");
    setConfirmando(false);
  };

  function marcarPagos() {
    const r = acoes.marcarItensFaturaComoPagos({ cartaoId: cartao.id, itemIds: [...sel] });
    if (!r.ok) {
      setErro(r.erro);
      setConfirmando(false);
      return;
    }
    avisar("sucesso", `${r.itens.length} ${r.itens.length === 1 ? "item marcado como pago" : "itens marcados como pagos"} (${formatBRL(r.liquido)}). Saldo da conta inalterado.`);
    onFechar();
  }

  function pagar() {
    const r = acoes.pagarItensFatura({ cartaoId: cartao.id, itemIds: [...sel] });
    if (!r.ok) {
      setErro(r.erro);
      setConfirmando(false);
      return;
    }
    avisar("sucesso", `Pagamento realizado: ${formatBRL(r.pagamento.valor)}. Limite restaurado: ${formatBRL(r.pagamento.valor)}.`);
    onFechar();
  }

  if (faturas.length === 0) {
    return (
      <div>
        <p className="text-sm text-muted">Não há itens em aberto neste cartão.</p>
        <div className="mt-5 flex justify-end">
          <Button onClick={onFechar}>Fechar</Button>
        </div>
      </div>
    );
  }

  const atalhos = [
    { id: "mes" as const, rotulo: "Pagar toda a fatura do mês", icone: <Wallet size={16} aria-hidden />, acao: () => definir(idsDaFatura(chavePeriodo), "mes") },
    { id: "itens" as const, rotulo: "Selecionar itens", icone: <ListChecks size={16} aria-hidden />, acao: () => definir([], "itens") },
    {
      id: "futuras" as const,
      rotulo: "Antecipar parcelas futuras",
      icone: <CalendarPlus size={16} aria-hidden />,
      acao: () => definir(faturas.filter((f) => chaveNum(f) > chaveNum(periodo)).flatMap((f) => f.itens.map((d) => d.id)), "futuras"),
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <label className={`flex cursor-pointer items-start gap-2 rounded-xl border p-3 text-sm ${jaPaga ? "border-primary bg-primary-soft" : "border-line"}`}>
        <input
          type="checkbox"
          className="mt-0.5 size-4 accent-[var(--primary)]"
          checked={jaPaga}
          onChange={(e) => {
            setJaPaga(e.target.checked);
            setConfirmando(false);
            setErro(null);
          }}
        />
        <span>
          <span className="flex items-center gap-1 font-medium">
            <CircleCheck size={16} aria-hidden /> Esta fatura já foi paga (apenas marcar como paga, sem debitar da conta)
          </span>
          <span className="text-xs text-muted">Libera o limite do cartão sem registrar pagamento nem mexer no saldo.</span>
        </span>
      </label>

      <div role="group" aria-label="Atalhos de pagamento" className="grid gap-2 sm:grid-cols-3">
        {atalhos.map((a) => (
          <button
            key={a.id}
            type="button"
            aria-pressed={atalho === a.id}
            onClick={a.acao}
            className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-left text-sm font-medium transition-colors ${atalho === a.id ? "border-primary bg-primary-soft text-primary" : "border-line bg-surface hover:bg-surface-2"}`}
          >
            {a.icone}
            {a.rotulo}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        <Button tamanho="sm" icone={<CheckSquare size={14} aria-hidden />} onClick={() => definir(todos, "itens")}>
          Selecionar tudo
        </Button>
        <Button tamanho="sm" onClick={() => definir(idsDaFatura(chavePeriodo), "mes")}>
          Só esta fatura ({mesRotulo(periodo)})
        </Button>
        <Button tamanho="sm" variante="ghost" onClick={() => definir([], "itens")}>
          Limpar
        </Button>
      </div>

      <div className="max-h-[40vh] space-y-3 overflow-y-auto pr-1">
        {faturas.map((f) => {
          const ids = f.itens.map((d) => d.id);
          const nSel = ids.filter((id) => sel.has(id)).length;
          return (
            <fieldset key={f.chave} className="rounded-xl border border-line">
              <legend className="sr-only">Itens em aberto da fatura {mesRotulo(f)}</legend>
              <div className="flex items-center gap-2 rounded-t-xl bg-surface-2 px-3 py-2">
                <CaixaGrupo marcado={nSel === ids.length} parcial={nSel > 0} rotulo={`Selecionar todos os itens da fatura ${mesRotulo(f)}`} onChange={(v) => alternar(ids, v)} />
                <span className="flex-1 text-sm font-medium">
                  Fatura {mesRotulo(f)} <span className="font-normal text-muted">· vence {formatData(f.vencimento)}</span>
                </span>
                {chaveNum(f) === chaveNum(periodo) && <Badge tom="primary">Exibida</Badge>}
                <Money valor={f.subtotal} className="text-sm font-semibold" />
              </div>
              <ul className="divide-y divide-line">
                {f.itens.map((d) => (
                  <li key={d.id} className="flex items-center gap-2 px-3 py-1.5 text-sm">
                    <input
                      id={`pf-${d.id}`}
                      type="checkbox"
                      className="size-4 accent-[var(--primary)]"
                      checked={sel.has(d.id)}
                      onChange={(e) => alternar([d.id], e.target.checked)}
                    />
                    <label htmlFor={`pf-${d.id}`} className="flex min-w-0 flex-1 cursor-pointer items-center justify-between gap-2">
                      <span className="min-w-0 truncate">
                        {d.descricao} <span className="text-xs text-muted">· {formatData(d.data)}</span>
                      </span>
                      <Money valor={d.tipo === "CREDITO" ? d.valor : -d.valor} tom="auto" />
                    </label>
                  </li>
                ))}
              </ul>
            </fieldset>
          );
        })}
      </div>

      <div className="rounded-xl border border-line p-3" aria-live="polite" aria-atomic="true">
        <dl className="grid gap-1 text-sm">
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Itens selecionados</dt>
            <dd>{itensSel.length}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Total selecionado</dt>
            <dd className="font-semibold">
              <Money valor={liquido} />
            </dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Limite restaurado</dt>
            <dd className="font-medium text-pos">
              <Money valor={jaPaga ? liquido : Math.max(0, liquido)} />
            </dd>
          </div>
          {jaPaga && (
            <div className="flex justify-between gap-2">
              <dt className="text-muted">Saldo da conta</dt>
              <dd className="font-medium">não será alterado</dd>
            </div>
          )}
          {!jaPaga && conta && saldoDepois !== null && (
            <div className="flex justify-between gap-2">
              <dt className="text-muted">
                Saldo da conta {conta.banco} após o pagamento
              </dt>
              <dd className={negativo ? "font-semibold text-neg" : "font-medium"}>
                <Money valor={saldoDepois} />
              </dd>
            </div>
          )}
        </dl>
        {!jaPaga && negativo && (
          <p role="status" className="mt-2 text-xs text-warn">
            Atenção: a conta ficará com saldo negativo após este pagamento. Você ainda pode pagar.
          </p>
        )}
        {!jaPaga && itensSel.length > 0 && liquido <= 0 && (
          <p role="status" className="mt-2 text-xs text-warn">
            Não há valor a pagar nos itens selecionados.
          </p>
        )}
      </div>

      {erro && <ErroBox>{erro}</ErroBox>}

      {confirmando && jaPaga ? (
        <div ref={confirmarRef} tabIndex={-1} className="rounded-xl border border-primary/50 bg-primary-soft p-3 outline-none" role="group" aria-label="Confirmar marcação como paga">
          <p className="text-sm">
            Os {itensSel.length} {itensSel.length === 1 ? "item selecionado" : "itens selecionados"} (<strong>{formatBRL(liquido)}</strong>) serão marcados como pagos. O limite do cartão será liberado. O saldo da conta
            NÃO será alterado e nenhum pagamento será registrado. Use isto só se a fatura já foi paga por fora.
          </p>
          <div className="mt-3 flex justify-end gap-2">
            <Button onClick={() => setConfirmando(false)}>Cancelar</Button>
            <Button variante="primary" onClick={marcarPagos}>
              Marcar como paga
            </Button>
          </div>
        </div>
      ) : confirmando ? (
        <div className="rounded-xl border border-primary/50 bg-primary-soft p-3" role="group" aria-label="Confirmar pagamento">
          <p className="text-sm">
            Confirmar o pagamento de <strong>{formatBRL(liquido)}</strong> ({itensSel.length} {itensSel.length === 1 ? "item" : "itens"}) do cartão {cartao.nome}
            {conta ? `, debitado da conta ${conta.banco}` : ""}?
          </p>
          <div className="mt-3 flex justify-end gap-2">
            <Button onClick={() => setConfirmando(false)}>Voltar</Button>
            <Button variante="primary" onClick={pagar}>
              Confirmar pagamento
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex justify-end gap-2">
          <Button onClick={onFechar}>Cancelar</Button>
          <Button variante="primary" disabled={itensSel.length === 0 || (!jaPaga && liquido <= 0)} onClick={() => setConfirmando(true)}>
            {jaPaga ? `Marcar ${formatBRL(liquido)} como paga` : `Pagar ${formatBRL(Math.max(0, liquido))}`}
          </Button>
        </div>
      )}
    </div>
  );
}
