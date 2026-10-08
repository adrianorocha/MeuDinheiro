"use client";

import { ArrowRight, Eraser, ListOrdered } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { rotuloDestino } from "@/components/conciliacao/DestinoResumo";
import { ConfirmarArquivo, ZonaDrop } from "@/components/conciliacao/EnvioArquivo";
import { ExecucaoLote, type EstadoExecucao } from "@/components/conciliacao/ExecucaoLote";
import { FilaArquivos } from "@/components/conciliacao/FilaArquivos";
import { OpcoesLote } from "@/components/conciliacao/OpcoesLote";
import { ResultadoLote } from "@/components/conciliacao/ResultadoLote";
import { useFila } from "@/components/conciliacao/useFila";
import { Button } from "@/components/ui/Button";
import { Card, CardTitulo } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { gerarOfxExemplo } from "@/lib/conciliacao/exemplo";
import { useUltimoLote } from "@/lib/conciliacao/lote";
import {
  arquivosComPendencias,
  contadoresVazios,
  executarLote,
  inversosEmOrdemInversa,
  OPCOES_LOTE_PADRAO,
  preverLote,
  type ArquivoParaLote,
  type EventoLote,
  type OpcoesLote as Opcoes,
} from "@/lib/conciliacao/lote-multiplo";
import { csvRelatorioConsolidado } from "@/lib/conciliacao/relatorio";
import type { ArquivoExtrato, Destino } from "@/lib/conciliacao/tipos";
import { baixarArquivo } from "@/lib/download";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

export type PassoLote = 1 | 2 | 3 | 4;
export interface Pendencia {
  nome: string;
  extrato: ArquivoExtrato;
  destino: Destino;
}

const EXEC_INICIAL: EstadoExecucao = { total: 0, indice: 0, nome: "", etapa: "FILA", pctArquivo: 0, pctGeral: 0, contadores: contadoresVazios(), recentes: [], cancelando: false };

function reduzir(prev: EstadoExecucao, ev: EventoLote): EstadoExecucao {
  switch (ev.tipo) {
    case "inicio":
      return { ...EXEC_INICIAL, total: ev.total, contadores: ev.contadores };
    case "etapa":
      return { ...prev, indice: ev.indice, nome: ev.nome, etapa: ev.etapa, pctArquivo: ev.pctArquivo, pctGeral: ev.pctGeral, contadores: ev.contadores };
    case "linhas":
      return { ...prev, contadores: ev.contadores, pctGeral: ev.pctGeral, recentes: [...ev.linhas.slice(-8).reverse(), ...prev.recentes].slice(0, 8) };
    case "arquivo":
      return { ...prev, contadores: ev.contadores, pctGeral: ev.pctGeral, etapa: ev.resultado.status === "ERRO" ? "ERRO" : "CONCLUIDO", pctArquivo: 100 };
    case "fim":
      return { ...prev, pctGeral: ev.pctGeral };
  }
}

interface Props {
  onPasso: (p: PassoLote) => void;
  onRevisarUmPorUm: (files: File[]) => void;
  onRevisarPendencias: (itens: Pendencia[]) => void;
}

/** Fluxo "Conciliar em lote": Arquivos, Opções, Conciliando, Resultado (R43). */
export function ConciliacaoLote({ onPasso, onRevisarUmPorUm, onRevisarPendencias }: Props) {
  const ds = useDataset();
  const agora = useAgora();
  const notificar = useNotificar();
  const lote = useUltimoLote((s) => s.multiplo);
  const fila = useFila(() => useStore.getState().ds);

  const [fase, setFase] = useState<PassoLote>(() => (useUltimoLote.getState().multiplo ? 4 : 1));
  const [opcoes, setOpcoes] = useState<Opcoes>(OPCOES_LOTE_PADRAO);
  const [exec, setExec] = useState<EstadoExecucao>(EXEC_INICIAL);
  const [usados, setUsados] = useState<ArquivoParaLote[]>([]);
  const [mapeando, setMapeando] = useState<string | null>(null);
  const cancelar = useRef(false);

  useEffect(() => onPasso(fase), [fase, onPasso]);

  const prontos = useMemo<ArquivoParaLote[]>(
    () =>
      fila.itens.flatMap((i) => (i.status === "PRONTO" && i.extrato && i.destino ? [{ id: i.id, nome: i.nome, extrato: i.extrato, destino: i.destino, rotuloDestino: rotuloDestino(ds, i.destino) }] : [])),
    [fila.itens, ds],
  );
  const ocupado = fila.itens.some((i) => i.status === "FILA" || i.status === "LENDO");
  const semDestino = fila.itens.some((i) => i.status === "PRONTO" && !i.destino);
  const previa = useMemo(() => (fase === 2 ? preverLote(ds, prontos, opcoes, agora) : []), [fase, ds, prontos, opcoes, agora]);
  const alvoMapa = fila.itens.find((i) => i.id === mapeando);

  function exemplo() {
    const conta = ds.contas.find((c) => ds.despesas.some((d) => d.conta === c.conta && !d.cartaoId && d.natureza === "NORMAL")) ?? ds.contas[0];
    const texto = conta ? gerarOfxExemplo(ds, conta.conta, agora) : null;
    if (!texto) return notificar.erro("Sem lançamentos recentes para gerar o exemplo. Carregue os dados de exemplo em Configurações.");
    fila.adicionar([new File([texto], "extrato-exemplo-gerado.ofx", { type: "application/x-ofx" })]);
  }

  async function conciliar() {
    cancelar.current = false;
    setUsados(prontos);
    setExec({ ...EXEC_INICIAL, total: prontos.length });
    setFase(3);
    const resultado = await executarLote({
      arquivos: prontos,
      opcoes,
      getDs: () => useStore.getState().ds,
      aplicar: (plano) => acoes.aplicarConciliacao(plano),
      onProgresso: (ev) => setExec((p) => reduzir(p, ev)),
      sinalCancelar: () => cancelar.current,
    });
    useUltimoLote.getState().definir(null);
    useUltimoLote.getState().definirMultiplo({ resultado, em: Date.now(), desfeito: false });
    setFase(4);
    const c = resultado.contadores;
    notificar.sucesso(`${resultado.cancelado ? "Lote interrompido" : "Lote concluído"}: ${c.conciliados} conciliado(s), ${c.criados} criado(s).`);
  }

  async function desfazer() {
    if (!lote) return;
    for (const inv of inversosEmOrdemInversa(lote.resultado)) {
      acoes.desfazerLoteConciliacao(inv);
      await new Promise<void>((r) => setTimeout(r, 0));
    }
    useUltimoLote.getState().definirMultiplo({ ...lote, desfeito: true });
    notificar.sucesso("Lote desfeito: valores restaurados e lançamentos criados removidos.");
  }

  function exportar() {
    if (!lote) return;
    const dia = new Date(lote.em).toISOString().slice(0, 10);
    baixarArquivo(`conciliacao-lote-${dia}.csv`, csvRelatorioConsolidado(lote.resultado.arquivos), "text/csv;charset=utf-8");
  }

  function revisarPendencias() {
    if (!lote) return;
    const ids = new Set(arquivosComPendencias(lote.resultado).map((a) => a.id));
    onRevisarPendencias(usados.filter((u) => ids.has(u.id)).map((u) => ({ nome: u.nome, extrato: u.extrato, destino: u.destino })));
  }

  function novoLote() {
    fila.limpar();
    useUltimoLote.getState().definirMultiplo(null);
    setFase(1);
  }

  if (fase === 4 && lote) return <ResultadoLote lote={lote} onDesfazer={() => void desfazer()} onExportar={exportar} onRevisarPendencias={revisarPendencias} onNovo={novoLote} />;
  if (fase === 3)
    return (
      <ExecucaoLote
        e={exec}
        onCancelar={() => {
          cancelar.current = true;
          setExec((p) => ({ ...p, cancelando: true }));
        }}
      />
    );
  if (fase === 2)
    return (
      <>
        <OpcoesLote opcoes={opcoes} onChange={setOpcoes} previa={previa} />
        <div className="mt-4 flex flex-wrap justify-end gap-2">
          <Button onClick={() => setFase(1)}>Voltar</Button>
          <Button variante="primary" icone={<ArrowRight size={16} aria-hidden />} disabled={prontos.length === 0} onClick={() => void conciliar()}>
            Conciliar {prontos.length} {prontos.length === 1 ? "arquivo" : "arquivos"}
          </Button>
        </div>
      </>
    );

  const vazio = fila.itens.length === 0;
  return (
    <Card aria-labelledby="t-fila">
      <CardTitulo id="t-fila">1. Arquivos do lote</CardTitulo>
      <ZonaDrop onArquivos={fila.adicionar} compacto={!vazio} />
      <p className="mt-3 text-xs text-muted">Privacidade: os arquivos são lidos somente no seu navegador. Nada deles é enviado a servidores; só os lançamentos que você confirmar são gravados.</p>
      {fila.avisos.map((a) => (
        <p key={a} role="status" className="mt-2 rounded-lg bg-warn-soft px-3 py-2 text-xs text-warn">
          {a}
        </p>
      ))}
      {vazio ? (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button onClick={exemplo} disabled={ds.contas.length === 0}>
            Experimentar com arquivo de exemplo
          </Button>
          <a className="inline-flex h-10 items-center rounded-lg border border-line px-4 text-sm font-medium hover:bg-surface-2" href="/exemplos/extrato-exemplo.ofx" download>
            Baixar OFX de exemplo
          </a>
          <a className="inline-flex h-10 items-center rounded-lg border border-line px-4 text-sm font-medium hover:bg-surface-2" href="/exemplos/fatura-exemplo.csv" download>
            Baixar CSV de fatura
          </a>
        </div>
      ) : (
        <div className="mt-4">
          <FilaArquivos itens={fila.itens} ds={ds} leitura={fila.leitura} onDestino={fila.definirDestino} onMapear={setMapeando} onRemover={fila.remover} />
          <div className="mt-4 flex flex-wrap justify-end gap-2">
            <Button icone={<Eraser size={16} aria-hidden />} onClick={fila.limpar}>
              Limpar fila
            </Button>
            <Button icone={<ListOrdered size={16} aria-hidden />} disabled={ocupado} onClick={() => onRevisarUmPorUm(fila.itens.filter((i) => i.status !== "ERRO").map((i) => i.file))}>
              Revisar um por um
            </Button>
            <Button variante="primary" disabled={ocupado || prontos.length === 0 || semDestino} onClick={() => setFase(2)}>
              Continuar com {prontos.length} {prontos.length === 1 ? "arquivo" : "arquivos"}
            </Button>
          </div>
        </div>
      )}
      <Modal aberto={alvoMapa !== undefined && alvoMapa.texto !== undefined} onFechar={() => setMapeando(null)} titulo="Mapear colunas do CSV">
        {alvoMapa?.texto !== undefined && (
          <ConfirmarArquivo
            key={alvoMapa.id}
            arquivo={{ nome: alvoMapa.nome, texto: alvoMapa.texto }}
            restantes={0}
            onPronto={(x) => {
              fila.concluirMapeamento(alvoMapa.id, x);
              setMapeando(null);
            }}
            onCancelar={() => setMapeando(null)}
          />
        )}
      </Modal>
    </Card>
  );
}
