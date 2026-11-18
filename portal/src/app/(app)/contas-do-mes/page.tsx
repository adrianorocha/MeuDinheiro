"use client";

import { CalendarPlus, CheckCircle2, Pause, Pencil, Play, Plus } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, InputValor, Select } from "@/components/ui/Field";
import { Badge, Card, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Modal, RodapeForm } from "@/components/ui/Modal";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { Money } from "@/components/ui/Money";
import { PicBadge } from "@/components/ui/PicIcon";
import { SeletorCategoria } from "@/components/ui/SeletorCategoria";
import { picDaCategoria } from "@/lib/catalogo";
import { contasDoMes, type EstadoContaMes, type LinhaContaMes } from "@/lib/finance/contasMes";
import { mesAnoDe } from "@/lib/finance/dates";
import { MAX_MESES_PROVISAO } from "@/lib/finance/operations";
import { deInputData, formatBRL, formatData, paraInputData, parseValorBR, valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const ROTULO: Record<EstadoContaMes, string> = {
  PAGA: "Paga",
  VENCIDA: "Vencida",
  PENDENTE: "Pendente",
  PROVISIONADA: "Provisionada",
  NAO_LANCADA: "Não lançada",
  DESABILITADA: "Desabilitada",
};
const TOM: Record<EstadoContaMes, "pos" | "neg" | "warn" | "neutro"> = { PAGA: "pos", VENCIDA: "neg", PENDENTE: "warn", PROVISIONADA: "neutro", NAO_LANCADA: "neutro", DESABILITADA: "neutro" };

/** Nova conta mensal (luz, água, telefone, gás…): cria a recorrência e já lança o mês quando vencido. */
function NovaContaForm({ onFechar }: { onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [descricao, setDescricao] = useState("");
  const [valorTxt, setValorTxt] = useState("");
  const [dia, setDia] = useState("10");
  const [conta, setConta] = useState(ds.contas[0]?.conta ?? "");
  const [categoria, setCategoria] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!categoria.trim()) return setErro("Informe a categoria.");
    const pic = picDaCategoria(categoria, ds.categorias) ?? "";
    const r = acoes.criarDespesaFixa({ descricao, valor: parseValorBR(valorTxt), tipo: "DEBITO", diaVencimento: Number(dia), conta, cartaoId: null, categoria: categoria.trim(), pic });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Conta mensal criada. Use Provisionar para já deixar os próximos meses prontos.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Descrição" value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder="Conta de luz" required />
      <div className="grid grid-cols-2 gap-3">
        <InputValor rotulo="Valor previsto (R$)" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} dica="Você ajusta o valor real na hora da baixa." />
        <Input rotulo="Dia do vencimento" type="number" min={1} max={31} value={dia} onChange={(e) => setDia(e.target.value)} />
      </div>
      <Select rotulo="Conta de onde costuma sair" value={conta} onChange={(e) => setConta(e.target.value)}>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.conta}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      <SeletorCategoria valor={categoria} onChange={setCategoria} personalizadas={ds.categorias} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

/** Baixa detalhada (conta, valor real, data) ou só alteração de valor/data de uma conta em aberto. */
function BaixaForm({ linha, modo, onFechar }: { linha: LinhaContaMes; modo: "baixar" | "alterar"; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const agora = useAgora();
  const oc = linha.ocorrencia!;
  const [conta, setConta] = useState(oc.conta);
  const [valorTxt, setValorTxt] = useState(valorParaCampo(oc.valor));
  const [data, setData] = useState(paraInputData(modo === "baixar" ? agora : oc.data));
  const [atualizar, setAtualizar] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const valor = parseValorBR(valorTxt);
  const difere = Number.isFinite(valor) && Math.round(valor * 100) !== Math.round(linha.regra.valor * 100);
  const contaSel = ds.contas.find((c) => c.conta === conta);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const ms = deInputData(data);
    if (Number.isNaN(ms)) return setErro("Data inválida.");
    if (modo === "baixar") {
      const r = acoes.baixarContaMensal(oc.id, { conta, valor, data: ms, atualizarRegra: atualizar && difere, regraId: linha.regra.id });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", `${linha.regra.descricao}: baixa de ${formatBRL(valor)} na conta ${contaSel?.banco ?? conta}.`);
    } else {
      const r = acoes.editLancamento(oc.id, { valor, data: ms });
      if (!r.ok) return setErro(r.erro);
      if (atualizar && difere) {
        const x = acoes.editarDespesaFixa(linha.regra.id, { valor });
        if (!x.ok) return setErro(x.erro);
      }
      avisar("sucesso", `${linha.regra.descricao} atualizada.`);
    }
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted">
        {linha.regra.descricao} · vencimento {formatData(oc.data)} · previsto {formatBRL(linha.regra.valor)}
      </p>
      <InputValor rotulo={modo === "baixar" ? "Valor cobrado (R$)" : "Novo valor (R$)"} value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
      <Input rotulo={modo === "baixar" ? "Data do pagamento" : "Nova data"} type="date" value={data} onChange={(e) => setData(e.target.value)} />
      {modo === "baixar" && (
        <Select rotulo="Pagar com a conta" value={conta} onChange={(e) => setConta(e.target.value)} dica="O valor sai do saldo desta conta.">
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
      )}
      {difere && <Checkbox rotulo={`Usar ${formatBRL(valor)} como valor previsto dos próximos meses`} checked={atualizar} onChange={(e) => setAtualizar(e.target.checked)} />}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar={modo === "baixar" ? "Dar baixa" : "Salvar"} />
    </form>
  );
}

function ProvisionarForm({ linha, onFechar }: { linha: LinhaContaMes; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [meses, setMeses] = useState("3");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const r = acoes.provisionarRecorrencia(linha.regra.id, Number(meses));
    if (!r.ok) return setErro(r.erro);
    avisar(r.criados.length ? "sucesso" : "info", r.criados.length ? `${r.criados.length} mês(es) provisionado(s). Altere valor e data quando precisar.` : "Esses meses já estão provisionados.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Quantos meses (a partir deste)" type="number" min={1} max={MAX_MESES_PROVISAO} value={meses} onChange={(e) => setMeses(e.target.value)} dica={`De 1 a ${MAX_MESES_PROVISAO}. Cria pendências com o valor e o dia da conta; o que já existe é mantido.`} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Provisionar" />
    </form>
  );
}

function DesabilitarForm({ linha, onFechar }: { linha: LinhaContaMes; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [ate, setAte] = useState("");
  const [remover, setRemover] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const limite = ate ? deInputData(ate) : null;
    if (limite !== null && Number.isNaN(limite)) return setErro("Data inválida.");
    const r = acoes.desabilitarContaMensal(linha.regra.id, { ate: limite, removerProvisionadas: remover });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", r.removidas ? `Conta desabilitada; ${r.removidas} provisionada(s) futura(s) foram para a lixeira.` : "Conta desabilitada.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Voltar a lançar em (opcional)" type="date" value={ate} onChange={(e) => setAte(e.target.value)} dica="Sem data, fica desabilitada até você habilitar." />
      <Checkbox rotulo="Enviar para a lixeira as provisionadas futuras ainda em aberto" checked={remover} onChange={(e) => setRemover(e.target.checked)} />
      <p className="text-xs text-muted">Contas já vencidas ou pagas nunca são tocadas. Os meses desabilitados não são lançados nem recuperados depois.</p>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Desabilitar" />
    </form>
  );
}

type Dialogo = { tipo: "nova" } | { tipo: "baixar" | "alterar" | "provisionar" | "desabilitar"; id: number };

export default function ContasDoMesPage() {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const [periodo, setPeriodo] = useState(() => mesAnoDe(agora));
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);

  const resumo = useMemo(() => contasDoMes(ds, periodo.mes, periodo.ano, agora), [ds, periodo, agora]);
  const alvo = dialogo && dialogo.tipo !== "nova" ? resumo.linhas.find((l) => l.regra.id === dialogo.id) : undefined;
  const fechar = () => setDialogo(null);

  function habilitar(l: LinhaContaMes) {
    const r = acoes.retomarRecorrencia(l.regra.id);
    if (!r.ok) return avisar("erro", r.erro);
    avisar("sucesso", "Conta habilitada. Os meses desabilitados não são recuperados.");
  }

  return (
    <>
      <PageHeader
        titulo="Contas do mês"
        descricao="Luz, água, telefone, gás… provisione, altere valor e data e dê baixa na conta de onde o dinheiro sai"
        acoes={
          <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setDialogo({ tipo: "nova" })} disabled={ds.contas.length === 0}>
            Nova conta mensal
          </Button>
        }
      />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <MonthPicker mes={periodo.mes} ano={periodo.ano} onChange={setPeriodo} rotulo="Mês das contas" />
        <Link href="/recorrencias/" className="text-sm underline">
          Gerenciar todas as recorrências
        </Link>
      </div>

      {resumo.linhas.length > 0 && (
        <dl className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Card>
            <dt className="text-xs text-muted">Previsto no mês</dt>
            <dd className="text-lg font-semibold">
              <Money valor={resumo.previsto} />
            </dd>
          </Card>
          <Card>
            <dt className="text-xs text-muted">Pago</dt>
            <dd className="text-lg font-semibold">
              <Money valor={resumo.pago} />
            </dd>
          </Card>
          <Card>
            <dt className="text-xs text-muted">A pagar</dt>
            <dd className="text-lg font-semibold">
              <Money valor={resumo.aPagar} />
            </dd>
          </Card>
          <Card>
            <dt className="text-xs text-muted">Vencidas</dt>
            <dd className="text-lg font-semibold">{resumo.vencidas}</dd>
          </Card>
        </dl>
      )}

      {resumo.linhas.length === 0 ? (
        <EmptyState
          titulo="Nenhuma conta neste mês"
          descricao="Cadastre luz, água, telefone, gás… uma vez; todo mês elas aparecem aqui para provisionar, ajustar e dar baixa."
          icone={<CalendarPlus size={32} />}
          acao={
            ds.contas.length > 0 ? (
              <Button variante="primary" onClick={() => setDialogo({ tipo: "nova" })}>
                Nova conta mensal
              </Button>
            ) : undefined
          }
        />
      ) : (
        <Card>
          <ul className="divide-y divide-line">
            {resumo.linhas.map((l) => {
              const oc = l.ocorrencia;
              const contaNome = ds.contas.find((c) => c.conta === (oc?.conta ?? l.regra.conta))?.banco ?? (oc?.conta ?? l.regra.conta);
              return (
                <li key={l.regra.id} className={`flex flex-wrap items-center gap-3 py-3 ${l.estado === "DESABILITADA" ? "opacity-60" : ""}`}>
                  <PicBadge pic={ds.categorias.find((c) => c.nome.trim().toLowerCase() === l.regra.categoria.trim().toLowerCase())?.pic || l.regra.pic} />
                  <div className="min-w-0 flex-1 basis-48">
                    <p className="truncate text-sm font-medium">{l.regra.descricao}</p>
                    <p className="truncate text-xs text-muted">
                      {l.estado === "PAGA" ? `Paga em ${formatData(l.data)}` : `Vence ${formatData(l.data)}`} · {l.noCartao ? "no cartão (pague pela fatura)" : contaNome}
                    </p>
                    {l.valorDifere && <p className="text-xs text-muted">Previsto na conta: {formatBRL(l.regra.valor)}</p>}
                  </div>
                  <Badge tom={TOM[l.estado]}>{ROTULO[l.estado]}</Badge>
                  {l.desabilitada && l.estado !== "DESABILITADA" && <Badge tom="warn">Conta desabilitada</Badge>}
                  <Money valor={l.valor} className="text-sm font-semibold" />
                  <div className="flex flex-wrap gap-2">
                    {oc && !oc.pago && !l.noCartao && (
                      <Button tamanho="sm" variante="primary" icone={<CheckCircle2 size={14} aria-hidden />} onClick={() => setDialogo({ tipo: "baixar", id: l.regra.id })} aria-label={`Dar baixa em ${l.regra.descricao}`}>
                        Dar baixa
                      </Button>
                    )}
                    {oc && !oc.pago && (
                      <Button tamanho="sm" icone={<Pencil size={14} aria-hidden />} onClick={() => setDialogo({ tipo: "alterar", id: l.regra.id })} aria-label={`Alterar valor ou data de ${l.regra.descricao}`}>
                        Alterar
                      </Button>
                    )}
                    {!oc && l.estado !== "DESABILITADA" && (
                      <Button tamanho="sm" icone={<CalendarPlus size={14} aria-hidden />} onClick={() => setDialogo({ tipo: "provisionar", id: l.regra.id })} aria-label={`Provisionar ${l.regra.descricao}`}>
                        Provisionar
                      </Button>
                    )}
                    {l.desabilitada ? (
                      <Button tamanho="sm" icone={<Play size={14} aria-hidden />} onClick={() => habilitar(l)} aria-label={`Habilitar ${l.regra.descricao}`}>
                        Habilitar
                      </Button>
                    ) : (
                      <Button tamanho="sm" icone={<Pause size={14} aria-hidden />} onClick={() => setDialogo({ tipo: "desabilitar", id: l.regra.id })} aria-label={`Desabilitar ${l.regra.descricao}`}>
                        Desabilitar
                      </Button>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        </Card>
      )}

      <Modal aberto={dialogo?.tipo === "nova"} onFechar={fechar} titulo="Nova conta mensal">
        {dialogo?.tipo === "nova" && <NovaContaForm onFechar={fechar} />}
      </Modal>
      <Modal aberto={dialogo?.tipo === "baixar" || dialogo?.tipo === "alterar"} onFechar={fechar} titulo={dialogo?.tipo === "baixar" ? `Dar baixa: ${alvo?.regra.descricao ?? ""}` : `Alterar: ${alvo?.regra.descricao ?? ""}`}>
        {alvo?.ocorrencia && (dialogo?.tipo === "baixar" || dialogo?.tipo === "alterar") && <BaixaForm key={`${dialogo.tipo}-${alvo.ocorrencia.id}`} linha={alvo} modo={dialogo.tipo} onFechar={fechar} />}
      </Modal>
      <Modal aberto={dialogo?.tipo === "provisionar"} onFechar={fechar} titulo={`Provisionar: ${alvo?.regra.descricao ?? ""}`}>
        {alvo && dialogo?.tipo === "provisionar" && <ProvisionarForm key={alvo.regra.id} linha={alvo} onFechar={fechar} />}
      </Modal>
      <Modal aberto={dialogo?.tipo === "desabilitar"} onFechar={fechar} titulo={`Desabilitar: ${alvo?.regra.descricao ?? ""}`}>
        {alvo && dialogo?.tipo === "desabilitar" && <DesabilitarForm key={alvo.regra.id} linha={alvo} onFechar={fechar} />}
      </Modal>
    </>
  );
}
