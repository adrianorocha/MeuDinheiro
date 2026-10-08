"use client";

import { CircleCheck, Undo2 } from "lucide-react";
import { useMemo, useState } from "react";
import { ConciliacaoLote, type PassoLote, type Pendencia } from "@/components/conciliacao/ConciliacaoLote";
import { DestinoResumo, rotuloDestino, sugerirDestino } from "@/components/conciliacao/DestinoResumo";
import { ConfirmarArquivo, lerArquivo, SeletorArquivos, type ArquivoLido } from "@/components/conciliacao/EnvioArquivo";
import { Revisao } from "@/components/conciliacao/Revisao";
import { Button } from "@/components/ui/Button";
import { Segmentado } from "@/components/ui/Field";
import { Card, ErroBox, PageHeader } from "@/components/ui/Misc";
import { casar, janelaPadrao, lancamentosDoDestino } from "@/lib/conciliacao/matching";
import { gerarOfxExemplo } from "@/lib/conciliacao/exemplo";
import { useUltimoLote } from "@/lib/conciliacao/lote";
import type { Plano } from "@/lib/conciliacao/plano";
import { csvRelatorio, resumoConciliacao } from "@/lib/conciliacao/relatorio";
import type { ArquivoExtrato, Destino } from "@/lib/conciliacao/tipos";
import { baixarArquivo } from "@/lib/download";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";

type Passo = 1 | 2 | 3 | 4;
const PASSOS = ["Arquivo", "Destino", "Revisão", "Resultado"];
const PASSOS_LOTE = ["Arquivos", "Opções", "Conciliando", "Resultado"];

function Progresso({ passo, passos = PASSOS }: { passo: number; passos?: string[] }) {
  return (
    <ol className="mb-4 flex flex-wrap gap-2" aria-label="Etapas">
      {passos.map((p, i) => (
        <li key={p} aria-current={passo === i + 1 ? "step" : undefined} className={`rounded-full px-3 py-1 text-xs font-medium ${passo === i + 1 ? "bg-primary text-primary-fg" : passo > i + 1 ? "bg-primary-soft text-primary" : "bg-surface-2 text-muted"}`}>
          {i + 1}. {p}
        </li>
      ))}
    </ol>
  );
}

export default function ConciliacaoPage() {
  const ds = useDataset();
  const agora = useAgora();
  const notificar = useNotificar();
  const lote = useUltimoLote((s) => s.ultimo);

  const [modo, setModo] = useState<"lote" | "um">("lote");
  const [passoLote, setPassoLote] = useState<PassoLote>(1);
  const [pend, setPend] = useState<Pendencia[]>([]);
  const [passo, setPasso] = useState<Passo>(1);
  const [fila, setFila] = useState<File[]>([]);
  const [lido, setLido] = useState<ArquivoLido | null>(null);
  const [extrato, setExtrato] = useState<ArquivoExtrato | null>(null);
  const [destino, setDestino] = useState<Destino | null>(null);
  const [janela, setJanela] = useState(3);
  const [tolerancia, setTolerancia] = useState(2);
  const [erro, setErro] = useState<string | null>(null);
  const [relatorio, setRelatorio] = useState<{ csv: string; nome: string; resumo: string } | null>(null);

  const resultado = useMemo(
    () => (extrato && destino ? casar(extrato.transacoes, lancamentosDoDestino(ds, destino), { janelaDias: janela, toleranciaPct: tolerancia / 100 }) : null),
    [extrato, destino, ds, janela, tolerancia],
  );

  async function proximoDaFila(arquivos: File[]) {
    setErro(null);
    const [primeiro, ...resto] = arquivos;
    if (!primeiro) {
      setLido(null);
      setExtrato(null);
      setPasso(1);
      return;
    }
    try {
      setLido(await lerArquivo(primeiro));
      setFila(resto);
      setExtrato(null);
      setPasso(1);
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível ler o arquivo.");
      await proximoDaFila(resto);
    }
  }

  function irParaUmPorUm(files: File[]) {
    setModo("um");
    setPend([]);
    void proximoDaFila(files);
  }

  function abrirPendencias(lista: Pendencia[]) {
    const [p, ...resto] = lista;
    if (!p) return;
    setPend(resto);
    setErro(null);
    setFila([]);
    setLido({ nome: `${p.nome} (pendências)`, texto: "" });
    setExtrato(p.extrato);
    setDestino(p.destino);
    setJanela(janelaPadrao(p.destino));
    setTolerancia(2);
    setPasso(3);
    setModo("um");
  }

  function aoPronto(a: ArquivoExtrato) {
    setExtrato(a);
    const sug = sugerirDestino(ds, a);
    setDestino(sug);
    setJanela(sug ? janelaPadrao(sug) : 3);
    setPasso(2);
  }

  function aoDestino(d: Destino) {
    setDestino(d);
    setJanela(janelaPadrao(d));
  }

  function experimentar() {
    const conta = ds.contas.find((c) => ds.despesas.some((d) => d.conta === c.conta && !d.cartaoId && d.natureza === "NORMAL")) ?? ds.contas[0];
    const texto = conta ? gerarOfxExemplo(ds, conta.conta, agora) : null;
    if (!texto) return notificar.erro("Sem lançamentos recentes para gerar o exemplo. Carregue os dados de exemplo em Configurações.");
    setLido({ nome: "extrato-exemplo-gerado.ofx", texto });
    setFila([]);
    setPasso(1);
  }

  function aplicar(plano: Plano, mapa: Map<number, { acao: string; lancamentoId?: number }>) {
    if (!resultado || !extrato || !destino) return;
    const r = acoes.aplicarConciliacao(plano);
    if (!r.ok) return setErro(r.erro);
    setErro(null);
    useUltimoLote.getState().definir({ inverso: r.inverso, resumo: r.resumo, rotuloDestino: rotuloDestino(ds, destino), em: agora });
    const resumo = resumoConciliacao(resultado, ds, destino, extrato);
    setRelatorio({
      csv: csvRelatorio(resultado, mapa),
      nome: `conciliacao-${(lido?.nome ?? "extrato").replace(/\.[^.]+$/, "")}.csv`,
      resumo: `${resumo.contagens.AUTOMATICO} automáticos, ${resumo.contagens.SUGERIDO} sugeridos, ${resumo.contagens.SO_NO_EXTRATO} só no extrato, ${resumo.contagens.SO_NO_APP} só no app, ${resumo.contagens.DUPLICADO} duplicados`,
    });
    setPasso(4);
  }

  function desfazer() {
    if (!lote) return;
    acoes.desfazerLoteConciliacao(lote.inverso);
    useUltimoLote.getState().definir(null);
    notificar.sucesso("Último lote desfeito: valores restaurados e lançamentos criados removidos.");
  }

  const emFila = lido !== null && passo === 1;

  return (
    <>
      <PageHeader titulo="Conciliação de extratos" descricao="Compare o extrato do banco com seus lançamentos, concilie e crie o que faltar" />
      <div className="mb-4 max-w-md">
        <Segmentado
          rotulo="Modo de conciliação"
          valor={modo}
          onChange={setModo}
          opcoes={[
            { valor: "lote", rotulo: "Conciliar em lote" },
            { valor: "um", rotulo: "Revisar um por um" },
          ]}
        />
      </div>
      <div hidden={modo !== "lote"}>
        <Progresso passo={passoLote} passos={PASSOS_LOTE} />
        <ConciliacaoLote onPasso={setPassoLote} onRevisarUmPorUm={irParaUmPorUm} onRevisarPendencias={abrirPendencias} />
      </div>
      <div hidden={modo !== "um"}>
      <Progresso passo={passo} />
      {erro && (
        <div className="mb-4">
          <ErroBox>{erro}</ErroBox>
        </div>
      )}

      {passo === 1 && !emFila && <SeletorArquivos onArquivos={(f) => void proximoDaFila(f)} onExemplo={experimentar} exemploDisponivel={ds.contas.length > 0} />}
      {emFila && lido && <ConfirmarArquivo key={lido.nome} arquivo={lido} restantes={fila.length} onPronto={aoPronto} onCancelar={() => void proximoDaFila(fila)} />}

      {passo === 2 && extrato && (
        <DestinoResumo
          ds={ds}
          arquivo={extrato}
          destino={destino}
          janela={janela}
          tolerancia={tolerancia}
          onDestino={aoDestino}
          onJanela={setJanela}
          onTolerancia={setTolerancia}
          onVoltar={() => setPasso(1)}
          onContinuar={() => setPasso(3)}
        />
      )}

      {passo === 3 && resultado && destino && <Revisao key={`${lido?.nome}-${janela}-${tolerancia}`} ds={ds} destino={destino} resultado={resultado} onAplicar={aplicar} onVoltar={() => setPasso(2)} />}

      {passo === 4 && (
        <Card aria-labelledby="t-resultado">
          <h2 id="t-resultado" className="mb-3 flex items-center gap-2 text-lg font-semibold">
            <CircleCheck className="text-pos" size={22} aria-hidden /> 4. Conciliação aplicada
          </h2>
          {lote && (
            <p className="text-sm" role="status">
              <strong>{lote.resumo.conciliados}</strong> conciliado(s), <strong>{lote.resumo.criados}</strong> criado(s) em {lote.rotuloDestino}.
            </p>
          )}
          {relatorio && <p className="mt-1 text-xs text-muted">Classificação do extrato: {relatorio.resumo}.</p>}
          <div className="mt-4 flex flex-wrap gap-2">
            <Button icone={<Undo2 size={16} aria-hidden />} disabled={!lote} onClick={desfazer}>
              Desfazer último lote
            </Button>
            <Button onClick={() => relatorio && baixarArquivo(relatorio.nome, relatorio.csv, "text/csv;charset=utf-8")} disabled={!relatorio}>
              Exportar relatório CSV
            </Button>
            {pend.length > 0 ? (
              <Button variante="primary" onClick={() => abrirPendencias(pend)}>
                Próxima pendência ({pend.length})
              </Button>
            ) : fila.length > 0 ? (
              <Button variante="primary" onClick={() => void proximoDaFila(fila)}>
                Próximo arquivo ({fila.length})
              </Button>
            ) : (
              <Button variante="primary" onClick={() => void proximoDaFila([])}>
                Importar outro arquivo
              </Button>
            )}
          </div>
          <p className="mt-3 text-xs text-muted">O botão de desfazer vale enquanto esta aba estiver aberta. Depois, use &quot;Desfazer conciliação&quot; em cada lançamento (Lançamentos).</p>
        </Card>
      )}
      </div>
    </>
  );
}
