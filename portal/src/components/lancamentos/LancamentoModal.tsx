"use client";

import { useMemo, useState } from "react";
import { SeletorCategoria } from "@/components/ui/SeletorCategoria";
import { picDaCategoria } from "@/lib/catalogo";
import type { FormEvent } from "react";
import { Checkbox, Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { TagsInput } from "@/components/ui/TagsInput";
import { AnexosCampo } from "./AnexosCampo";
import { normalizarAnexos } from "@/lib/finance/anexos";
import { sincronizarAnexos, type Pendente } from "@/lib/firebase/anexos";
import { useSession } from "@/lib/session";
import { aplicarRegras, sugerirRegraAprendida, type SugestaoRegra } from "@/lib/finance/regras";
import { Modal, RodapeForm } from "@/components/ui/Modal";
import { MOEDAS } from "@/lib/catalogo";
import { cartaoDeDebito, cartaoIdDe, saldoDoCartao } from "@/lib/finance/calc";
import { sugerirCategoria } from "@/lib/finance/analises";
import { normalizar } from "@/lib/finance/texto";
import { CATEGORIA_CASHBACK, ehCashbackDoCartao, MAX_PARCELAS, modalidadeDaCompra, type Modalidade } from "@/lib/finance/operations";
import { Button } from "@/components/ui/Button";
import { round2 } from "@/lib/finance/money";
import type { Despesa, Tipo } from "@/lib/finance/types";
import { deInputData, paraInputData, parseValorBR, valorParaCampo } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

type Entrada = "receita" | "despesa" | "cartao";
type Modo = "unica" | "parcelada" | "fixa";

export interface PredefinicaoLancamento {
  entrada?: Entrada;
  cartaoId?: number;
  conta?: string;
  /** R62 - campos vindos de "Lançar por texto" (o usuário revisa antes de salvar). */
  descricao?: string;
  valorTxt?: string;
  data?: number;
  categoria?: string;
  parcelas?: number;
}

interface Props {
  aberto: boolean;
  onFechar: () => void;
  editar?: Despesa | null;
  predefinicao?: PredefinicaoLancamento;
}

/** Formulário de lançamento. O conteúdo é montado só quando o modal abre (estado inicial limpo). */
export function LancamentoModal(props: Props) {
  return (
    <Modal aberto={props.aberto} onFechar={props.onFechar} titulo={props.editar ? "Editar lançamento" : "Novo lançamento"}>
      {props.aberto && <Formulario {...props} />}
    </Modal>
  );
}

/** Último instante de hoje (ms): lançamento com data até aqui já pode ser tratado como ocorrido. */
function fimDoDiaHoje(): number {
  const d = new Date();
  d.setHours(23, 59, 59, 999);
  return d.getTime();
}

function Formulario({ onFechar, editar, predefinicao }: Props) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const contaPadrao = predefinicao?.conta ?? editar?.conta ?? ds.contas[0]?.conta ?? "";

  const [entrada, setEntrada] = useState<Entrada>(
    editar ? (cartaoIdDe(editar) !== null ? "cartao" : editar.tipo === "CREDITO" ? "receita" : "despesa") : (predefinicao?.entrada ?? "despesa"),
  );
  const [modo, setModo] = useState<Modo>(!editar && predefinicao?.parcelas ? "parcelada" : "unica");
  const [operacao, setOperacao] = useState<"compra" | "estorno" | "cashback">(
    editar && cartaoIdDe(editar) !== null && editar.tipo === "CREDITO" ? (ehCashbackDoCartao(editar) ? "cashback" : "estorno") : "compra",
  );
  const estorno = operacao !== "compra";
  const cashback = operacao === "cashback";
  const [descricao, setDescricao] = useState(editar?.descricao ?? predefinicao?.descricao ?? "");
  const [moeda, setMoeda] = useState(editar?.moedaOriginal ?? "BRL");
  const [valorTxt, setValorTxt] = useState(
    editar ? valorParaCampo(editar.moedaOriginal !== "BRL" ? editar.valorOriginal : editar.valor) : (predefinicao?.valorTxt ?? ""),
  );
  const [cotacaoTxt, setCotacaoTxt] = useState(editar && editar.moedaOriginal !== "BRL" ? valorParaCampo(editar.cotacaoNaData) : "");
  const [data, setData] = useState(() => paraInputData(editar?.data ?? predefinicao?.data ?? Date.now()));
  const [categoria, setCategoria] = useState(editar?.categoria ?? predefinicao?.categoria ?? "");
  // R55 - a regra só preenche a categoria enquanto o usuário não escolheu uma manualmente (nunca sobrescreve).
  const [categoriaManual, setCategoriaManual] = useState(!editar && !!predefinicao?.categoria);
  const [tags, setTags] = useState<string[]>(editar?.tags ?? []);
  const [oferta, setOferta] = useState<SugestaoRegra | null>(null);
  const [conta, setConta] = useState(contaPadrao);
  const [cartaoId, setCartaoId] = useState(String(predefinicao?.cartaoId ?? (editar ? cartaoIdDe(editar) : null) ?? ds.cartoes[0]?.id ?? ""));
  // Lançamento novo: "já paga" acompanha a data (data futura = agendado/pendente) até o usuário mexer na caixa.
  const [pago, setPago] = useState(editar?.pago ?? (!predefinicao?.data || predefinicao.data <= fimDoDiaHoje()));
  const [pagoManual, setPagoManual] = useState(false);
  const [parcelasTxt, setParcelasTxt] = useState(String(predefinicao?.parcelas ?? 2));
  const [parcelaAtualTxt, setParcelaAtualTxt] = useState("1");
  const [modalidade, setModalidade] = useState<Modalidade>("CREDITO");
  const [erro, setErro] = useState<string | null>(null);
  // R60 - anexos: arquivos escolhidos (ainda não enviados), ids a remover e o lançamento já salvo (para "Tentar de novo").
  const nuvem = useSession((s) => s.modo === "firebase" && s.user !== null);
  const [pendentes, setPendentes] = useState<Pendente[]>([]);
  const [removidos, setRemovidos] = useState<string[]>([]);
  const [manterVinculo, setManterVinculo] = useState(true);
  const [salvoId, setSalvoId] = useState<number | null>(editar?.id ?? null);
  const [enviandoAnexos, setEnviandoAnexos] = useState(false);
  const existentes = normalizarAnexos(ds.despesas.find((d) => d.id === (salvoId ?? -1))?.anexos);

  const noCartao = entrada === "cartao";
  const tipoAtual: Tipo = entrada === "receita" || (noCartao && estorno) ? "CREDITO" : "DEBITO";
  const contaRegra = noCartao ? ds.contas.find((c) => c.id === ds.cartoes.find((x) => x.id === Number(cartaoId))?.contaId)?.conta : conta;
  const regra = useMemo(
    () => (editar ? null : aplicarRegras(ds.regrasCategoria, { descricao, conta: contaRegra, tipo: tipoAtual })),
    [editar, ds.regrasCategoria, descricao, contaRegra, tipoAtual],
  );
  const categoriaEf = !categoriaManual && regra ? regra.categoria.trim() : categoria;
  const sugestaoHeuristica = useMemo(() => (editar ? null : sugerirCategoria(descricao, ds.despesas)), [descricao, ds.despesas, editar]);
  // regra explícita tem prioridade sobre a sugestão heurística (R21)
  const sugestao = regra ? regra.categoria.trim() : sugestaoHeuristica;
  const estrangeira = moeda !== "BRL";
  // R42 - modalidade efetiva da compra: débito sai direto da conta (sem limite, fatura ou parcelas).
  const cartaoSel = noCartao ? ds.cartoes.find((x) => x.id === Number(cartaoId)) : undefined;
  const multiplo = !!cartaoSel && modalidadeDaCompra(cartaoSel, "DEBITO") === "DEBITO" && modalidadeDaCompra(cartaoSel, "CREDITO") === "CREDITO";
  const noDebito = cartaoSel ? modalidadeDaCompra(cartaoSel, modalidade) === "DEBITO" : false;
  const idDebito = editar ? cartaoDeDebito(editar) : null;
  const vinculoDebito = idDebito !== null ? ds.cartoes.find((x) => x.id === idDebito) : undefined;
  // R41 - aviso (não bloqueia) quando a compra ultrapassaria o limite próprio do cartão.
  const avisoLimiteProprio = (() => {
    if (!noCartao || estorno || editar || modo !== "unica" || noDebito) return null;
    const c = ds.cartoes.find((x) => x.id === Number(cartaoId));
    if (!c || !c.limiteProprio) return null;
    const base = parseValorBR(valorTxt);
    const valor = estrangeira ? base * parseValorBR(cotacaoTxt) : base;
    if (!(valor > 0)) return null;
    const { usado } = saldoDoCartao(c, ds.cartoes, ds.despesas);
    return usado + valor > c.limiteProprio ? `Esta compra ultrapassa o limite próprio de ${c.nome} (R$ ${c.limiteProprio.toFixed(2).replace(".", ",")}).` : null;
  })();

  /** Envia os anexos do lançamento já salvo. O lançamento nunca se perde: se algo falhar, o formulário fica aberto com "Tentar de novo". */
  async function concluirAnexos(id: number, seguinte: () => void) {
    setSalvoId(id);
    if (pendentes.length === 0 && removidos.length === 0) return seguinte();
    setEnviandoAnexos(true);
    const r = await sincronizarAnexos(id, pendentes, removidos, (chave, parcial) =>
      setPendentes((l) => l.map((p) => (p.chave === chave ? { ...p, ...parcial } : p))),
    );
    setEnviandoAnexos(false);
    setPendentes((l) => l.filter((p) => !r.enviados.includes(p.chave)));
    setRemovidos([]);
    if (r.falhas > 0) {
      avisar("erro", "Lançamento salvo, mas nem todos os anexos foram enviados. Use \"Tentar de novo\".");
      return;
    }
    seguinte();
  }

  function tentarAnexosDeNovo() {
    if (salvoId !== null) void concluirAnexos(salvoId, onFechar);
  }

  function submeter(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    if (enviandoAnexos) return;
    // lançamento já salvo e só restaram anexos com erro: reenvia em vez de gravar de novo
    if (salvoId !== null && !editar) return tentarAnexosDeNovo();
    const base = parseValorBR(valorTxt);
    if (!(base > 0)) return setErro("Informe um valor maior que zero.");
    let valor = base;
    let valorOriginal: number | undefined;
    let cotacao: number | undefined;
    if (estrangeira) {
      cotacao = parseValorBR(cotacaoTxt);
      if (!(cotacao > 0)) return setErro("Informe a cotação (R$ por unidade da moeda).");
      valorOriginal = base;
      valor = round2(base * cotacao);
    }
    const timestamp = deInputData(data);
    if (Number.isNaN(timestamp)) return setErro("Informe uma data válida.");
    if (!descricao.trim()) return setErro("Informe a descrição.");
    if (!categoriaEf.trim()) return setErro("Informe a categoria.");
    if (cashback && noDebito) return setErro("Cashback só vale em compra no crédito: ele abate a fatura.");
    const tipo: Tipo = tipoAtual;
    const picCategoria = picDaCategoria(categoriaEf, ds.categorias) ?? "";
    const moedaOriginal = moeda;

    if (editar) {
      const r = acoes.editLancamento(editar.id, {
        descricao,
        valor,
        data: timestamp,
        categoria: categoriaEf,
        tipo,
        ...(noCartao ? { cartaoId: Number(cartaoId), modalidade } : { cartaoId: null, conta, pago, desvincularDebito: !manterVinculo }),
        valorOriginal: valorOriginal ?? valor,
        moedaOriginal,
        cotacaoNaData: cotacao ?? 1,
        tags,
      });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Lançamento atualizado.");
      return void concluirAnexos(editar.id, () => {
        // R55 - aprendizado leve: trocou a categoria e já há 2+ lançamentos iguais com ela? Oferece (não cria sozinho).
        if (normalizar(categoriaEf) !== normalizar(editar.categoria)) {
          const atual = useStore.getState().ds;
          const novo = atual.despesas.find((x) => x.id === editar.id);
          const s = novo ? sugerirRegraAprendida(atual.despesas, atual.regrasCategoria, novo) : null;
          if (s) return setOferta(s);
        }
        onFechar();
      });
    }

    if (modo === "fixa" && !noCartao) {
      const r = acoes.criarDespesaFixa({
        descricao,
        valor,
        conta,
        categoria: categoriaEf,
        pic: picCategoria,
        tipo,
        diaVencimento: new Date(timestamp).getDate(),
      });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Recorrência criada.");
      return onFechar();
    }

    const parcelada = modo === "parcelada" && !noDebito;
    const parcelas = parcelada ? Number(parcelasTxt) : 1;
    if (parcelada && (!Number.isInteger(parcelas) || parcelas < 2 || parcelas > MAX_PARCELAS)) {
      return setErro(`Informe o número de parcelas (2 a ${MAX_PARCELAS}).`);
    }
    const parcelaAtual = parcelada && noCartao ? Number(parcelaAtualTxt) : 1;
    if (parcelada && noCartao && (!Number.isInteger(parcelaAtual) || parcelaAtual < 1 || parcelaAtual > parcelas)) {
      return setErro(`A parcela atual deve estar entre 1 e ${parcelas}.`);
    }
    const r = acoes.addLancamento({
      descricao,
      valor,
      data: timestamp,
      categoria: categoriaEf,
      pic: picCategoria,
      tipo,
      tags,
      ...(noCartao ? { cartaoId: Number(cartaoId), modalidade } : { conta, pago }),
      valorOriginal,
      moedaOriginal,
      cotacaoNaData: cotacao,
      parcelas,
      ...(parcelaAtual > 1 ? { parcelaAtual } : {}),
    });
    if (!r.ok) return setErro(r.erro);
    const lancadas = parcelas - parcelaAtual + 1;
    avisar("sucesso", parcelas > 1 ? `${lancadas} ${lancadas === 1 ? "parcela lançada" : "parcelas lançadas"}.` : "Lançamento salvo.");
    // R60 - anexos só em lançamento único (parcelas/recorrências não recebem anexos; anexe editando cada lançamento).
    if (parcelas === 1 && r.criados[0]) return void concluirAnexos(r.criados[0].id, onFechar);
    onFechar();
  }

  const semContas = ds.contas.length === 0;

  if (oferta) {
    return (
      <div className="flex flex-col gap-4" role="status">
        <p className="text-sm">
          Criar regra: <strong>{oferta.padrao}</strong> → <strong>{oferta.categoria}</strong>?
        </p>
        <p className="text-xs text-muted">Você já usou esta categoria para essa descrição {oferta.vezes} vezes. A regra vale para lançamentos novos e pode ser editada em Regras.</p>
        <div className="flex justify-end gap-2">
          <Button onClick={onFechar}>Agora não</Button>
          <Button
            variante="primary"
            onClick={() => {
              acoes.criarRegraAprendida(oferta);
              avisar("sucesso", "Regra criada.");
              onFechar();
            }}
          >
            Criar regra
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={submeter} className="flex flex-col gap-4" noValidate>
      {semContas && <ErroBox>Cadastre uma conta antes de criar lançamentos.</ErroBox>}
      {editar && editar.natureza === "NORMAL" && (
        <p className="text-xs text-muted">
          Você pode mudar onde o lançamento foi feito (conta ou cartão; no cartão, crédito ou débito). Saldos, limites e faturas são recalculados
          {editar.grupoId?.startsWith("parc:") ? "; em compra parcelada a mudança vale para todas as parcelas" : ""}.
        </p>
      )}
      {(!editar || editar.natureza === "NORMAL") && (
        <Segmentado
          rotulo={editar ? "Lançado em" : "Tipo de lançamento"}
          valor={entrada}
          onChange={(v) => {
            setEntrada(v);
            if (v === "cartao") setPago(false);
            else if (editar && entrada === "cartao") setPago(deInputData(data) <= Date.now());
            if (v === "cartao" && modo === "fixa") setModo("unica");
          }}
          opcoes={[
            { valor: "despesa", rotulo: "Despesa" },
            { valor: "receita", rotulo: "Receita" },
            { valor: "cartao", rotulo: "Cartão" },
          ]}
        />
      )}
      {!editar && (
        <Segmentado
          rotulo="Frequência"
          valor={noDebito && modo === "parcelada" ? "unica" : modo}
          onChange={setModo}
          opcoes={[
            { valor: "unica", rotulo: "Única" },
            ...(noDebito ? [] : [{ valor: "parcelada" as const, rotulo: "Parcelada" }]),
            ...(noCartao ? [] : [{ valor: "fixa" as const, rotulo: "Fixa (mensal)" }]),
          ]}
        />
      )}
      <Input rotulo="Descrição" value={descricao} onChange={(e) => setDescricao(e.target.value)} maxLength={120} required />
      <div className="grid grid-cols-2 gap-3">
        <Select rotulo="Moeda" value={moeda} onChange={(e) => setMoeda(e.target.value)}>
          {MOEDAS.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </Select>
        <InputValor rotulo={estrangeira ? `Valor em ${moeda}` : "Valor (R$)"} value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
      </div>
      {estrangeira && (
        <InputValor
          rotulo="Cotação (R$ por unidade)"
          value={cotacaoTxt}
          onChange={(e) => setCotacaoTxt(e.target.value)}
          dica={(() => {
            const v = parseValorBR(valorTxt);
            const c = parseValorBR(cotacaoTxt);
            return v > 0 && c > 0 ? `Valor lançado: R$ ${round2(v * c).toFixed(2).replace(".", ",")}` : "O valor lançado em reais = valor × cotação.";
          })()}
        />
      )}
      <div className="grid grid-cols-2 gap-3">
        <Input rotulo={modo === "fixa" ? "Primeiro vencimento" : "Data"} type="date" value={data} onChange={(e) => {
          setData(e.target.value);
          const ts = deInputData(e.target.value);
          if (!editar && !pagoManual && !Number.isNaN(ts)) setPago(ts <= fimDoDiaHoje());
        }} required />
        {modo === "parcelada" && !editar && !noDebito ? (
          <Input rotulo="Parcelas" type="number" min={2} max={MAX_PARCELAS} value={parcelasTxt} onChange={(e) => setParcelasTxt(e.target.value)} />
        ) : (
          <div />
        )}
      </div>
      {modo === "parcelada" && !editar && !noDebito && noCartao && !estorno && (
        <Input
          rotulo="Parcela atual"
          type="number"
          min={1}
          max={Number(parcelasTxt) || MAX_PARCELAS}
          value={parcelaAtualTxt}
          onChange={(e) => setParcelaAtualTxt(e.target.value)}
          dica="1 = compra nova. Para compra já em andamento, informe a parcela que está sendo lançada agora: serão criadas só dela até a última, a primeira na data informada."
        />
      )}
      <SeletorCategoria valor={categoriaEf} onChange={(v) => { setCategoria(v); setCategoriaManual(true); }} personalizadas={ds.categorias} />
      {sugestao && normalizar(sugestao) !== normalizar(categoriaEf) && (
        <button
          type="button"
          onClick={() => { setCategoria(sugestao); setCategoriaManual(true); }}
          className="-mt-2 self-start rounded-full border border-primary/40 bg-primary-soft px-3 py-1 text-xs font-medium text-primary"
        >
          {regra ? "Regra" : "Sugestão"} de categoria: {sugestao} (usar)
        </button>
      )}
      {noCartao ? (
        <div className="grid grid-cols-2 gap-3">
          <Select rotulo="Cartão" value={cartaoId} onChange={(e) => setCartaoId(e.target.value)} required>
            {ds.cartoes.length === 0 && <option value="">Nenhum cartão cadastrado</option>}
            {ds.cartoes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nome} (final {c.finalCartao}){c.cartaoPrincipalId != null ? " · virtual" : ""}
              </option>
            ))}
          </Select>
          <Select
            rotulo="Operação"
            value={operacao}
            onChange={(e) => {
              const v = e.target.value as typeof operacao;
              setOperacao(v);
              if (v === "cashback") {
                setCategoria(CATEGORIA_CASHBACK);
                setCategoriaManual(true);
                if (!descricao.trim()) setDescricao("Cashback");
                setModo("unica");
              }
            }}
          >
            <option value="compra">Compra</option>
            <option value="estorno">Estorno</option>
            <option value="cashback">Cashback (abate a fatura)</option>
          </Select>
        </div>
      ) : (
        <Select rotulo="Conta" value={conta} onChange={(e) => setConta(e.target.value)} required>
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
      )}
      {multiplo && (
        <Segmentado<Modalidade>
          rotulo="Modalidade da compra"
          valor={modalidade}
          onChange={setModalidade}
          opcoes={[
            { valor: "CREDITO", rotulo: "Crédito" },
            { valor: "DEBITO", rotulo: "Débito" },
          ]}
        />
      )}
      {noDebito && <p className="text-xs text-warn" role="status">Compra no débito: sai direto da conta e aparece na tela do cartão (fora da fatura e do limite)</p>}
      {!noCartao && vinculoDebito && (
        <div>
          <p className="text-xs text-muted" role="status">Compra no débito do cartão {vinculoDebito.nome}: sai da conta e aparece na tela do cartão.</p>
          <Checkbox rotulo={`Manter o vínculo com o cartão ${vinculoDebito.nome}`} checked={manterVinculo} onChange={(e) => setManterVinculo(e.target.checked)} />
        </div>
      )}
      {avisoLimiteProprio && <p className="text-xs text-warn" role="status">{avisoLimiteProprio}</p>}
      {!noCartao && modo === "unica" && (
        <div>
          <Checkbox rotulo={entrada === "receita" ? "Já recebida" : "Já paga"} checked={pago} onChange={(e) => { setPagoManual(true); setPago(e.target.checked); }} />
          {!pago && deInputData(data) > fimDoDiaHoje() && <p className="ml-6 text-xs text-muted">Data futura: fica agendado em Pendências e não altera o saldo até a baixa.</p>}
        </div>
      )}
      {modo === "parcelada" && !editar && !noDebito && <p className="text-xs text-muted">O valor informado é o total da compra; a última parcela recebe o resto dos centavos.{noCartao && Number(parcelaAtualTxt) > 1 ? " Com parcela atual maior que 1, só as parcelas restantes são lançadas (todas reservam o limite)." : ""}</p>}
      {modo === "fixa" && <p className="text-xs text-muted">Cria uma recorrência: lançada automaticamente todo mês no dia do vencimento (dias 29–31 caem no último dia dos meses curtos).</p>}
      <TagsInput valor={tags} onChange={setTags} despesas={ds.despesas} />
      {modo === "fixa" && tags.length > 0 && <p className="text-xs text-muted">As tags não são aplicadas às recorrências (só aos lançamentos criados diretamente).</p>}
      {(editar || modo === "unica") && (
        <AnexosCampo
          existentes={existentes}
          pendentes={pendentes}
          onPendentes={setPendentes}
          removidos={removidos}
          onRemover={(id) => setRemovidos((l) => [...l, id])}
          nuvem={nuvem}
          onTentarDeNovo={salvoId !== null ? tentarAnexosDeNovo : undefined}
          desabilitado={enviandoAnexos}
        />
      )}
      {!editar && modo !== "unica" && <p className="text-xs text-muted">Anexos estão disponíveis em lançamentos únicos (para parcelas e recorrências, anexe depois editando cada lançamento).</p>}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} enviando={enviandoAnexos} />
    </form>
  );
}
