"use client";

import { ArrowDown, ArrowUp, ListChecks, Pencil, Plus, Trash2, Wand2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Checkbox, Input, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { SeletorCategoria } from "@/components/ui/SeletorCategoria";
import { aplicarRegras, previaRegrasExistentes, ROTULO_MATCH, TIPOS_MATCH, type MudancaRegra } from "@/lib/finance/regras";
import type { RegraCategoria, Tipo, TipoMatchRegra } from "@/lib/finance/types";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

function RegraForm({ editar, onFechar }: { editar: RegraCategoria | null; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [padrao, setPadrao] = useState(editar?.padrao ?? "");
  const [tipoMatch, setTipoMatch] = useState<TipoMatchRegra>(editar?.tipoMatch ?? "CONTEM");
  const [categoria, setCategoria] = useState(editar?.categoria ?? "");
  const [conta, setConta] = useState(editar?.conta ?? "");
  const [tipo, setTipo] = useState<"" | Tipo>(editar?.tipo ?? "");
  const [prioridade, setPrioridade] = useState(String(editar?.prioridade ?? 0));
  const [ativa, setAtiva] = useState(editar?.ativa ?? true);
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const p = Number(prioridade);
    const r = acoes.salvarRegra(
      { padrao, tipoMatch, categoria, conta: conta || null, tipo: tipo || null, ativa, prioridade: Number.isFinite(p) ? Math.trunc(p) : Number.NaN },
      editar?.id,
    );
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Regra salva.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <div className="grid grid-cols-2 gap-3">
        <Select rotulo="Quando a descrição" value={tipoMatch} onChange={(e) => setTipoMatch(e.target.value as TipoMatchRegra)}>
          {TIPOS_MATCH.map((t) => (
            <option key={t} value={t}>
              {ROTULO_MATCH[t]}
            </option>
          ))}
        </Select>
        <Input rotulo="Texto" value={padrao} onChange={(e) => setPadrao(e.target.value)} maxLength={80} required placeholder="ex.: uber" dica="Sem diferenciar maiúsculas nem acentos." />
      </div>
      <SeletorCategoria valor={categoria} onChange={setCategoria} personalizadas={ds.categorias} rotulo="Usar a categoria" />
      <div className="grid grid-cols-2 gap-3">
        <Select rotulo="Só na conta" value={conta} onChange={(e) => setConta(e.target.value)}>
          <option value="">Qualquer</option>
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
        <Select rotulo="Só em" value={tipo} onChange={(e) => setTipo(e.target.value as "" | Tipo)}>
          <option value="">Despesas e receitas</option>
          <option value="DEBITO">Despesas</option>
          <option value="CREDITO">Receitas</option>
        </Select>
      </div>
      <Input
        rotulo="Prioridade"
        type="number"
        step={1}
        value={prioridade}
        onChange={(e) => setPrioridade(e.target.value)}
        dica="Maior vence. Em empate, vale o texto mais longo (mais específico)."
      />
      <Checkbox rotulo="Regra ativa" checked={ativa} onChange={(e) => setAtiva(e.target.checked)} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

interface EstadoAplicar {
  mudancas: MudancaRegra[];
}

export default function RegrasPage() {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [form, setForm] = useState<{ editar: RegraCategoria | null } | null>(null);
  const [excluir, setExcluir] = useState<RegraCategoria | null>(null);
  const [testeDesc, setTesteDesc] = useState("");
  const [testeConta, setTesteConta] = useState("");
  const [testeTipo, setTesteTipo] = useState<Tipo>("DEBITO");
  const [previa, setPrevia] = useState<EstadoAplicar | null>(null);
  const [desfazer, setDesfazer] = useState<{ id: number; categoria: string }[] | null>(null);

  const ordenadas = useMemo(
    () => [...ds.regrasCategoria].sort((a, b) => b.prioridade - a.prioridade || b.padrao.length - a.padrao.length || a.id - b.id),
    [ds.regrasCategoria],
  );
  const teste = useMemo(
    () => (testeDesc.trim() ? aplicarRegras(ds.regrasCategoria, { descricao: testeDesc, conta: testeConta || null, tipo: testeTipo }) : null),
    [ds.regrasCategoria, testeDesc, testeConta, testeTipo],
  );

  function abrirPrevia() {
    setDesfazer(null);
    setPrevia({ mudancas: previaRegrasExistentes(ds.despesas, ds.regrasCategoria) });
  }

  function confirmarAplicacao() {
    if (!previa) return;
    const r = acoes.aplicarRegrasExistentes(previa.mudancas);
    if (!r.ok) return avisar("erro", r.erro);
    setDesfazer(r.desfazer);
    avisar("sucesso", `${r.desfazer.length} lançamento(s) recategorizado(s). Use "Desfazer" para voltar.`);
    setPrevia(null);
  }

  function desfazerAplicacao() {
    if (!desfazer) return;
    const r = acoes.desfazerRegrasExistentes(desfazer);
    if (!r.ok) return avisar("erro", r.erro);
    avisar("sucesso", "Categorias anteriores restauradas.");
    setDesfazer(null);
  }

  return (
    <>
      <PageHeader
        titulo="Regras de categoria"
        descricao="Categorize lançamentos automaticamente pela descrição"
        acoes={
          <>
            <Button icone={<ListChecks size={16} aria-hidden />} disabled={ds.regrasCategoria.length === 0} onClick={abrirPrevia}>
              Aplicar às existentes
            </Button>
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })}>
              Nova regra
            </Button>
          </>
        }
      />

      {desfazer && (
        <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-line bg-surface-2 px-4 py-3 text-sm" role="status">
          <span>{desfazer.length} lançamento(s) recategorizado(s).</span>
          <Button tamanho="sm" onClick={desfazerAplicacao}>
            Desfazer
          </Button>
        </div>
      )}

      <Card className="mb-4" aria-labelledby="t-teste">
        <CardTitulo id="t-teste" acao={<Wand2 size={14} className="text-muted" aria-hidden />}>
          Teste rápido
        </CardTitulo>
        <div className="grid gap-3 sm:grid-cols-3">
          <Input rotulo="Descrição" value={testeDesc} onChange={(e) => setTesteDesc(e.target.value)} placeholder="ex.: Uber *trip 123" />
          <Select rotulo="Conta" value={testeConta} onChange={(e) => setTesteConta(e.target.value)}>
            <option value="">Qualquer</option>
            {ds.contas.map((c) => (
              <option key={c.id} value={c.conta}>
                {c.banco} · {c.conta}
              </option>
            ))}
          </Select>
          <Select rotulo="Tipo" value={testeTipo} onChange={(e) => setTesteTipo(e.target.value as Tipo)}>
            <option value="DEBITO">Despesa</option>
            <option value="CREDITO">Receita</option>
          </Select>
        </div>
        {testeDesc.trim() && (
          <p className="mt-3 text-sm" role="status">
            {teste ? (
              <>
                Categoria: <strong>{teste.categoria}</strong> <span className="text-muted">(regra “{ROTULO_MATCH[teste.tipoMatch]} {teste.padrao}”, prioridade {teste.prioridade})</span>
              </>
            ) : (
              <span className="text-muted">Nenhuma regra se aplica a esta descrição.</span>
            )}
          </p>
        )}
      </Card>

      {ordenadas.length === 0 ? (
        <EmptyState
          titulo="Nenhuma regra ainda"
          descricao="Crie regras como “contém uber → Transporte”. Elas valem para lançamentos novos, para a conciliação de extratos e para o lançamento rápido, sem trocar a categoria que você escolher à mão."
          icone={<ListChecks size={32} />}
          acao={
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })}>
              Nova regra
            </Button>
          }
        />
      ) : (
        <Card aria-label="Regras">
          <ul className="divide-y divide-line">
            {ordenadas.map((r) => (
              <li key={r.id} className="flex items-center gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <p className={`truncate text-sm font-medium ${r.ativa ? "" : "text-muted line-through"}`}>
                    {ROTULO_MATCH[r.tipoMatch]} “{r.padrao}” → {r.categoria}
                  </p>
                  <div className="mt-1 flex flex-wrap gap-1">
                    <Badge tom="primary">prioridade {r.prioridade}</Badge>
                    {r.conta && <Badge>conta {r.conta}</Badge>}
                    {r.tipo && <Badge>{r.tipo === "DEBITO" ? "só despesas" : "só receitas"}</Badge>}
                    {!r.ativa && <Badge tom="warn">desativada</Badge>}
                  </div>
                </div>
                <div className="flex shrink-0 items-center">
                  <IconButton rotulo={`Aumentar prioridade de ${r.padrao}`} onClick={() => acoes.ajustarPrioridadeRegra(r.id, 1)}>
                    <ArrowUp size={16} aria-hidden />
                  </IconButton>
                  <IconButton rotulo={`Diminuir prioridade de ${r.padrao}`} onClick={() => acoes.ajustarPrioridadeRegra(r.id, -1)}>
                    <ArrowDown size={16} aria-hidden />
                  </IconButton>
                  <Button tamanho="sm" variante="ghost" aria-pressed={r.ativa} onClick={() => acoes.alternarRegra(r.id)}>
                    {r.ativa ? "Desativar" : "Ativar"}
                  </Button>
                  <IconButton rotulo={`Editar regra ${r.padrao}`} onClick={() => setForm({ editar: r })}>
                    <Pencil size={16} aria-hidden />
                  </IconButton>
                  <IconButton rotulo={`Excluir regra ${r.padrao}`} onClick={() => setExcluir(r)}>
                    <Trash2 size={16} aria-hidden />
                  </IconButton>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Modal aberto={form !== null} onFechar={() => setForm(null)} titulo={form?.editar ? "Editar regra" : "Nova regra"}>
        {form && <RegraForm editar={form.editar} onFechar={() => setForm(null)} />}
      </Modal>

      <Confirmar
        aberto={excluir !== null}
        titulo="Excluir regra"
        mensagem={excluir ? `Excluir a regra “${excluir.padrao}”? Lançamentos já categorizados não mudam.` : ""}
        rotuloConfirmar="Excluir"
        perigo
        onCancelar={() => setExcluir(null)}
        onConfirmar={() => {
          if (excluir) acoes.excluirRegra(excluir.id);
          setExcluir(null);
        }}
      />

      <Modal aberto={previa !== null} onFechar={() => setPrevia(null)} titulo="Aplicar regras às existentes">
        {previa && (
          <div className="flex flex-col gap-3">
            {previa.mudancas.length === 0 ? (
              <p className="text-sm text-muted">Nenhum lançamento comum mudaria de categoria.</p>
            ) : (
              <>
                <p className="text-sm">
                  <strong>{previa.mudancas.length}</strong> lançamento(s) comum(ns) mudariam de categoria. Nada foi alterado ainda.
                </p>
                <ul className="max-h-72 divide-y divide-line overflow-y-auto rounded-lg border border-line text-sm">
                  {previa.mudancas.slice(0, 200).map((m) => (
                    <li key={m.id} className="px-3 py-1.5">
                      <span className="font-medium">{m.descricao}</span>
                      <span className="block text-xs text-muted">
                        {m.de || "(sem categoria)"} → {m.para}
                      </span>
                    </li>
                  ))}
                </ul>
                {previa.mudancas.length > 200 && <p className="text-xs text-muted">Mostrando 200 de {previa.mudancas.length}.</p>}
              </>
            )}
            <div className="flex justify-end gap-2">
              <Button onClick={() => setPrevia(null)}>Cancelar</Button>
              <Button variante="primary" disabled={previa.mudancas.length === 0} onClick={confirmarAplicacao}>
                Aplicar a {previa.mudancas.length}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
}
