"use client";

import { Pencil, PieChart, Plus, Trash2 } from "lucide-react";
import { SeletorCategoria } from "@/components/ui/SeletorCategoria";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { InputValor, Select } from "@/components/ui/Field";
import { Badge, Card, EmptyState, ErroBox, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { progressoOrcamento } from "@/lib/finance/calc";
import { mesAnoDe } from "@/lib/finance/dates";
import type { Orcamento } from "@/lib/finance/types";
import { formatMesAno, formatPercentual, parseValorBR, valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

function OrcamentoForm({ editar, onFechar }: { editar: Orcamento | null; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [categoria, setCategoria] = useState(editar?.categoria ?? "");
  const [limiteTxt, setLimiteTxt] = useState(editar ? valorParaCampo(editar.valorLimite) : "");
  // R80 - de quem é o gasto: "todos" (como sempre), "eu" (desconta o que é de outras pessoas) ou "p:<id>" (só de uma pessoa)
  const [quem, setQuem] = useState(editar?.escopo === "EU" ? "eu" : editar?.escopo === "PESSOA" && editar.pessoaId != null ? `p:${editar.pessoaId}` : "todos");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const escopo = quem === "eu" ? "EU" : quem.startsWith("p:") ? "PESSOA" : undefined;
    const r = acoes.salvarOrcamento(categoria, parseValorBR(limiteTxt), escopo, escopo === "PESSOA" ? Number(quem.slice(2)) : null);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Orçamento salvo.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <SeletorCategoria valor={categoria} onChange={setCategoria} personalizadas={ds.categorias} disabled={editar !== null} dica="Um orçamento por categoria e por pessoa." />
      <Select rotulo="Quem gasta?" value={quem} onChange={(e) => setQuem(e.target.value)} disabled={editar !== null} dica="Para tirar um gasto do seu orçamento, vincule o lançamento à pessoa (A receber) em Pessoas.">
        <option value="todos">Todos (tudo da categoria)</option>
        <option value="eu">Só eu (desconta o que é de outras pessoas)</option>
        {ds.pessoas.map((p) => (
          <option key={p.id} value={`p:${p.id}`}>
            Só {p.nome}
          </option>
        ))}
      </Select>
      <InputValor rotulo="Limite mensal (R$)" value={limiteTxt} onChange={(e) => setLimiteTxt(e.target.value)} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

export default function OrcamentosPage() {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const { mes, ano } = mesAnoDe(agora);
  const [form, setForm] = useState<{ editar: Orcamento | null } | null>(null);
  const [excluir, setExcluir] = useState<Orcamento | null>(null);

  const lista = useMemo(
    () => ds.orcamentos.map((o) => progressoOrcamento(o, ds.despesas, mes, ano, ds.acertos)).sort((a, b) => b.pct - a.pct),
    [ds.orcamentos, ds.despesas, ds.acertos, mes, ano],
  );

  return (
    <>
      <PageHeader
        titulo="Orçamentos"
        descricao={`Gastos de ${formatMesAno(mes, ano)} por categoria`}
        acoes={
          <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })}>
            Novo orçamento
          </Button>
        }
      />
      {lista.length === 0 ? (
        <EmptyState titulo="Nenhum orçamento" descricao="Defina um limite mensal por categoria para acompanhar seus gastos." icone={<PieChart size={32} />} />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {lista.map((p) => {
            const tom = p.status === "ESTOURADO" ? "neg" : p.status === "ATENCAO" ? "warn" : "primary";
            return (
              <li key={p.orcamento.id}>
                <Card>
                  <div className="flex items-start gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">
                        {p.orcamento.categoria}
                        {p.orcamento.escopo === "EU" && <span className="ml-2 text-xs font-normal text-muted">só eu</span>}
                        {p.orcamento.escopo === "PESSOA" && <span className="ml-2 text-xs font-normal text-muted">só {ds.pessoas.find((x) => x.id === p.orcamento.pessoaId)?.nome ?? "pessoa"}</span>}
                      </p>
                      <p className="text-xs text-muted">
                        <Money valor={p.gasto} /> de <Money valor={p.limite} />
                      </p>
                    </div>
                    <Badge tom={p.status === "ESTOURADO" ? "neg" : p.status === "ATENCAO" ? "warn" : "pos"}>
                      {p.status === "ESTOURADO" ? "Estourado" : p.status === "ATENCAO" ? "Atenção" : "No limite"}
                    </Badge>
                    <IconButton rotulo={`Editar orçamento ${p.orcamento.categoria}`} onClick={() => setForm({ editar: p.orcamento })}>
                      <Pencil size={16} aria-hidden />
                    </IconButton>
                    <IconButton rotulo={`Excluir orçamento ${p.orcamento.categoria}`} onClick={() => setExcluir(p.orcamento)}>
                      <Trash2 size={16} aria-hidden />
                    </IconButton>
                  </div>
                  <div className="mt-3">
                    <ProgressBar razao={p.pct} tom={tom} rotulo={`Uso do orçamento ${p.orcamento.categoria}`} />
                  </div>
                  <p className="mt-1.5 flex justify-between text-xs text-muted">
                    <span>{formatPercentual(p.pct)} utilizado</span>
                    <span>
                      {p.limite - p.gasto >= 0 ? "Restam " : "Excedeu "}
                      <Money valor={Math.abs(p.limite - p.gasto)} />
                    </span>
                  </p>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
      <Modal aberto={form !== null} onFechar={() => setForm(null)} titulo={form?.editar ? "Editar orçamento" : "Novo orçamento"}>
        {form && <OrcamentoForm editar={form.editar} onFechar={() => setForm(null)} />}
      </Modal>
      <Confirmar
        aberto={excluir !== null}
        perigo
        titulo="Excluir orçamento"
        rotuloConfirmar="Excluir"
        mensagem={`Excluir o orçamento de ${excluir?.categoria ?? ""}?`}
        onConfirmar={() => {
          if (excluir) {
            acoes.excluirOrcamento(excluir.id);
            avisar("sucesso", "Orçamento excluído.");
          }
          setExcluir(null);
        }}
        onCancelar={() => setExcluir(null)}
      />
    </>
  );
}
