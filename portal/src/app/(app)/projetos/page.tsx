"use client";

import { FolderKanban, Pencil, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { ListaLancamentos } from "@/components/lancamentos/ListaLancamentos";
import { Button, IconButton } from "@/components/ui/Button";
import { Checkbox, Input, InputValor } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { chaveTag, resumoProjeto, rotuloTag, tagsUsadas, type StatusProjeto } from "@/lib/finance/tags";
import type { Projeto } from "@/lib/finance/types";
import { deInputData, paraInputData, parseValorBR, valorParaCampo } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const STATUS: Record<StatusProjeto, { texto: string; tom: "neutro" | "pos" | "warn" | "neg" }> = {
  SEM_ORCAMENTO: { texto: "Sem orçamento", tom: "neutro" },
  NO_ORCAMENTO: { texto: "Dentro do orçamento", tom: "pos" },
  ATENCAO: { texto: "Perto do limite", tom: "warn" },
  ESTOUROU: { texto: "Orçamento estourado", tom: "neg" },
};

function ProjetoForm({ editar, tagInicial, onFechar }: { editar: Projeto | null; tagInicial?: string; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [nome, setNome] = useState(editar?.nome ?? tagInicial ?? "");
  const [tag, setTag] = useState(editar?.tag ?? tagInicial ?? "");
  const [orcTxt, setOrcTxt] = useState(editar?.orcamento ? valorParaCampo(editar.orcamento) : "");
  const [inicio, setInicio] = useState(editar?.inicio != null ? paraInputData(editar.inicio) : "");
  const [fim, setFim] = useState(editar?.fim != null ? paraInputData(editar.fim) : "");
  const [cor, setCor] = useState(editar?.cor ?? "#4f46e5");
  const [ativo, setAtivo] = useState(editar?.ativo ?? true);
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const orc = orcTxt.trim() ? parseValorBR(orcTxt) : null;
    const i = inicio ? deInputData(inicio) : null;
    const f = fim ? deInputData(fim) + 86_399_999 : null;
    if ((i !== null && Number.isNaN(i)) || (f !== null && Number.isNaN(f))) return setErro("Informe datas válidas.");
    const r = acoes.salvarProjeto({ nome, tag, orcamento: orc, inicio: i, fim: f, ativo, cor }, editar?.id);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Projeto salvo.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Nome" value={nome} onChange={(e) => setNome(e.target.value)} maxLength={60} required />
      <Input
        rotulo="Tag do projeto"
        value={tag}
        onChange={(e) => setTag(e.target.value)}
        maxLength={30}
        required
        dica="Os lançamentos com esta tag entram no projeto (sem diferenciar maiúsculas nem acentos)."
      />
      <InputValor rotulo="Orçamento (opcional)" value={orcTxt} onChange={(e) => setOrcTxt(e.target.value)} />
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Início (opcional)" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
        <Input rotulo="Fim (opcional)" type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
      </div>
      <Input rotulo="Cor" type="color" value={cor} onChange={(e) => setCor(e.target.value)} className="!p-1" />
      <Checkbox rotulo="Projeto ativo" checked={ativo} onChange={(e) => setAtivo(e.target.checked)} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

export default function ProjetosPage() {
  const ds = useDataset();
  const [form, setForm] = useState<{ editar: Projeto | null; tag?: string } | null>(null);
  const [excluir, setExcluir] = useState<Projeto | null>(null);
  const [aberto, setAberto] = useState<number | null>(null);

  const resumos = useMemo(
    () => [...ds.projetos].sort((a, b) => Number(b.ativo) - Number(a.ativo) || a.nome.localeCompare(b.nome, "pt-BR")).map((p) => resumoProjeto(p, ds.despesas)),
    [ds.projetos, ds.despesas],
  );
  const semProjeto = useMemo(() => {
    const com = new Set(ds.projetos.map((p) => chaveTag(p.tag)));
    return tagsUsadas(ds.despesas).filter((t) => !com.has(t.chave)).slice(0, 8);
  }, [ds.projetos, ds.despesas]);

  return (
    <>
      <PageHeader
        titulo="Projetos"
        descricao="Acompanhe quanto cada projeto (reforma, viagem…) está custando, pelas tags dos lançamentos"
        acoes={
          <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })}>
            Novo projeto
          </Button>
        }
      />

      {resumos.length === 0 ? (
        <EmptyState
          titulo="Nenhum projeto ainda"
          descricao="Marque lançamentos com uma tag (ex.: #reforma) e crie um projeto com a mesma tag para ver o total, o orçamento e a divisão por categoria e mês."
          icone={<FolderKanban size={32} />}
        />
      ) : (
        <div className="space-y-4">
          {resumos.map((r) => {
            const p = r.projeto;
            const st = STATUS[r.status];
            return (
              <Card key={p.id} aria-label={`Projeto ${p.nome}`}>
                <CardTitulo
                  acao={
                    <span className="flex items-center gap-1">
                      <Badge tom={st.tom}>{st.texto}</Badge>
                      <IconButton rotulo={`Editar ${p.nome}`} onClick={() => setForm({ editar: p })}>
                        <Pencil size={16} aria-hidden />
                      </IconButton>
                      <IconButton rotulo={`Excluir ${p.nome}`} onClick={() => setExcluir(p)}>
                        <Trash2 size={16} aria-hidden />
                      </IconButton>
                    </span>
                  }
                >
                  <span className="inline-flex items-center gap-2">
                    <span className="inline-block size-3 rounded-full" style={{ background: p.cor ?? "var(--primary)" }} aria-hidden />
                    {p.nome} <span className="text-xs font-normal text-muted">#{rotuloTag(p.tag)}</span>
                    {!p.ativo && <Badge tom="warn">inativo</Badge>}
                  </span>
                </CardTitulo>
                <p className="text-2xl font-semibold tracking-tight">
                  <Money valor={r.gasto} />
                  {r.orcamento !== null && <span className="ml-2 text-sm font-normal text-muted">de <Money valor={r.orcamento} /></span>}
                </p>
                {r.percentual !== null && (
                  <div className="mt-2">
                    <ProgressBar razao={r.percentual} tom={r.status === "ESTOUROU" ? "neg" : r.status === "ATENCAO" ? "warn" : "pos"} rotulo={`Uso do orçamento de ${p.nome}`} />
                    <p className="mt-1 text-xs text-muted">{Math.round(r.percentual * 100)}% do orçamento</p>
                  </div>
                )}
                <p className="mt-1 text-xs text-muted">
                  {r.quantidade} lançamento(s){p.inicio != null || p.fim != null ? " no período do projeto" : ""}
                </p>

                {r.quantidade > 0 && (
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <div>
                      <h3 className="mb-1 text-xs font-semibold uppercase text-muted">Por categoria</h3>
                      <ul className="space-y-0.5 text-sm">
                        {r.porCategoria.map((c) => (
                          <li key={c.nome} className="flex justify-between gap-2">
                            <span className="truncate">{c.nome}</span>
                            <Money valor={c.total} />
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <h3 className="mb-1 text-xs font-semibold uppercase text-muted">Por mês</h3>
                      <ul className="space-y-0.5 text-sm">
                        {r.porMes.map((m) => (
                          <li key={m.mes} className="flex justify-between gap-2">
                            <span>{m.mes}</span>
                            <Money valor={m.total} />
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                )}

                {r.quantidade > 0 && (
                  <div className="mt-3">
                    <Button tamanho="sm" onClick={() => setAberto(aberto === p.id ? null : p.id)} aria-expanded={aberto === p.id}>
                      {aberto === p.id ? "Ocultar lançamentos" : "Ver lançamentos"}
                    </Button>
                    {aberto === p.id && (
                      <div className="mt-2">
                        <ListaLancamentos itens={r.itens.slice(0, 200)} />
                      </div>
                    )}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}

      {semProjeto.length > 0 && (
        <Card className="mt-4" aria-labelledby="t-sem">
          <CardTitulo id="t-sem">Tags sem projeto</CardTitulo>
          <div className="flex flex-wrap gap-2">
            {semProjeto.map((t) => (
              <button
                key={t.chave}
                type="button"
                onClick={() => setForm({ editar: null, tag: t.rotulo })}
                className="rounded-full border border-line px-3 py-1 text-xs text-muted hover:bg-surface-2 hover:text-fg"
              >
                #{t.rotulo} ({t.usos}) — criar projeto
              </button>
            ))}
          </div>
        </Card>
      )}

      <Modal aberto={form !== null} onFechar={() => setForm(null)} titulo={form?.editar ? "Editar projeto" : "Novo projeto"}>
        {form && <ProjetoForm editar={form.editar} tagInicial={form.tag} onFechar={() => setForm(null)} />}
      </Modal>

      <Confirmar
        aberto={excluir !== null}
        titulo="Excluir projeto"
        mensagem={excluir ? `Excluir o projeto “${excluir.nome}”? Os lançamentos e suas tags continuam como estão.` : ""}
        rotuloConfirmar="Excluir"
        perigo
        onCancelar={() => setExcluir(null)}
        onConfirmar={() => {
          if (excluir) acoes.excluirProjeto(excluir.id);
          setExcluir(null);
        }}
      />
    </>
  );
}
