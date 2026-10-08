"use client";

import { useMemo, useState } from "react";
import { SeletorCategoria } from "@/components/ui/SeletorCategoria";
import { picDaCategoria } from "@/lib/catalogo";
import type { FormEvent } from "react";
import { Checkbox, Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal, RodapeForm } from "@/components/ui/Modal";
import { MOEDAS } from "@/lib/catalogo";
import { cartaoIdDe } from "@/lib/finance/calc";
import { sugerirCategoria } from "@/lib/finance/analises";
import { normalizar } from "@/lib/finance/texto";
import { MAX_PARCELAS } from "@/lib/finance/operations";
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

function Formulario({ onFechar, editar, predefinicao }: Props) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const contaPadrao = predefinicao?.conta ?? editar?.conta ?? ds.contas[0]?.conta ?? "";

  const [entrada, setEntrada] = useState<Entrada>(
    editar ? (cartaoIdDe(editar) !== null ? "cartao" : editar.tipo === "CREDITO" ? "receita" : "despesa") : (predefinicao?.entrada ?? "despesa"),
  );
  const [modo, setModo] = useState<Modo>("unica");
  const [estorno, setEstorno] = useState(editar ? cartaoIdDe(editar) !== null && editar.tipo === "CREDITO" : false);
  const [descricao, setDescricao] = useState(editar?.descricao ?? "");
  const [moeda, setMoeda] = useState(editar?.moedaOriginal ?? "BRL");
  const [valorTxt, setValorTxt] = useState(
    editar ? valorParaCampo(editar.moedaOriginal !== "BRL" ? editar.valorOriginal : editar.valor) : "",
  );
  const [cotacaoTxt, setCotacaoTxt] = useState(editar && editar.moedaOriginal !== "BRL" ? valorParaCampo(editar.cotacaoNaData) : "");
  const [data, setData] = useState(() => paraInputData(editar?.data ?? Date.now()));
  const [categoria, setCategoria] = useState(editar?.categoria ?? "");
  const [conta, setConta] = useState(contaPadrao);
  const [cartaoId, setCartaoId] = useState(String(predefinicao?.cartaoId ?? (editar ? cartaoIdDe(editar) : null) ?? ds.cartoes[0]?.id ?? ""));
  const [pago, setPago] = useState(editar?.pago ?? true);
  const [parcelasTxt, setParcelasTxt] = useState("2");
  const [erro, setErro] = useState<string | null>(null);

  const sugestao = useMemo(() => (editar ? null : sugerirCategoria(descricao, ds.despesas)), [descricao, ds.despesas, editar]);
  const noCartao = entrada === "cartao";
  const estrangeira = moeda !== "BRL";

  function submeter(e: FormEvent) {
    e.preventDefault();
    setErro(null);
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
    if (!categoria.trim()) return setErro("Informe a categoria.");
    const tipo: Tipo = entrada === "receita" || (noCartao && estorno) ? "CREDITO" : "DEBITO";
    const picCategoria = picDaCategoria(categoria, ds.categorias) ?? "";
    const moedaOriginal = moeda;

    if (editar) {
      const r = acoes.editLancamento(editar.id, {
        descricao,
        valor,
        data: timestamp,
        categoria,
        tipo,
        ...(noCartao ? { cartaoId: Number(cartaoId) } : { cartaoId: null, conta, pago }),
        valorOriginal: valorOriginal ?? valor,
        moedaOriginal,
        cotacaoNaData: cotacao ?? 1,
      });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Lançamento atualizado.");
      return onFechar();
    }

    if (modo === "fixa" && !noCartao) {
      const r = acoes.criarDespesaFixa({
        descricao,
        valor,
        conta,
        categoria,
        pic: picCategoria,
        tipo,
        diaVencimento: new Date(timestamp).getDate(),
      });
      if (!r.ok) return setErro(r.erro);
      avisar("sucesso", "Recorrência criada.");
      return onFechar();
    }

    const parcelas = modo === "parcelada" ? Number(parcelasTxt) : 1;
    if (modo === "parcelada" && (!Number.isInteger(parcelas) || parcelas < 2 || parcelas > MAX_PARCELAS)) {
      return setErro(`Informe o número de parcelas (2 a ${MAX_PARCELAS}).`);
    }
    const r = acoes.addLancamento({
      descricao,
      valor,
      data: timestamp,
      categoria,
      pic: picCategoria,
      tipo,
      ...(noCartao ? { cartaoId: Number(cartaoId) } : { conta, pago }),
      valorOriginal,
      moedaOriginal,
      cotacaoNaData: cotacao,
      parcelas,
    });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", parcelas > 1 ? `${parcelas} parcelas lançadas.` : "Lançamento salvo.");
    onFechar();
  }

  const semContas = ds.contas.length === 0;

  return (
    <form onSubmit={submeter} className="flex flex-col gap-4" noValidate>
      {semContas && <ErroBox>Cadastre uma conta antes de criar lançamentos.</ErroBox>}
      {!editar && (
        <Segmentado
          rotulo="Tipo de lançamento"
          valor={entrada}
          onChange={(v) => {
            setEntrada(v);
            if (v === "cartao") setPago(false);
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
          valor={modo}
          onChange={setModo}
          opcoes={[
            { valor: "unica", rotulo: "Única" },
            { valor: "parcelada", rotulo: "Parcelada" },
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
        <Input rotulo={modo === "fixa" ? "Primeiro vencimento" : "Data"} type="date" value={data} onChange={(e) => setData(e.target.value)} required />
        {modo === "parcelada" && !editar ? (
          <Input rotulo="Parcelas" type="number" min={2} max={MAX_PARCELAS} value={parcelasTxt} onChange={(e) => setParcelasTxt(e.target.value)} />
        ) : (
          <div />
        )}
      </div>
      <SeletorCategoria valor={categoria} onChange={setCategoria} personalizadas={ds.categorias} />
      {sugestao && normalizar(sugestao) !== normalizar(categoria) && (
        <button
          type="button"
          onClick={() => setCategoria(sugestao)}
          className="-mt-2 self-start rounded-full border border-primary/40 bg-primary-soft px-3 py-1 text-xs font-medium text-primary"
        >
          Sugestão de categoria: {sugestao} (usar)
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
          <Select rotulo="Operação" value={estorno ? "estorno" : "compra"} onChange={(e) => setEstorno(e.target.value === "estorno")}>
            <option value="compra">Compra</option>
            <option value="estorno">Estorno</option>
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
      {!noCartao && modo === "unica" && <Checkbox rotulo={entrada === "receita" ? "Já recebida" : "Já paga"} checked={pago} onChange={(e) => setPago(e.target.checked)} />}
      {modo === "parcelada" && !editar && <p className="text-xs text-muted">O valor informado é o total; a última parcela recebe o resto dos centavos.</p>}
      {modo === "fixa" && <p className="text-xs text-muted">Cria uma recorrência: lançada automaticamente todo mês no dia do vencimento (dias 29–31 caem no último dia dos meses curtos).</p>}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}
