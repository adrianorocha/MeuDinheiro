"use client";

import { ShieldAlert } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Select } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { cartaoIdDe } from "@/lib/finance/calc";
import { lancamentosApagaveisDoCartao } from "@/lib/finance/operations";
import { toCents, fromCents } from "@/lib/finance/money";
import type { Cartao } from "@/lib/finance/types";
import { confirmarSenha, traduzirErroAuth } from "@/lib/firebase/auth";
import { deInputData, formatBRL, formatData } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { useSession } from "@/lib/session";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

/**
 * R75 - apaga só os lançamentos do cartão (todos ou os escolhidos), com a opção de excluir o cartão junto.
 * Exige a senha de login (nuvem) ou o nome do cartão (local) e grava no log de controle.
 */
export function ApagarLancamentosCartaoModal({ cartao, onFechar }: { cartao: Cartao | null; onFechar: () => void }) {
  return (
    <Modal aberto={cartao !== null} onFechar={onFechar} titulo="Apagar lançamentos do cartão">
      {cartao && <Conteudo key={cartao.id} cartao={cartao} onFechar={onFechar} />}
    </Modal>
  );
}

function Conteudo({ cartao, onFechar }: { cartao: Cartao; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const modo = useSession((s) => s.modo);
  const nuvem = modo === "firebase";
  const lista = useMemo(() => {
    const l = lancamentosApagaveisDoCartao(ds, cartao.id);
    return l ? [...l.credito, ...l.debito].sort((a, b) => a.data - b.data) : [];
  }, [ds, cartao.id]);
  const [sel, setSel] = useState<Set<number>>(new Set());
  const [status, setStatus] = useState<"TODOS" | "ABERTO" | "PAGO">("TODOS");
  const [antesDe, setAntesDe] = useState("");
  const [excluirCartao, setExcluirCartao] = useState(false);
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  const limite = antesDe ? deInputData(antesDe) : NaN;
  const visiveis = lista.filter(
    (d) => (status === "TODOS" || (status === "ABERTO" ? !d.pago : d.pago)) && (Number.isNaN(limite) || d.data < limite),
  );
  const todosVisiveis = visiveis.length > 0 && visiveis.every((d) => sel.has(d.id));
  const nomeCartao = (d: (typeof lista)[number]) => {
    const cid = cartaoIdDe(d);
    return cid !== null ? (ds.cartoes.find((c) => c.id === cid)?.nome ?? "Cartão") : "Débito";
  };
  const escolhidos = lista.filter((d) => sel.has(d.id));
  const total = fromCents(escolhidos.reduce((s, d) => s + toCents(d.valor), 0));

  function alternarTodos() {
    setSel((atual) => {
      const novo = new Set(atual);
      for (const d of visiveis) {
        if (todosVisiveis) novo.delete(d.id);
        else novo.add(d.id);
      }
      return novo;
    });
  }

  async function enviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    if (sel.size === 0 && !excluirCartao) return setErro("Escolha ao menos um lançamento.");
    if (nuvem) {
      if (!senha) return setErro("Digite sua senha de login para confirmar.");
      setOcupado(true);
      try {
        await confirmarSenha(senha);
      } catch (err) {
        setOcupado(false);
        return setErro(traduzirErroAuth(err));
      }
      setOcupado(false);
    } else if (senha.trim() !== cartao.nome.trim()) {
      return setErro("Digite o nome do cartão exatamente como está para confirmar.");
    }
    const r = acoes.apagarLancamentosDoCartao(cartao.id, [...sel], { excluirCartao });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", excluirCartao ? "Cartão e lançamentos excluídos e registrados no log." : `${r.resumo.lancamentos} lançamento(s) apagado(s) e registrado(s) no log.`);
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <div className="flex gap-3 rounded-xl border border-neg/40 bg-neg-soft p-3 text-sm text-neg">
        <ShieldAlert size={20} className="mt-0.5 shrink-0" aria-hidden />
        <p>Os lançamentos apagados não vão para a lixeira e não podem ser recuperados. A ação fica registrada no log.</p>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Select rotulo="Mostrar" value={status} onChange={(e) => setStatus(e.target.value as typeof status)}>
          <option value="TODOS">Todos</option>
          <option value="ABERTO">Em aberto</option>
          <option value="PAGO">Pagos</option>
        </Select>
        <Input rotulo="Anteriores a (opcional)" type="date" value={antesDe} onChange={(e) => setAntesDe(e.target.value)} />
      </div>
      {lista.length === 0 ? (
        <p className="text-sm text-muted">Este cartão não tem lançamentos.</p>
      ) : (
        <>
          <Checkbox rotulo={`Selecionar todos os exibidos (${visiveis.length})`} checked={todosVisiveis} onChange={alternarTodos} disabled={visiveis.length === 0} />
          <ul className="max-h-64 divide-y divide-line overflow-auto rounded-lg border border-line" aria-label="Lançamentos do cartão">
            {visiveis.map((d) => (
              <li key={d.id} className="px-3 py-2">
                <label className="flex cursor-pointer items-center gap-3">
                  <input
                    type="checkbox"
                    className="size-4 accent-[var(--primary)]"
                    checked={sel.has(d.id)}
                    onChange={() =>
                      setSel((atual) => {
                        const novo = new Set(atual);
                        if (novo.has(d.id)) novo.delete(d.id);
                        else novo.add(d.id);
                        return novo;
                      })
                    }
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm">{d.descricao}</span>
                    <span className="block text-xs text-muted">
                      {formatData(d.data)} · {nomeCartao(d)} · {d.pago ? "pago" : "em aberto"}
                    </span>
                  </span>
                  <Money valor={d.valor} className="text-sm" />
                </label>
              </li>
            ))}
            {visiveis.length === 0 && <li className="px-3 py-3 text-sm text-muted">Nenhum lançamento neste filtro.</li>}
          </ul>
        </>
      )}
      <p className="text-sm" aria-live="polite">
        Selecionados: <strong>{sel.size}</strong> · total: <strong>{formatBRL(total)}</strong>
      </p>
      <div>
        <Checkbox rotulo="Excluir também o cartão (apaga todas as compras de crédito e as recorrências dele)" checked={excluirCartao} onChange={(e) => setExcluirCartao(e.target.checked)} />
        <p className="ml-6 text-xs text-muted">Sem marcar, o cartão e as recorrências continuam; só os lançamentos escolhidos saem.</p>
      </div>
      <Input
        rotulo={nuvem ? "Senha de login" : `Digite o nome do cartão (${cartao.nome})`}
        type={nuvem ? "password" : "text"}
        autoComplete={nuvem ? "current-password" : "off"}
        value={senha}
        onChange={(e) => setSenha(e.target.value)}
      />
      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar} disabled={ocupado}>
          Cancelar
        </Button>
        <Button type="submit" variante="danger" disabled={ocupado}>
          {ocupado ? "Confirmando..." : excluirCartao ? "Apagar e excluir cartão" : "Apagar selecionados"}
        </Button>
      </div>
    </form>
  );
}
