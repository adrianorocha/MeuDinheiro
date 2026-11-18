"use client";

import { ShieldAlert } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { formatBRL } from "@/lib/format";
import { confirmarSenha, traduzirErroAuth } from "@/lib/firebase/auth";
import { resumoExclusaoCartao } from "@/lib/finance/operations";
import type { Cartao } from "@/lib/finance/types";
import { useDataset } from "@/lib/hooks";
import { useSession } from "@/lib/session";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

/**
 * R74 - exclusão de cartão. Sem compras em aberto nem recorrências segue a regra antiga (confirmação simples).
 * Com elas, mostra o que será apagado e exige a SENHA DE LOGIN (reautenticação no Firebase; no modo local, que não tem
 * senha, digitar o nome do cartão). A operação grava no log de controle (Backups > Trilha de alterações).
 */
export function ExcluirCartaoModal({ cartao, onFechar, onExcluido }: { cartao: Cartao | null; onFechar: () => void; onExcluido: () => void }) {
  return (
    <Modal aberto={cartao !== null} onFechar={onFechar} titulo="Excluir cartão">
      {cartao && <Conteudo key={cartao.id} cartao={cartao} onFechar={onFechar} onExcluido={onExcluido} />}
    </Modal>
  );
}

function Conteudo({ cartao, onFechar, onExcluido }: { cartao: Cartao; onFechar: () => void; onExcluido: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const modo = useSession((s) => s.modo);
  const [apagarDebito, setApagarDebito] = useState(false);
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  const virtual = cartao.cartaoPrincipalId != null;
  const r = resumoExclusaoCartao(ds, cartao.id, { apagarDebito });
  const forcado = !!r && (r.abertos > 0 || r.recorrencias.length > 0);
  const nuvem = modo === "firebase";

  async function enviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    if (forcado) {
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
    }
    const res = forcado ? acoes.excluirCartaoForcado(cartao.id, { apagarDebito }) : acoes.excluirCartao(cartao.id, true);
    if (!res.ok) return setErro(res.erro);
    avisar("sucesso", forcado ? "Cartão excluído e registrado no log." : "Cartão excluído.");
    onExcluido();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      {forcado && r ? (
        <>
          <div className="flex gap-3 rounded-xl border border-neg/40 bg-neg-soft p-3 text-sm text-neg">
            <ShieldAlert size={20} className="mt-0.5 shrink-0" aria-hidden />
            <div>
              <p className="font-semibold">Exclusão com dados em aberto</p>
              <p className="mt-1">Esta ação não pode ser desfeita e os lançamentos apagados não vão para a lixeira.</p>
            </div>
          </div>
          <ul className="list-disc space-y-1 pl-5 text-sm">
            <li>{virtual ? "Cartão virtual" : `Cartão${r.cartoes.length > 1 ? " e virtuais" : ""}`}: <strong>{r.cartoes.join(", ")}</strong></li>
            <li>
              <strong>{r.lancamentos}</strong> compra(s) de crédito apagada(s), sendo <strong>{r.abertos}</strong> em aberto
            </li>
            {r.recorrencias.length > 0 && (
              <li>
                <strong>{r.recorrencias.length}</strong> recorrência(s) removida(s): {r.recorrencias.join(", ")}
              </li>
            )}
            <li>
              Total dos lançamentos apagados: <strong>{formatBRL(r.valorApagado)}</strong>
            </li>
          </ul>
          {r.debito > 0 && (
            <div>
              <Checkbox rotulo={`Apagar também as ${r.debito} compra(s) no débito`} checked={apagarDebito} onChange={(e) => setApagarDebito(e.target.checked)} />
              <p className="ml-6 text-xs text-muted">Sem marcar, elas ficam como lançamentos comuns da conta. Apagá-las altera o saldo da conta.</p>
            </div>
          )}
          <Input
            rotulo={nuvem ? "Senha de login" : `Digite o nome do cartão (${cartao.nome})`}
            type={nuvem ? "password" : "text"}
            autoComplete={nuvem ? "current-password" : "off"}
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            dica="A exclusão fica registrada em Backups > Trilha de alterações."
          />
        </>
      ) : (
        <p className="text-sm text-muted">
          {virtual
            ? `Excluir o cartão virtual ${cartao.nome}? O histórico pago passa para o cartão físico.`
            : r && r.lancamentos > 0
              ? `Excluir ${cartao.nome} remove também seus cartões virtuais e o histórico de compras já pagas. Esta ação não pode ser desfeita.`
              : `Excluir o cartão ${cartao.nome}?`}
        </p>
      )}
      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar} disabled={ocupado}>
          Cancelar
        </Button>
        <Button type="submit" variante="danger" disabled={ocupado}>
          {ocupado ? "Confirmando..." : "Excluir"}
        </Button>
      </div>
    </form>
  );
}
