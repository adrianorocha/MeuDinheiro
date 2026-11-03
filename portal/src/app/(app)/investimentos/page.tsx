"use client";

import { Pencil, Plus, RefreshCw, Trash2, TrendingUp } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { GraficoPizza } from "@/components/charts/Graficos";
import { Button, IconButton } from "@/components/ui/Button";
import { Input, InputValor, Select } from "@/components/ui/Field";
import { Card, CardTitulo, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { TIPOS_INVESTIMENTO } from "@/lib/catalogo";
import { diversificacaoPorTipo, rendimentoReal, rentabilidadePercentual } from "@/lib/finance/calc";
import { fromCents, toCents } from "@/lib/finance/money";
import type { Investimento } from "@/lib/finance/types";
import { parseValorBR, valorParaCampo } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";
import { useSession } from "@/lib/session";

function InvestimentoForm({ editar, onFechar }: { editar: Investimento | null; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [tipo, setTipo] = useState(editar?.tipo ?? TIPOS_INVESTIMENTO[0]);
  const [investidoTxt, setInvestidoTxt] = useState(editar ? valorParaCampo(editar.valorInvestido) : "");
  const [atualTxt, setAtualTxt] = useState(editar ? valorParaCampo(editar.valorAtual) : "");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const investido = parseValorBR(investidoTxt);
    const atual = atualTxt.trim() ? parseValorBR(atualTxt) : investido;
    const r = acoes.salvarInvestimento({ nome, tipo, valorInvestido: investido, valorAtual: atual }, editar?.id);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Investimento salvo.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Nome" value={nome} onChange={(e) => setNome(e.target.value)} required />
      <Select rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)}>
        {[...new Set<string>([...TIPOS_INVESTIMENTO, tipo])].map((t) => (
          <option key={t}>{t}</option>
        ))}
      </Select>
      <div className="grid grid-cols-2 gap-3">
        <InputValor rotulo="Valor investido (R$)" value={investidoTxt} onChange={(e) => setInvestidoTxt(e.target.value)} />
        <InputValor rotulo="Valor atual (R$)" value={atualTxt} onChange={(e) => setAtualTxt(e.target.value)} dica="Vazio = igual ao investido." />
      </div>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

function AtualizarValorForm({ inv, onFechar }: { inv: Investimento; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [txt, setTxt] = useState(valorParaCampo(inv.valorAtual));
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const r = acoes.atualizarValorInvestimento(inv.id, parseValorBR(txt));
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Valor atualizado.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <InputValor rotulo={`Valor atual de ${inv.nome} (R$)`} value={txt} onChange={(e) => setTxt(e.target.value)} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

type Dialogo = { tipo: "form"; inv: Investimento | null } | { tipo: "valor"; inv: Investimento };

export default function InvestimentosPage() {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const privado = useSession((s) => s.privado);
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const [excluir, setExcluir] = useState<Investimento | null>(null);

  const resumo = useMemo(() => {
    const investido = fromCents(ds.investimentos.reduce((a, i) => a + toCents(i.valorInvestido), 0));
    const atual = fromCents(ds.investimentos.reduce((a, i) => a + toCents(i.valorAtual), 0));
    const rendimento = fromCents(toCents(atual) - toCents(investido));
    return { investido, atual, rendimento, pct: rentabilidadePercentual({ valorInvestido: investido, valorAtual: atual }) };
  }, [ds.investimentos]);
  const diversificacao = useMemo(() => diversificacaoPorTipo(ds.investimentos).map((d) => ({ nome: d.tipo, valor: d.valor })), [ds.investimentos]);
  const fmtPct = (v: number) => (privado ? "••••" : `${v >= 0 ? "+" : ""}${v.toFixed(2).replace(".", ",")}%`);

  return (
    <>
      <PageHeader
        titulo="Investimentos"
        descricao="Acompanhe rendimento e diversificação"
        acoes={
          <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setDialogo({ tipo: "form", inv: null })}>
            Novo investimento
          </Button>
        }
      />
      {ds.investimentos.length === 0 ? (
        <EmptyState titulo="Nenhum investimento" descricao="Cadastre seus ativos e atualize o valor atual para ver o rendimento." icone={<TrendingUp size={32} />} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Card>
              <p className="text-xs text-muted">Investido</p>
              <p className="mt-1 text-xl font-semibold">
                <Money valor={resumo.investido} />
              </p>
            </Card>
            <Card>
              <p className="text-xs text-muted">Valor atual</p>
              <p className="mt-1 text-xl font-semibold">
                <Money valor={resumo.atual} />
              </p>
            </Card>
            <Card>
              <p className="text-xs text-muted">Rendimento</p>
              <p className="mt-1 text-xl font-semibold">
                <Money valor={resumo.rendimento} tom="auto" />
              </p>
            </Card>
            <Card>
              <p className="text-xs text-muted">Rentabilidade</p>
              <p className={`tabular mt-1 text-xl font-semibold ${privado ? "" : resumo.pct >= 0 ? "text-pos" : "text-neg"}`}>{fmtPct(resumo.pct)}</p>
            </Card>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_1.2fr]">
            <Card aria-labelledby="t-div">
              <CardTitulo id="t-div">Diversificação por tipo</CardTitulo>
              <GraficoPizza dados={diversificacao} rotulo="Distribuição dos investimentos por tipo" />
            </Card>
            <Card aria-labelledby="t-ativos">
              <CardTitulo id="t-ativos">Ativos</CardTitulo>
              <ul className="divide-y divide-line">
                {ds.investimentos.map((i) => {
                  const rend = rendimentoReal(i);
                  return (
                    <li key={i.id} className="flex items-center gap-2 py-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{i.nome}</p>
                        <p className="text-xs text-muted">
                          {i.tipo} · investido <Money valor={i.valorInvestido} />
                        </p>
                      </div>
                      <div className="text-right">
                        <Money valor={i.valorAtual} className="text-sm font-semibold" />
                        <p className={`tabular text-xs ${privado ? "text-muted" : rend >= 0 ? "text-pos" : "text-neg"}`}>
                          <Money valor={rend} /> ({fmtPct(rentabilidadePercentual(i))})
                        </p>
                      </div>
                      <IconButton rotulo={`Atualizar valor de ${i.nome}`} onClick={() => setDialogo({ tipo: "valor", inv: i })}>
                        <RefreshCw size={16} aria-hidden />
                      </IconButton>
                      <IconButton rotulo={`Editar ${i.nome}`} onClick={() => setDialogo({ tipo: "form", inv: i })}>
                        <Pencil size={16} aria-hidden />
                      </IconButton>
                      <IconButton rotulo={`Excluir ${i.nome}`} onClick={() => setExcluir(i)}>
                        <Trash2 size={16} aria-hidden />
                      </IconButton>
                    </li>
                  );
                })}
              </ul>
            </Card>
          </div>
        </>
      )}
      <Modal
        aberto={dialogo !== null}
        onFechar={() => setDialogo(null)}
        titulo={dialogo?.tipo === "valor" ? "Atualizar valor atual" : dialogo?.inv ? "Editar investimento" : "Novo investimento"}
      >
        {dialogo?.tipo === "form" && <InvestimentoForm editar={dialogo.inv} onFechar={() => setDialogo(null)} />}
        {dialogo?.tipo === "valor" && <AtualizarValorForm inv={dialogo.inv} onFechar={() => setDialogo(null)} />}
      </Modal>
      <Confirmar
        aberto={excluir !== null}
        perigo
        titulo="Excluir investimento"
        rotuloConfirmar="Excluir"
        mensagem={`Excluir ${excluir?.nome ?? ""}?`}
        onConfirmar={() => {
          if (excluir) {
            acoes.excluirInvestimento(excluir.id);
            avisar("sucesso", "Investimento excluído.");
          }
          setExcluir(null);
        }}
        onCancelar={() => setExcluir(null)}
      />
    </>
  );
}
