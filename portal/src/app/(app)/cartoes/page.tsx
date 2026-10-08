"use client";

import { CircleCheck, CreditCard, Landmark, Pencil, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { LimiteComprometido } from "@/components/cartoes/LimiteComprometido";
import { PagarFaturaModal } from "@/components/cartoes/PagarFaturaModal";
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
import { cartaoIdDe, cartoesDoGrupo, comprometimentoCartao, debitosDoCartao, faturaDaCompra, principalDe, resumoFatura, saldoDoCartao, totalDebitos } from "@/lib/finance/calc";
import { cartaoTemCompras } from "@/lib/finance/operations";
import type { Cartao } from "@/lib/finance/types";
import { formatData, formatPercentual, parseValorBR, valorParaCampo } from "@/lib/format";
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
  const [forma, setForma] = useState<Forma>("fisico");
  // Na edição "" = cartão físico independente; na criação o seletor só aparece no modo virtual.
  const [principalId, setPrincipalId] = useState(editar ? String(editar.cartaoPrincipalId ?? "") : String(fisicos[0]?.id ?? ""));
  const temVirtuais = editar ? ds.cartoes.some((c) => c.cartaoPrincipalId === editar.id) : false;
  const opcoesFisicos = fisicos.filter((c) => c.id !== editar?.id);
  const orfao = editar?.cartaoPrincipalId != null && !fisicos.some((c) => c.id === editar.cartaoPrincipalId);
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [final, setFinal] = useState(editar?.finalCartao ?? "");
  const [tipo, setTipo] = useState(editar?.tipo ?? TIPOS_CARTAO[0]);
  const [limiteTxt, setLimiteTxt] = useState(editar ? valorParaCampo(editar.limiteTotal) : "");
  const [fechamento, setFechamento] = useState(String(editar?.diaFechamento ?? 25));
  const [vencimento, setVencimento] = useState(String(editar?.diaVencimento ?? 5));
  const [limiteProprioTxt, setLimiteProprioTxt] = useState(editar?.limiteProprio ? valorParaCampo(editar.limiteProprio) : "");
  const [contaId, setContaId] = useState(String(editar?.contaId ?? ds.contas[0]?.id ?? ""));
  const [erro, setErro] = useState<string | null>(null);

  const virtual = editar ? principalId !== "" : forma === "virtual";
  const principal = opcoesFisicos.find((c) => c.id === Number(principalId));
  // Virtual: campos herdados do físico, somente leitura.
  const vTipo = virtual && principal ? principal.tipo : tipo;
  const vLimite = virtual && principal ? valorParaCampo(principal.limiteTotal) : limiteTxt;
  const vFecha = virtual && principal ? String(principal.diaFechamento) : fechamento;
  const vVence = virtual && principal ? String(principal.diaVencimento) : vencimento;
  const vConta = virtual && principal ? String(principal.contaId) : contaId;

  function enviar(e: FormEvent) {
    e.preventDefault();
    let r;
    const lpTxt = limiteProprioTxt.trim();
    const limiteProprio = lpTxt ? parseValorBR(lpTxt) : null;
    if (limiteProprio !== null && !(limiteProprio > 0)) return setErro("O limite próprio deve ser maior que zero e não pode passar do limite total do cartão físico.");
    if (virtual) {
      if (!principal) return setErro("Selecione o cartão físico.");
      const dados = { nome, finalCartao: final.trim(), tipo: principal.tipo, limiteTotal: principal.limiteTotal, diaFechamento: principal.diaFechamento, diaVencimento: principal.diaVencimento, contaId: principal.contaId };
      r = editar ? acoes.editarCartao(editar.id, { nome, finalCartao: final.trim(), limiteProprio, cartaoPrincipalId: principal.id }) : acoes.criarCartao({ ...dados, limiteProprio, cartaoPrincipalId: principal.id });
    } else {
      const limite = parseValorBR(limiteTxt);
      if (!(limite >= 0)) return setErro("Informe o limite total.");
      const dados = { nome, finalCartao: final.trim(), tipo, limiteTotal: limite, diaFechamento: Number(fechamento), diaVencimento: Number(vencimento), contaId: Number(contaId), limiteProprio };
      r = editar ? acoes.editarCartao(editar.id, { ...dados, cartaoPrincipalId: null }) : acoes.criarCartao(dados);
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
      {editar && (
        <>
          <Select rotulo="Compartilha o saldo do cartão físico" value={principalId} onChange={(e) => setPrincipalId(e.target.value)} disabled={temVirtuais}>
            <option value="">Nenhum (cartão físico independente)</option>
            {orfao && editar.cartaoPrincipalId != null && <option value={String(editar.cartaoPrincipalId)}>Cartão físico não encontrado — escolha um</option>}
            {opcoesFisicos.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nome} (final {c.finalCartao || "—"})
              </option>
            ))}
          </Select>
          {temVirtuais && <p className="text-xs text-muted">Este cartão físico tem cartões virtuais; mova ou exclua-os antes de torná-lo virtual.</p>}
          {virtual && <p className="text-xs text-muted">Compartilha limite, conta, fechamento, vencimento e fatura com o cartão físico escolhido; o físico é a base do saldo.</p>}
        </>
      )}
      {!editar && virtual && (
        <>
          <Select rotulo="Compartilha o saldo do cartão físico" value={principalId} onChange={(e) => setPrincipalId(e.target.value)} required>
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
      {!virtual && editar && temVirtuais && (
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
      <InputValor rotulo="Limite próprio (opcional)" value={limiteProprioTxt} onChange={(e) => setLimiteProprioTxt(e.target.value)} />
      <p className="-mt-2 text-xs text-muted">Teto de gasto deste cartão dentro do limite total compartilhado. Deixe vazio para não limitar.</p>
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
  /** Filtro da lista de lançamentos: null = todos os cartões do grupo. */
  const [filtroId, setFiltroId] = useState<number | null>(null);

  /** Seleciona o grupo, opcionalmente filtra por um cartão dele e leva a tela até a fatura. */
  function selecionar(c: Cartao, filtro: number | null = null) {
    setSelecionadoId(c.id);
    setFatura(null);
    setFiltroId(filtro);
    requestAnimationFrame(() => {
      const reduz = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      document.getElementById("t-fatura")?.scrollIntoView({ behavior: reduz ? "auto" : "smooth", block: "start" });
    });
  }

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
  // Filtro só vale se o cartão ainda pertence ao grupo selecionado.
  const filtro = filtroId !== null && grupo.some((k) => k.id === filtroId) ? filtroId : null;
  const itensVisiveis = resumo ? (filtro === null ? resumo.itens : resumo.itens.filter((d) => cartaoIdDe(d) === filtro)) : [];

  // R42b: compras no débito (saem direto da conta) do mês exibido; não entram em fatura/limite/pagamento.
  const debitos = useMemo(
    () => (periodo ? debitosDoCartao(grupo, ds.despesas, periodo.mes, periodo.ano, filtro) : []),
    [grupo, ds.despesas, periodo, filtro],
  );

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
                <li
                  key={c.id}
                  className="cursor-pointer"
                  onClick={(e) => {
                    // O cartão inteiro seleciona; botões internos (editar/excluir/linhas) cuidam do próprio clique.
                    if ((e.target as HTMLElement).closest("button, a, input, select, [role=button]")) return;
                    selecionar(c);
                  }}
                >
                  <Card className={ativo ? "border-primary ring-2 ring-primary" : "hover:bg-surface-2"}>
                    <div className="flex items-start gap-2">
                      <button type="button" onClick={() => selecionar(c)} aria-pressed={ativo} className="min-w-0 flex-1 rounded-lg text-left">
                        {ativo && (
                          <span className="mb-1 flex items-center gap-1 text-xs font-medium text-primary">
                            <CircleCheck size={14} aria-hidden /> Fatura exibida abaixo
                          </span>
                        )}
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
                      <span className="text-muted">{virtuais.length > 0 ? "Limite compartilhado · disponível" : "Disponível"}</span>
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
                    <LimiteComprometido nome={c.nome} c={comprometimentoCartao(c, ds.cartoes, ds.despesas, agora)} />
                    <ul className="mt-3 divide-y divide-line border-t border-line" aria-label={`Saldo por cartão de ${c.nome}`}>
                      {[c, ...virtuais].map((k) => {
                        const sc = saldoDoCartao(k, ds.cartoes, ds.despesas);
                        const virt = k.cartaoPrincipalId != null;
                        return (
                          <li key={k.id} className="py-2">
                            <div className="flex items-center gap-2">
                              <div
                                role="button"
                                tabIndex={0}
                                onClick={() => selecionar(c, virtuais.length > 0 ? k.id : null)}
                                onKeyDown={(e) => {
                                  if (e.key === "Enter" || e.key === " ") {
                                    e.preventDefault();
                                    selecionar(c, virtuais.length > 0 ? k.id : null);
                                  }
                                }}
                                aria-label={`Ver lançamentos de ${k.nome}`}
                                aria-pressed={ativo && filtroId === (virtuais.length > 0 ? k.id : null)}
                                className="min-w-0 flex-1 rounded-lg"
                              >
                                <p className="flex items-center gap-2 truncate text-sm font-medium">
                                  {k.nome} {virt && <Badge tom="primary">VIRTUAL</Badge>}
                                </p>
                                {virt && <p className="truncate text-xs text-muted">Compartilha o saldo de {ds.cartoes.find((x) => x.id === k.cartaoPrincipalId)?.nome ?? "cartão físico não encontrado"}</p>}
                                <p className="truncate text-xs text-muted">
                                  Final {k.finalCartao || "—"}
                                  {sc.limiteProprio != null && (
                                    <>
                                      {" "}· Limite próprio <Money valor={sc.limiteProprio} />
                                    </>
                                  )}
                                </p>
                              </div>
                              {virt && (
                                <>
                                  <IconButton rotulo={`Editar cartão virtual ${k.nome}`} onClick={() => setFormCartao({ editar: k })}>
                                    <Pencil size={16} aria-hidden />
                                  </IconButton>
                                  <IconButton rotulo={`Excluir cartão virtual ${k.nome}`} onClick={() => setExcluir(k)}>
                                    <Trash2 size={16} aria-hidden />
                                  </IconButton>
                                </>
                              )}
                            </div>
                            <div className="mt-1">
                              <ProgressBar razao={sc.razao} tom={sc.razao >= 1 ? "neg" : sc.razao >= 0.8 ? "warn" : "primary"} rotulo={`Uso próprio do cartão ${k.nome}`} />
                            </div>
                            <p className="mt-1 flex justify-between text-xs text-muted">
                              <span>
                                Usado <Money valor={sc.usado} /> ({formatPercentual(sc.razao)})
                              </span>
                              <span>
                                Disponível <Money valor={sc.disponivel} />
                              </span>
                            </p>
                          </li>
                        );
                      })}
                    </ul>
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
                <Button variante="primary" disabled={comprometimentoCartao(selecionado, ds.cartoes, ds.despesas, agora).emAbertoTotal <= 0} onClick={() => setPagar(true)}>
                  Pagar fatura
                </Button>
                {conta && <span className="text-xs text-muted">Débito na conta {conta.banco} · {conta.conta}</span>}
              </div>
              {grupo.length > 1 && (
                <div role="group" aria-label="Filtrar lançamentos por cartão" className="mt-4 flex flex-wrap gap-2">
                  {[{ id: null as number | null, nome: "Todos os cartões" }, ...grupo.map((k) => ({ id: k.id as number | null, nome: k.nome }))].map((o) => (
                    <button
                      key={o.id ?? "todos"}
                      type="button"
                      aria-pressed={filtro === o.id}
                      onClick={() => setFiltroId(o.id)}
                      className={`rounded-full border px-3 py-1 text-xs font-medium ${filtro === o.id ? "border-primary bg-primary-soft text-primary" : "border-line text-muted hover:bg-surface-2"}`}
                    >
                      {o.nome}
                    </button>
                  ))}
                </div>
              )}
              <div className="mt-4">
                {itensVisiveis.length === 0 ? (
                  <p className="py-6 text-center text-sm text-muted">{resumo.itens.length === 0 ? "Nenhuma compra nesta fatura." : "Nenhuma compra deste cartão nesta fatura."}</p>
                ) : (
                  <ListaLancamentos itens={itensVisiveis} semOrigem={grupo.length <= 1} />
                )}
              </div>
              <section className="mt-6 border-t border-line pt-4" aria-labelledby="t-debitos">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 id="t-debitos" className="flex items-center gap-2 text-sm font-semibold">
                    <Landmark size={16} aria-hidden /> Compras no débito (saem direto da conta)
                  </h3>
                  <p className="text-sm text-muted">
                    Subtotal <Money valor={totalDebitos(debitos)} className="font-semibold text-fg" />
                  </p>
                </div>
                <p className="mt-1 text-xs text-muted">Em {String(periodo.mes).padStart(2, "0")}/{periodo.ano}, pela data da compra. Não entram no total da fatura, no limite nem em &quot;Pagar fatura&quot;.</p>
                <div className="mt-2">
                  {debitos.length === 0 ? (
                    <p className="py-4 text-center text-sm text-muted">Nenhuma compra no débito neste mês.</p>
                  ) : (
                    <ListaLancamentos itens={debitos} semOrigem={false} />
                  )}
                </div>
              </section>
            </Card>
          )}
        </>
      )}

      <Modal aberto={formCartao !== null} onFechar={() => setFormCartao(null)} titulo={formCartao?.editar ? "Editar cartão" : "Novo cartão"}>
        {formCartao && <CartaoForm editar={formCartao.editar} onFechar={() => setFormCartao(null)} />}
      </Modal>
      {selecionado && periodo && (
        <PagarFaturaModal aberto={pagar} onFechar={() => setPagar(false)} cartao={selecionado} cartoes={ds.cartoes} despesas={ds.despesas} conta={conta} periodo={periodo} />
      )}
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
      <LancamentoModal aberto={compra} onFechar={() => setCompra(false)} predefinicao={{ entrada: "cartao", cartaoId: filtro ?? selecionado?.id }} />
    </>
  );
}
