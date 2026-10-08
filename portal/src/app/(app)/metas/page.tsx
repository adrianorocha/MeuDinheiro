"use client";

import { Pencil, PiggyBank, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Input, InputValor, Select } from "@/components/ui/Field";
import { Badge, Card, EmptyState, ErroBox, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import type { Meta } from "@/lib/finance/types";
import { analisarMeta } from "@/lib/finance/analises";
import { deInputData, formatData, formatPercentual, paraInputData, parseValorBR, valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

function MetaForm({ editar, onFechar }: { editar: Meta | null; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [objetivoTxt, setObjetivoTxt] = useState(editar ? valorParaCampo(editar.valorObjetivo) : "");
  const [prazo, setPrazo] = useState(editar?.dataAlvo ? paraInputData(editar.dataAlvo) : "");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const objetivo = parseValorBR(objetivoTxt);
    const dataAlvo = prazo ? deInputData(prazo) : null;
    if (dataAlvo !== null && Number.isNaN(dataAlvo)) return setErro("Prazo inválido.");
    const r = editar ? acoes.editarMeta(editar.id, { nome, valorObjetivo: objetivo, dataAlvo }) : acoes.criarMeta({ nome, valorObjetivo: objetivo, dataAlvo });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", editar ? "Meta atualizada." : "Meta criada.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Nome da meta" value={nome} onChange={(e) => setNome(e.target.value)} maxLength={60} required />
      <InputValor rotulo="Valor objetivo (R$)" value={objetivoTxt} onChange={(e) => setObjetivoTxt(e.target.value)} />
      <Input rotulo="Prazo (opcional)" type="date" value={prazo} onChange={(e) => setPrazo(e.target.value)} dica="Com prazo, mostramos o aporte mensal necessário e se você está no ritmo." />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

function AporteForm({ meta, onFechar }: { meta: Meta; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [conta, setConta] = useState(ds.contas[0]?.conta ?? "");
  const [valorTxt, setValorTxt] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const saldo = ds.contas.find((c) => c.conta === conta)?.saldo;

  function enviar(e: FormEvent) {
    e.preventDefault();
    const r = acoes.aportarMeta({ metaId: meta.id, conta, valor: parseValorBR(valorTxt) });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Aporte realizado.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Select rotulo="Debitar da conta" value={conta} onChange={(e) => setConta(e.target.value)}>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.conta}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      {saldo !== undefined && (
        <p className="text-xs text-muted">
          Saldo disponível: <Money valor={saldo} />
        </p>
      )}
      <InputValor rotulo="Valor do aporte (R$)" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Aportar" />
    </form>
  );
}

function ExcluirMetaForm({ meta, onFechar }: { meta: Meta; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [conta, setConta] = useState(ds.contas[0]?.conta ?? "");
  const [erro, setErro] = useState<string | null>(null);
  const temSaldo = meta.valorGuardado > 0;

  function enviar(e: FormEvent) {
    e.preventDefault();
    const r = acoes.excluirMeta(meta.id, temSaldo ? conta : null);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", temSaldo ? "Meta excluída e valor resgatado." : "Meta excluída.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted">
        {temSaldo ? (
          <>
            A meta <strong>{meta.nome}</strong> tem <Money valor={meta.valorGuardado} /> guardados. Escolha a conta que receberá o resgate.
          </>
        ) : (
          <>Excluir a meta {meta.nome}?</>
        )}
      </p>
      {temSaldo && (
        <Select rotulo="Conta de destino do resgate" value={conta} onChange={(e) => setConta(e.target.value)}>
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
      )}
      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="mt-2 flex justify-end gap-2">
        <Button onClick={onFechar}>Cancelar</Button>
        <Button type="submit" variante="danger">
          Excluir meta
        </Button>
      </div>
    </form>
  );
}

type Dialogo = { tipo: "form"; meta: Meta | null } | { tipo: "aporte"; meta: Meta } | { tipo: "excluir"; meta: Meta };

export default function MetasPage() {
  const ds = useDataset();
  const agora = useAgora();
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const fechar = () => setDialogo(null);

  const titulo = dialogo?.tipo === "aporte" ? `Aportar em ${dialogo.meta.nome}` : dialogo?.tipo === "excluir" ? "Excluir meta" : dialogo?.meta ? "Editar meta" : "Nova meta";

  return (
    <>
      <PageHeader
        titulo="Metas"
        descricao="Cofrinhos para seus objetivos"
        acoes={
          <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setDialogo({ tipo: "form", meta: null })}>
            Nova meta
          </Button>
        }
      />
      {ds.metas.length === 0 ? (
        <EmptyState titulo="Nenhuma meta" descricao="Crie uma meta e faça aportes a partir das suas contas." icone={<PiggyBank size={32} />} />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {ds.metas.map((m) => {
            const razao = m.valorObjetivo > 0 ? m.valorGuardado / m.valorObjetivo : 0;
            const prazo = analisarMeta(m, ds.despesas, agora);
            return (
              <li key={m.id}>
                <Card>
                  <div className="flex items-start gap-2">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden>
                      <PiggyBank size={18} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{m.nome}</p>
                      <p className="text-xs text-muted">
                        Objetivo <Money valor={m.valorObjetivo} />
                      </p>
                    </div>
                    {razao >= 1 && <Badge tom="pos">Concluída</Badge>}
                    <IconButton rotulo={`Editar meta ${m.nome}`} onClick={() => setDialogo({ tipo: "form", meta: m })}>
                      <Pencil size={16} aria-hidden />
                    </IconButton>
                    <IconButton rotulo={`Excluir meta ${m.nome}`} onClick={() => setDialogo({ tipo: "excluir", meta: m })}>
                      <Trash2 size={16} aria-hidden />
                    </IconButton>
                  </div>
                  <p className="mt-3 text-2xl font-semibold">
                    <Money valor={m.valorGuardado} />
                  </p>
                  <div className="mt-2">
                    <ProgressBar razao={razao} tom={razao >= 1 ? "pos" : "primary"} rotulo={`Progresso da meta ${m.nome}`} />
                  </div>
                  {m.dataAlvo !== null && prazo.status !== null && (
                    <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
                      <Badge tom={prazo.status === "CONCLUIDA" || prazo.status === "NO_RITMO" ? "pos" : prazo.status === "ATRASADA" ? "neg" : "warn"}>
                        {prazo.status === "CONCLUIDA" ? "Concluída" : prazo.status === "NO_RITMO" ? "No ritmo" : prazo.status === "ATRASADA" ? "Atrasada" : "Abaixo do ritmo"}
                      </Badge>
                      <span>Prazo {formatData(m.dataAlvo)}</span>
                      {prazo.status !== "CONCLUIDA" && prazo.aporteMensalNecessario !== null && (
                        <span>
                          Precisa guardar <Money valor={prazo.aporteMensalNecessario} />/mês ({prazo.mesesRestantes} mês(es)); ritmo atual <Money valor={prazo.ritmo} />/mês
                        </span>
                      )}
                    </div>
                  )}
                  <div className="mt-2 flex items-center justify-between">
                    <span className="text-xs text-muted">{formatPercentual(razao)} concluído</span>
                    <Button tamanho="sm" variante="primary" onClick={() => setDialogo({ tipo: "aporte", meta: m })} disabled={ds.contas.length === 0}>
                      Aportar
                    </Button>
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
      <Modal aberto={dialogo !== null} onFechar={fechar} titulo={titulo}>
        {dialogo?.tipo === "form" && <MetaForm editar={dialogo.meta} onFechar={fechar} />}
        {dialogo?.tipo === "aporte" && <AporteForm meta={dialogo.meta} onFechar={fechar} />}
        {dialogo?.tipo === "excluir" && <ExcluirMetaForm meta={dialogo.meta} onFechar={fechar} />}
      </Modal>
    </>
  );
}
