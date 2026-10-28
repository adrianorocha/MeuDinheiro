"use client";

import { ArrowLeftRight, CalendarClock, Download, Landmark, Pencil, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { ListaLancamentos } from "@/components/lancamentos/ListaLancamentos";
import { Button, IconButton } from "@/components/ui/Button";
import { Checkbox, Input, InputValor, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { PicBadge } from "@/components/ui/PicIcon";
import { BANCOS } from "@/lib/catalogo";
import { saldoConta } from "@/lib/finance/calc";
import { contaTemDependencias, diferencaAjuste } from "@/lib/finance/operations";
import type { Conta } from "@/lib/finance/types";
import { deInputData, formatBRL, formatData, paraInputData, parseValorBR } from "@/lib/format";
import { lancamentosParaCsv } from "@/lib/csv";
import { baixarArquivo } from "@/lib/download";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

function ContaForm({ editar, onFechar }: { editar: Conta | null; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const bancoInicial = BANCOS.find((b) => b.nome === editar?.banco)?.nome ?? (editar ? "Outro" : BANCOS[0].nome);
  const [banco, setBanco] = useState(bancoInicial);
  const [nomeOutro, setNomeOutro] = useState(editar && bancoInicial === "Outro" ? editar.banco : "");
  const [agencia, setAgencia] = useState(editar?.agencia ?? "");
  const [numero, setNumero] = useState(editar?.conta ?? "");
  const [titular, setTitular] = useState(editar?.titular ?? "");
  const [saldoTxt, setSaldoTxt] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const ref = BANCOS.find((b) => b.nome === banco) ?? BANCOS[BANCOS.length - 1];
    const nomeBanco = banco === "Outro" ? nomeOutro.trim() : banco;
    if (!nomeBanco) return setErro("Informe o nome do banco.");
    if (editar) {
      const r = acoes.editarConta(editar.id, { banco: nomeBanco, pic: ref.pic, agencia, conta: numero, titular });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Conta atualizada.");
    } else {
      const saldo = saldoTxt.trim() ? parseValorBR(saldoTxt) : 0;
      if (Number.isNaN(saldo)) return setErro("Saldo inicial inválido.");
      const r = acoes.criarConta({ banco: nomeBanco, pic: ref.pic, agencia, conta: numero, titular, saldoInicial: saldo });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Conta criada.");
    }
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Select rotulo="Banco" value={banco} onChange={(e) => setBanco(e.target.value)}>
        {BANCOS.map((b) => (
          <option key={b.nome}>{b.nome}</option>
        ))}
      </Select>
      {banco === "Outro" && <Input rotulo="Nome do banco" value={nomeOutro} onChange={(e) => setNomeOutro(e.target.value)} required />}
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Agência" value={agencia} onChange={(e) => setAgencia(e.target.value)} />
        <Input rotulo="Número da conta" value={numero} onChange={(e) => setNumero(e.target.value)} required />
      </div>
      <Input rotulo="Titular" value={titular} onChange={(e) => setTitular(e.target.value)} />
      {!editar && (
        <InputValor rotulo="Saldo inicial (R$)" value={saldoTxt} onChange={(e) => setSaldoTxt(e.target.value)} dica="Vira um lançamento de Saldo inicial no extrato." />
      )}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

function AjusteSaldoForm({ conta, onFechar }: { conta: Conta; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [realTxt, setRealTxt] = useState("");
  const [obs, setObs] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const noSistema = saldoConta(ds.despesas, conta.conta);
  const informado = realTxt.trim() !== "" && !Number.isNaN(parseValorBR(realTxt));
  const diferenca = informado ? diferencaAjuste(ds, conta.conta, parseValorBR(realTxt)) : null;

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!informado) return setErro("Informe o saldo real da conta no banco.");
    const r = acoes.ajustarSaldoConta({ conta: conta.conta, saldoReal: parseValorBR(realTxt), observacao: obs });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", `Saldo ajustado (${r.diferenca > 0 ? "+" : "−"}${formatBRL(Math.abs(r.diferenca))}). Exclua o lançamento "Ajuste" do extrato para desfazer.`);
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <div>
        <p className="text-xs text-muted">Saldo no sistema</p>
        <p className="text-xl font-semibold">
          <Money valor={noSistema} tom="auto" />
        </p>
      </div>
      <InputValor rotulo="Saldo real no banco (R$)" value={realTxt} onChange={(e) => setRealTxt(e.target.value)} dica="Informe o saldo que o banco mostra agora." />
      <Input rotulo="Observação (opcional)" value={obs} onChange={(e) => setObs(e.target.value)} maxLength={80} />
      {diferenca !== null && (
        <p className="text-sm" role="status">
          {diferenca === 0 ? (
            "O saldo já confere com o informado."
          ) : (
            <>
              Diferença: <strong>{diferenca > 0 ? "+" : "−"}<Money valor={Math.abs(diferenca)} /></strong> ({diferenca > 0 ? "entrada" : "saída"} de ajuste; não conta como receita nem despesa)
            </>
          )}
        </p>
      )}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Ajustar saldo" />
    </form>
  );
}

function TransferenciaForm({ onFechar, origemInicial }: { onFechar: () => void; origemInicial?: string }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [origem, setOrigem] = useState(origemInicial ?? ds.contas[0]?.conta ?? "");
  const [destino, setDestino] = useState(ds.contas.find((c) => c.conta !== (origemInicial ?? ds.contas[0]?.conta))?.conta ?? "");
  const [valorTxt, setValorTxt] = useState("");
  const [agendar, setAgendar] = useState(false);
  const [data, setData] = useState(() => paraInputData(Date.now() + 86_400_000));
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const valor = parseValorBR(valorTxt);
    if (!(valor > 0)) return setErro("Informe um valor maior que zero.");
    if (agendar) {
      const ts = deInputData(data);
      if (Number.isNaN(ts)) return setErro("Informe uma data válida.");
      const r = acoes.agendarTransferencia({ dataAgendada: ts, contaOrigem: origem, contaDestino: destino, valor });
      if (!r.ok) return setErro(r.erro);
      // Se a data já chegou, executa na hora.
      const exec = acoes.executarAgendadas();
      avisar("sucesso", exec.ok && exec.executadas > 0 ? "Transferência executada." : "Transferência agendada.");
    } else {
      const r = acoes.transferir({ origem, destino, valor });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Transferência realizada.");
    }
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Select rotulo="Conta de origem" value={origem} onChange={(e) => setOrigem(e.target.value)}>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.conta}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      <Select rotulo="Conta de destino" value={destino} onChange={(e) => setDestino(e.target.value)}>
        <option value="">Selecione</option>
        {ds.contas
          .filter((c) => c.conta !== origem)
          .map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
      </Select>
      <InputValor rotulo="Valor (R$)" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
      <Checkbox rotulo="Agendar para uma data" checked={agendar} onChange={(e) => setAgendar(e.target.checked)} />
      {agendar && <Input rotulo="Data da transferência" type="date" value={data} onChange={(e) => setData(e.target.value)} dica="Executada automaticamente ao abrir o portal a partir dessa data, se houver saldo." />}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar={agendar ? "Agendar" : "Transferir"} />
    </form>
  );
}

export default function ContasPage() {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [formConta, setFormConta] = useState<{ editar: Conta | null } | null>(null);
  const [transf, setTransf] = useState<{ origem?: string } | null>(null);
  const [excluir, setExcluir] = useState<Conta | null>(null);
  const [extratoId, setExtratoId] = useState<number | null>(null);
  const [ajuste, setAjuste] = useState<Conta | null>(null);

  const extratoConta = ds.contas.find((c) => c.id === extratoId) ?? null;
  const extrato = useMemo(
    () => (extratoConta ? ds.despesas.filter((d) => d.conta === extratoConta.conta && !d.cartaoId).sort((a, b) => b.data - a.data || b.id - a.id) : []),
    [ds.despesas, extratoConta],
  );
  const agendadas = useMemo(() => [...ds.transferenciasAgendadas].sort((a, b) => a.dataAgendada - b.dataAgendada), [ds.transferenciasAgendadas]);
  const nomeConta = (num: string) => ds.contas.find((c) => c.conta === num)?.banco ?? num;

  function confirmarExclusao() {
    if (!excluir) return;
    const r = acoes.excluirConta(excluir.id, true);
    if (r.ok) {
      avisar("sucesso", "Conta excluída.");
      if (extratoId === excluir.id) setExtratoId(null);
    } else avisar("erro", r.erro);
    setExcluir(null);
  }

  return (
    <>
      <PageHeader
        titulo="Contas"
        descricao="Saldos calculados a partir do extrato"
        acoes={
          <>
            <Button icone={<ArrowLeftRight size={16} aria-hidden />} onClick={() => setTransf({})} disabled={ds.contas.length < 2}>
              Transferir
            </Button>
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setFormConta({ editar: null })}>
              Nova conta
            </Button>
          </>
        }
      />

      {ds.contas.length === 0 ? (
        <EmptyState titulo="Nenhuma conta cadastrada" descricao="Crie sua primeira conta, com o saldo inicial, para começar a registrar lançamentos." icone={<Landmark size={32} />} acao={<Button variante="primary" onClick={() => setFormConta({ editar: null })}>Nova conta</Button>} />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {ds.contas.map((c) => (
            <li key={c.id}>
              <Card className={extratoId === c.id ? "border-primary" : ""}>
                <div className="flex items-start gap-3">
                  <PicBadge pic={c.pic || "bank"} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{c.banco}</p>
                    <p className="truncate text-xs text-muted">
                      Ag. {c.agencia || "—"} · Conta {c.conta}
                    </p>
                    {c.titular && <p className="truncate text-xs text-muted">{c.titular}</p>}
                  </div>
                  <IconButton rotulo={`Editar conta ${c.banco}`} onClick={() => setFormConta({ editar: c })}>
                    <Pencil size={16} aria-hidden />
                  </IconButton>
                  <IconButton rotulo={`Excluir conta ${c.banco}`} onClick={() => setExcluir(c)}>
                    <Trash2 size={16} aria-hidden />
                  </IconButton>
                </div>
                <p className="mt-4 text-xs text-muted">Saldo</p>
                <p className="text-2xl font-semibold">
                  <Money valor={c.saldo} tom="auto" />
                </p>
                <div className="mt-3 flex gap-2">
                  <Button tamanho="sm" onClick={() => setExtratoId(extratoId === c.id ? null : c.id)} aria-expanded={extratoId === c.id}>
                    {extratoId === c.id ? "Ocultar extrato" : "Ver extrato"}
                  </Button>
                  <Button tamanho="sm" onClick={() => setTransf({ origem: c.conta })} disabled={ds.contas.length < 2}>
                    Transferir
                  </Button>
                  <Button tamanho="sm" onClick={() => setAjuste(c)}>
                    Ajustar saldo
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {extratoConta && (
        <Card className="mt-6" aria-labelledby="t-extrato">
          <CardTitulo
            id="t-extrato"
            acao={
              <Button
                tamanho="sm"
                icone={<Download size={14} aria-hidden />}
                onClick={() => baixarArquivo(`extrato-${extratoConta.conta}.csv`, lancamentosParaCsv(extrato), "text/csv;charset=utf-8")}
                disabled={extrato.length === 0}
              >
                CSV
              </Button>
            }
          >
            Extrato · {extratoConta.banco} {extratoConta.conta}
          </CardTitulo>
          {extrato.length === 0 ? <p className="py-6 text-center text-sm text-muted">Sem lançamentos nesta conta.</p> : <ListaLancamentos itens={extrato} semOrigem />}
        </Card>
      )}

      <Card className="mt-6" aria-labelledby="t-agendadas">
        <CardTitulo
          id="t-agendadas"
          acao={
            <Button tamanho="sm" icone={<CalendarClock size={14} aria-hidden />} onClick={() => {
              const r = acoes.executarAgendadas();
              if (r.ok) avisar(r.falhas.length ? "erro" : "info", r.falhas.length ? r.falhas[0].erro : r.executadas ? `${r.executadas} transferência(s) executada(s).` : "Nenhuma transferência vencida.");
            }}>
              Executar vencidas
            </Button>
          }
        >
          Transferências agendadas
        </CardTitulo>
        {agendadas.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted">Nenhuma transferência agendada.</p>
        ) : (
          <ul className="divide-y divide-line">
            {agendadas.map((t) => (
              <li key={t.id} className="flex items-center gap-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">
                    {nomeConta(t.contaOrigem)} → {nomeConta(t.contaDestino)}
                  </p>
                  <p className="text-xs text-muted">{formatData(t.dataAgendada)}</p>
                </div>
                <Badge tom={t.executada ? "pos" : "warn"}>{t.executada ? "Executada" : "Agendada"}</Badge>
                <Money valor={t.valor} className="text-sm font-semibold" />
                <IconButton rotulo="Excluir agendamento" onClick={() => acoes.excluirAgendada(t.id)}>
                  <Trash2 size={16} aria-hidden />
                </IconButton>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Modal aberto={formConta !== null} onFechar={() => setFormConta(null)} titulo={formConta?.editar ? "Editar conta" : "Nova conta"}>
        {formConta && <ContaForm editar={formConta.editar} onFechar={() => setFormConta(null)} />}
      </Modal>
      <Modal aberto={transf !== null} onFechar={() => setTransf(null)} titulo="Transferência entre contas">
        {transf && <TransferenciaForm origemInicial={transf.origem} onFechar={() => setTransf(null)} />}
      </Modal>
      <Modal aberto={ajuste !== null} onFechar={() => setAjuste(null)} titulo="Ajustar saldo da conta">
        {ajuste && <AjusteSaldoForm conta={ajuste} onFechar={() => setAjuste(null)} />}
      </Modal>
      <Confirmar
        aberto={excluir !== null}
        perigo
        titulo="Excluir conta"
        rotuloConfirmar="Excluir"
        mensagem={
          excluir && contaTemDependencias(ds, excluir.id)
            ? `A conta ${excluir.banco} possui lançamentos e/ou cartões vinculados. Excluí-la também remove esses lançamentos, os cartões e as compras deles. Esta ação não pode ser desfeita.`
            : `Excluir a conta ${excluir?.banco ?? ""}?`
        }
        onConfirmar={confirmarExclusao}
        onCancelar={() => setExcluir(null)}
      />
    </>
  );
}
