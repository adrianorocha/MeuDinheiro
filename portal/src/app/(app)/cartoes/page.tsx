"use client";

import { CreditCard, Pencil, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { LancamentoModal } from "@/components/lancamentos/LancamentoModal";
import { ListaLancamentos } from "@/components/lancamentos/ListaLancamentos";
import { Button, IconButton } from "@/components/ui/Button";
import { Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader, ProgressBar } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { Money } from "@/components/ui/Money";
import { TIPOS_CARTAO } from "@/lib/catalogo";
import { melhorDiaCompra } from "@/lib/finance/analises";
import { mesAnoDe } from "@/lib/finance/dates";
import { cartoesDoGrupo, faturaDaCompra, principalDe, resumoFatura } from "@/lib/finance/calc";
import { cartaoTemCompras } from "@/lib/finance/operations";
import type { Cartao } from "@/lib/finance/types";
import { formatBRL, formatData, formatPercentual, parseValorBR, valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

type Forma = "fisico" | "virtual";

interface FormProps {
  editar: Cartao | null;
  onFechar: () => void;
}

function CartaoForm({ editar, onFechar }: FormProps) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const fisicos = ds.cartoes.filter((c) => c.cartaoPrincipalId == null);
  const [forma, setForma] = useState<Forma>(editar ? (editar.cartaoPrincipalId != null ? "virtual" : "fisico") : "fisico");
  const [principalId, setPrincipalId] = useState(String(editar?.cartaoPrincipalId ?? fisicos[0]?.id ?? ""));
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [final, setFinal] = useState(editar?.finalCartao ?? "");
  const [tipo, setTipo] = useState(editar?.tipo ?? TIPOS_CARTAO[0]);
  const [limiteTxt, setLimiteTxt] = useState(editar ? valorParaCampo(editar.limiteTotal) : "");
  const [fechamento, setFechamento] = useState(String(editar?.diaFechamento ?? 25));
  const [vencimento, setVencimento] = useState(String(editar?.diaVencimento ?? 5));
  const [contaId, setContaId] = useState(String(editar?.contaId ?? ds.contas[0]?.id ?? ""));
  const [erro, setErro] = useState<string | null>(null);

  const virtual = forma === "virtual";
  const principal = fisicos.find((c) => c.id === Number(principalId));
  // Virtual: campos herdados do físico, somente leitura.
  const vTipo = virtual && principal ? principal.tipo : tipo;
  const vLimite = virtual && principal ? valorParaCampo(principal.limiteTotal) : limiteTxt;
  const vFecha = virtual && principal ? String(principal.diaFechamento) : fechamento;
  const vVence = virtual && principal ? String(principal.diaVencimento) : vencimento;
  const vConta = virtual && principal ? String(principal.contaId) : contaId;

  function enviar(e: FormEvent) {
    e.preventDefault();
    let r;
    if (virtual) {
      if (!principal) return setErro("Selecione o cartão físico.");
      const dados = { nome, finalCartao: final.trim(), tipo: principal.tipo, limiteTotal: principal.limiteTotal, diaFechamento: principal.diaFechamento, diaVencimento: principal.diaVencimento, contaId: principal.contaId };
      r = editar ? acoes.editarCartao(editar.id, { nome, finalCartao: final.trim() }) : acoes.criarCartao({ ...dados, cartaoPrincipalId: principal.id });
    } else {
      const limite = parseValorBR(limiteTxt);
      if (!(limite >= 0)) return setErro("Informe o limite total.");
      const dados = { nome, finalCartao: final.trim(), tipo, limiteTotal: limite, diaFechamento: Number(fechamento), diaVencimento: Number(vencimento), contaId: Number(contaId) };
      r = editar ? acoes.editarCartao(editar.id, dados) : acoes.criarCartao(dados);
    }
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", editar ? "Cartão atualizado." : virtual ? "Cartão virtual criado." : "Cartão criado.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      {ds.contas.length === 0 && <ErroBox>Cadastre uma conta para vincular o cartão (a fatura é paga por ela).</ErroBox>}
      {!editar && (
        <Segmentado<Forma>
          rotulo="Tipo de cartão"
          valor={forma}
          onChange={setForma}
          opcoes={[
            { valor: "fisico", rotulo: "Físico" },
            { valor: "virtual", rotulo: "Virtual" },
          ]}
        />
      )}
      {virtual && (
        <>
          <Select rotulo="Cartão físico" value={principalId} onChange={(e) => setPrincipalId(e.target.value)} disabled={editar !== null} required>
            {fisicos.length === 0 && <option value="">Cadastre um cartão físico primeiro</option>}
            {fisicos.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nome} (final {c.finalCartao || "—"})
              </option>
            ))}
          </Select>
          <p className="text-xs text-muted">O virtual compartilha limite, conta, fechamento, vencimento e fatura com o cartão físico.</p>
        </>
      )}
      {!virtual && editar && ds.cartoes.some((c) => c.cartaoPrincipalId === editar.id) && (
        <p className="text-xs text-muted">Alterações de limite, conta, dias e tipo são aplicadas também aos cartões virtuais deste cartão.</p>
      )}
      <Input rotulo="Nome do cartão" value={nome} onChange={(e) => setNome(e.target.value)} required />
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Final (4 dígitos)" value={final} maxLength={4} inputMode="numeric" onChange={(e) => setFinal(e.target.value.replace(/\D/g, ""))} />
        <Select rotulo="Tipo" value={vTipo} onChange={(e) => setTipo(e.target.value)} disabled={virtual}>
          {[...new Set<string>([...TIPOS_CARTAO, vTipo])].map((t) => (
            <option key={t}>{t}</option>
          ))}
        </Select>
      </div>
      <InputValor rotulo="Limite total (R$)" value={vLimite} onChange={(e) => setLimiteTxt(e.target.value)} readOnly={virtual} disabled={virtual} />
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo="Dia de fechamento" type="number" min={1} max={31} value={vFecha} onChange={(e) => setFechamento(e.target.value)} readOnly={virtual} disabled={virtual} />
        <Input rotulo="Dia de vencimento" type="number" min={1} max={31} value={vVence} onChange={(e) => setVencimento(e.target.value)} readOnly={virtual} disabled={virtual} />
      </div>
      <Select rotulo="Conta vinculada" value={vConta} onChange={(e) => setContaId(e.target.value)} disabled={virtual}>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.id}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

export default function CartoesPage() {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const mesHoje = mesAnoDe(agora);
  const [selecionadoId, setSelecionadoId] = useState<number | null>(null);
  const [fatura, setFatura] = useState<{ mes: number; ano: number } | null>(null);
  const [formCartao, setFormCartao] = useState<{ editar: Cartao | null } | null>(null);
  const [excluir, setExcluir] = useState<Cartao | null>(null);
  const [pagar, setPagar] = useState(false);
  const [compra, setCompra] = useState(false);

  // Um item de lista por grupo: o cartão principal (físico) e, dentro, seus virtuais.
  const principais = useMemo(() => ds.cartoes.filter((c) => principalDe(c, ds.cartoes) === c), [ds.cartoes]);
  const selecionado = principais.find((c) => c.id === selecionadoId) ?? principais[0] ?? null;
  const grupo = useMemo(() => (selecionado ? cartoesDoGrupo(selecionado, ds.cartoes) : []), [selecionado, ds.cartoes]);
  const cicloAtual = selecionado ? faturaDaCompra(selecionado, agora) : null;
  const periodo = fatura ?? cicloAtual;
  const resumo = useMemo(
    () => (selecionado && periodo ? resumoFatura(selecionado, ds.despesas, periodo.mes, periodo.ano, ds.cartoes) : null),
    [selecionado, periodo, ds.despesas, ds.cartoes],
  );
  const conta = selecionado ? ds.contas.find((c) => c.id === selecionado.contaId) : undefined;

  function confirmarPagamento() {
    if (!selecionado || !periodo) return;
    const r = acoes.pagarFatura(selecionado.id, periodo.mes, periodo.ano);
    if (r.ok) avisar("sucesso", `Fatura paga: ${formatBRL(r.pagamento.valor)}.`);
    else avisar("erro", r.erro);
    setPagar(false);
  }

  function confirmarExclusao() {
    if (!excluir) return;
    const r = acoes.excluirCartao(excluir.id, true);
    if (r.ok) {
      avisar("sucesso", "Cartão excluído.");
      setSelecionadoId(null);
    } else avisar("erro", r.erro);
    setExcluir(null);
  }

  const nomePrincipal = (c: Cartao) => ds.cartoes.find((x) => x.id === c.cartaoPrincipalId)?.nome ?? "cartão físico";

  return (
    <>
      <PageHeader
        titulo="Cartões"
        descricao="Limite, faturas e pagamento"
        acoes={
          <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setFormCartao({ editar: null })} disabled={ds.contas.length === 0}>
            Novo cartão
          </Button>
        }
      />

      {ds.cartoes.length === 0 ? (
        <EmptyState titulo="Nenhum cartão cadastrado" descricao="Cadastre um cartão (vinculado a uma conta) para registrar compras e acompanhar a fatura." icone={<CreditCard size={32} />} />
      ) : (
        <>
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {principais.map((c) => {
              const virtuais = ds.cartoes.filter((v) => v.cartaoPrincipalId === c.id);
              const usado = Math.max(0, c.limiteTotal - c.limiteDisponivel);
              const razao = c.limiteTotal > 0 ? usado / c.limiteTotal : 0;
              const ativo = selecionado?.id === c.id;
              return (
                <li key={c.id}>
                  <Card className={ativo ? "border-primary" : ""}>
                    <div className="flex items-start gap-2">
                      <button type="button" onClick={() => { setSelecionadoId(c.id); setFatura(null); }} aria-pressed={ativo} className="min-w-0 flex-1 rounded-lg text-left">
                        <p className="truncate font-medium">{c.nome}</p>
                        <p className="text-xs text-muted">
                          Final {c.finalCartao || "—"} · {c.tipo}
                          {c.cartaoPrincipalId != null && " · virtual sem físico"}
                        </p>
                      </button>
                      <IconButton rotulo={`Editar cartão ${c.nome}`} onClick={() => setFormCartao({ editar: c })}>
                        <Pencil size={16} aria-hidden />
                      </IconButton>
                      <IconButton rotulo={`Excluir cartão ${c.nome}`} onClick={() => setExcluir(c)}>
                        <Trash2 size={16} aria-hidden />
                      </IconButton>
                    </div>
                    <div className="mt-3 flex items-end justify-between text-sm">
                      <span className="text-muted">{virtuais.length > 0 ? "Disponível (compartilhado)" : "Disponível"}</span>
                      <Money valor={c.limiteDisponivel} className="text-lg font-semibold" />
                    </div>
                    <div className="mt-2">
                      <ProgressBar razao={razao} tom={razao >= 1 ? "neg" : razao >= 0.8 ? "warn" : "primary"} rotulo={`Limite usado do cartão ${c.nome}`} />
                    </div>
                    <p className="mt-1 flex justify-between text-xs text-muted">
                      <span>
                        Usado <Money valor={usado} /> ({formatPercentual(razao)})
                      </span>
                      <span>
                        Total <Money valor={c.limiteTotal} />
                      </span>
                    </p>
                    <p className="mt-2 text-xs text-muted">
                      Fecha dia {c.diaFechamento} · vence dia {c.diaVencimento}
                    </p>
                    {(() => {
                      const mh = melhorDiaCompra(c, mesHoje.mes, mesHoje.ano);
                      return (
                        <p className="mt-1 text-xs text-muted">
                          Melhor dia de compra: <strong className="text-fg">{String(mh.dia).padStart(2, "0")}/{String(mh.mes).padStart(2, "0")}</strong> (até {mh.prazoMaximoDias} dias para pagar)
                        </p>
                      );
                    })()}
                    {virtuais.length > 0 && (
                      <ul className="mt-3 divide-y divide-line border-t border-line" aria-label={`Cartões virtuais de ${c.nome}`}>
                        {virtuais.map((v) => (
                          <li key={v.id} className="flex items-center gap-2 py-2">
                            <div className="min-w-0 flex-1">
                              <p className="flex items-center gap-2 truncate text-sm font-medium">
                                {v.nome} <Badge tom="primary">VIRTUAL</Badge>
                              </p>
                              <p className="truncate text-xs text-muted">
                                Final {v.finalCartao || "—"} · compartilha limite com {nomePrincipal(v)}
                              </p>
                            </div>
                            <IconButton rotulo={`Editar cartão virtual ${v.nome}`} onClick={() => setFormCartao({ editar: v })}>
                              <Pencil size={16} aria-hidden />
                            </IconButton>
                            <IconButton rotulo={`Excluir cartão virtual ${v.nome}`} onClick={() => setExcluir(v)}>
                              <Trash2 size={16} aria-hidden />
                            </IconButton>
                          </li>
                        ))}
                      </ul>
                    )}
                  </Card>
                </li>
              );
            })}
          </ul>

          {selecionado && periodo && resumo && (
            <Card className="mt-6" aria-labelledby="t-fatura">
              <CardTitulo id="t-fatura" acao={<Button tamanho="sm" icone={<ShoppingBag size={14} aria-hidden />} onClick={() => setCompra(true)}>Nova compra</Button>}>
                Fatura · {selecionado.nome}
                {grupo.length > 1 && ` (+${grupo.length - 1} virtual${grupo.length > 2 ? "is" : ""})`}
              </CardTitulo>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <MonthPicker mes={periodo.mes} ano={periodo.ano} onChange={setFatura} rotulo="Mês da fatura" />
                <div className="flex gap-2">
                  {cicloAtual && (periodo.mes !== cicloAtual.mes || periodo.ano !== cicloAtual.ano) && (
                    <Button tamanho="sm" onClick={() => setFatura(null)}>
                      Fatura atual
                    </Button>
                  )}
                </div>
              </div>
              <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div>
                  <dt className="text-xs text-muted">Fechamento</dt>
                  <dd className="text-sm font-medium">{formatData(resumo.fechamento)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">Vencimento</dt>
                  <dd className="text-sm font-medium">{formatData(resumo.vencimento)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">Total da fatura</dt>
                  <dd className="text-sm font-semibold">
                    <Money valor={resumo.total} />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">Em aberto</dt>
                  <dd className="text-sm font-semibold">
                    <Money valor={resumo.emAberto} />
                  </dd>
                </div>
              </dl>
              <div className="mt-4 flex flex-wrap items-center gap-3">
                {resumo.paga ? <Badge tom="pos">Fatura paga</Badge> : resumo.itens.length === 0 ? <Badge>Sem compras</Badge> : <Badge tom="warn">Em aberto</Badge>}
                <Button variante="primary" disabled={resumo.emAberto <= 0} onClick={() => setPagar(true)}>
                  Pagar fatura
                </Button>
                {conta && <span className="text-xs text-muted">Débito na conta {conta.banco} · {conta.conta}</span>}
              </div>
              <div className="mt-4">
                {resumo.itens.length === 0 ? <p className="py-6 text-center text-sm text-muted">Nenhuma compra nesta fatura.</p> : <ListaLancamentos itens={resumo.itens} semOrigem={grupo.length <= 1} />}
              </div>
            </Card>
          )}
        </>
      )}

      <Modal aberto={formCartao !== null} onFechar={() => setFormCartao(null)} titulo={formCartao?.editar ? "Editar cartão" : "Novo cartão"}>
        {formCartao && <CartaoForm editar={formCartao.editar} onFechar={() => setFormCartao(null)} />}
      </Modal>
      <Confirmar
        aberto={pagar}
        titulo="Pagar fatura"
        rotuloConfirmar="Pagar"
        mensagem={resumo && selecionado && periodo ? `Pagar ${formatBRL(resumo.emAberto)} da fatura ${String(periodo.mes).padStart(2, "0")}/${periodo.ano} do cartão ${selecionado.nome}${grupo.length > 1 ? " e seus virtuais" : ""}? O valor será debitado da conta ${conta?.banco ?? ""}.` : ""}
        onConfirmar={confirmarPagamento}
        onCancelar={() => setPagar(false)}
      />
      <Confirmar
        aberto={excluir !== null}
        perigo
        titulo="Excluir cartão"
        rotuloConfirmar="Excluir"
        mensagem={
          excluir && excluir.cartaoPrincipalId != null
            ? `Excluir o cartão virtual ${excluir.nome}? Só é possível sem compras em aberto; o histórico pago passa para ${nomePrincipal(excluir)}.`
            : excluir && cartaoTemCompras(ds, excluir.id)
              ? `Excluir ${excluir.nome} remove também seus cartões virtuais e todo o histórico de compras (só é possível sem compras em aberto). Esta ação não pode ser desfeita.`
              : `Excluir o cartão ${excluir?.nome ?? ""}?`
        }
        onConfirmar={confirmarExclusao}
        onCancelar={() => setExcluir(null)}
      />
      <LancamentoModal aberto={compra} onFechar={() => setCompra(false)} predefinicao={{ entrada: "cartao", cartaoId: selecionado?.id }} />
    </>
  );
}
