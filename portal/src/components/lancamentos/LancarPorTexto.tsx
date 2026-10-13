"use client";

import { Mic, Sparkles } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { categoriasDisponiveis } from "@/lib/catalogo";
import { dataParaMs, interpretarLancamento, type Confianca, type Interpretacao } from "@/lib/finance/lancamentoTexto";
import { valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import type { PredefinicaoLancamento } from "./LancamentoModal";

/** Mínimo da Web Speech API que usamos (não está nos tipos do TypeScript). */
interface Reconhecedor {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
}
type FabricaReconhecedor = new () => Reconhecedor;

function fabricaDeReconhecimento(): FabricaReconhecedor | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as { SpeechRecognition?: FabricaReconhecedor; webkitSpeechRecognition?: FabricaReconhecedor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

const COR: Record<Confianca, string> = { ALTA: "text-pos", MEDIA: "text-warn", BAIXA: "text-muted" };
const ROTULO_CONF: Record<Confianca, string> = { ALTA: "certo", MEDIA: "confira", BAIXA: "assumido" };

/** Converte a interpretação nos campos iniciais do formulário de novo lançamento. */
export function predefinicaoDe(i: Interpretacao): PredefinicaoLancamento {
  const p: PredefinicaoLancamento = {
    entrada: i.destino?.tipo === "CARTAO" ? "cartao" : i.tipo === "CREDITO" ? "receita" : "despesa",
    descricao: i.descricao,
    data: dataParaMs(i.data),
  };
  if (i.valorCentavos !== null) p.valorTxt = valorParaCampo(i.valorCentavos / 100);
  if (i.categoria) p.categoria = i.categoria;
  if (i.parcelas) p.parcelas = i.parcelas;
  if (i.destino?.tipo === "CONTA") p.conta = i.destino.id;
  if (i.destino?.tipo === "CARTAO") p.cartaoId = Number(i.destino.id);
  return p;
}

function Formulario({ onFechar, onPreencher }: { onFechar: () => void; onPreencher: (p: PredefinicaoLancamento) => void }) {
  const ds = useDataset();
  const agora = useAgora();
  const [texto, setTexto] = useState("");
  const [ouvindo, setOuvindo] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const rec = useRef<Reconhecedor | null>(null);
  const suporta = useMemo(() => fabricaDeReconhecimento() !== null, []);
  const categorias = useMemo(() => categoriasDisponiveis(ds.categorias).map((c) => c.nome), [ds.categorias]);

  const resultado = useMemo(
    () =>
      texto.trim()
        ? interpretarLancamento(texto, {
            contas: ds.contas.map((c) => ({ id: c.conta, nome: c.banco })),
            cartoes: ds.cartoes.map((c) => ({ id: c.id, nome: c.nome })),
            categorias,
            regras: ds.regrasCategoria,
            agora,
          })
        : null,
    [texto, ds.contas, ds.cartoes, ds.regrasCategoria, categorias, agora],
  );

  useEffect(() => () => rec.current?.stop(), []);

  function ouvir() {
    const F = fabricaDeReconhecimento();
    if (!F) return;
    setAviso(null);
    try {
      const r = new F();
      r.lang = "pt-BR";
      r.interimResults = false;
      r.maxAlternatives = 1;
      r.onresult = (e) => {
        const t = e.results[0]?.[0]?.transcript;
        if (t) setTexto(t);
      };
      r.onerror = () => setAviso("Não consegui ouvir (microfone bloqueado ou indisponível). Digite a frase.");
      r.onend = () => setOuvindo(false);
      rec.current = r;
      setOuvindo(true);
      r.start();
    } catch {
      setOuvindo(false);
      setAviso("Não consegui usar o microfone. Digite a frase.");
    }
  }

  const campos: { rotulo: string; valor: string; conf: Confianca }[] = resultado
    ? [
        { rotulo: "Tipo", valor: resultado.tipo === "CREDITO" ? "Receita" : "Despesa", conf: resultado.confianca.tipo },
        { rotulo: "Valor", valor: resultado.valorCentavos === null ? "não entendi" : `R$ ${valorParaCampo(resultado.valorCentavos / 100)}${resultado.parcelas ? ` em ${resultado.parcelas}x` : ""}`, conf: resultado.confianca.valor },
        { rotulo: "Descrição", valor: resultado.descricao || "faltou", conf: resultado.confianca.descricao },
        { rotulo: "Data", valor: `${String(resultado.data.dia).padStart(2, "0")}/${String(resultado.data.mes).padStart(2, "0")}/${resultado.data.ano}`, conf: resultado.confianca.data },
        { rotulo: "Conta/cartão", valor: resultado.destino ? `${resultado.destino.nome} (${resultado.destino.tipo === "CARTAO" ? "cartão" : "conta"})` : resultado.candidatos.length > 0 ? "escolher no formulário" : "não informado", conf: resultado.confianca.destino },
        { rotulo: "Categoria", valor: resultado.categoria ?? "não identificada", conf: resultado.confianca.categoria },
      ]
    : [];

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted">
        Descreva em uma frase, como: <em>gastei 45 no mercado ontem no Nubank</em>, <em>recebi 3.500 de salário hoje</em>, <em>parcelei 600 em 3x no cartão</em>. Nada é salvo agora: o formulário abre preenchido para você revisar.
      </p>
      <div className="flex gap-2">
        <textarea
          aria-label="Descreva o lançamento em uma frase"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          rows={2}
          maxLength={300}
          autoFocus
          className="min-h-16 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm"
          placeholder="Ex.: uber 18,90 anteontem no cartão itaú"
        />
        {suporta && (
          <Button onClick={ouvindo ? () => rec.current?.stop() : ouvir} aria-label={ouvindo ? "Parar de ouvir" : "Falar a frase"} aria-pressed={ouvindo} icone={<Mic size={16} aria-hidden />} variante={ouvindo ? "primary" : "secondary"}>
            {ouvindo ? "Ouvindo…" : "Falar"}
          </Button>
        )}
      </div>
      {aviso && <ErroBox>{aviso}</ErroBox>}
      {resultado && (
        <div aria-live="polite" className="rounded-xl border border-line bg-surface-2 p-3">
          <p className="mb-2 text-xs font-semibold text-muted">O que entendi</p>
          <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
            {campos.map((c) => (
              <div key={c.rotulo} className="contents">
                <dt className="text-muted">{c.rotulo}</dt>
                <dd>
                  {c.valor} <span className={`text-xs ${COR[c.conf]}`}>({ROTULO_CONF[c.conf]})</span>
                </dd>
              </div>
            ))}
          </dl>
          {resultado.pendencias.length > 0 && (
            <ul className="mt-3 list-disc pl-5 text-sm text-warn">
              {resultado.pendencias.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar}>Cancelar</Button>
        <Button variante="primary" disabled={!resultado} icone={<Sparkles size={16} aria-hidden />} onClick={() => resultado && onPreencher(predefinicaoDe(resultado))}>
          Abrir formulário preenchido
        </Button>
      </div>
    </div>
  );
}

/** Modal "Lançar por texto": interpreta a frase e entrega a predefinição para o formulário (nunca salva sozinho). */
export function LancarPorTexto({ aberto, onFechar, onPreencher }: { aberto: boolean; onFechar: () => void; onPreencher: (p: PredefinicaoLancamento) => void }) {
  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo="Lançar por texto">
      {aberto && <Formulario onFechar={onFechar} onPreencher={onPreencher} />}
    </Modal>
  );
}
