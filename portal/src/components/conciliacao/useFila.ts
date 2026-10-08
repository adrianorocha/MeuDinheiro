"use client";

import { useRef, useState } from "react";
import { sugerirDestino } from "@/components/conciliacao/DestinoResumo";
import { MAX_BYTES } from "@/components/conciliacao/EnvioArquivo";
import { interpretarTexto } from "@/lib/conciliacao/leitura";
import { decodificarTexto } from "@/lib/conciliacao/texto-banco";
import type { ArquivoExtrato, Destino } from "@/lib/conciliacao/tipos";
import type { Dataset } from "@/lib/finance/types";

export type StatusFila = "FILA" | "LENDO" | "PRONTO" | "REVISAR" | "ERRO";

export interface EntradaFila {
  id: string;
  file: File;
  nome: string;
  tamanho: number;
  status: StatusFila;
  /** progresso de leitura deste arquivo (0..100) */
  pct: number;
  erro?: string;
  texto?: string;
  formato?: "OFX" | "CSV";
  extrato?: ArquivoExtrato;
  destino?: Destino | null;
}

export interface ProgressoLeitura {
  feitos: number;
  total: number;
  pct: number;
}

const ceder = () => new Promise<void>((r) => setTimeout(r, 0));
let contador = 0;

/** Fila de arquivos do lote: leitura assíncrona sequencial, cedendo ao navegador entre as etapas. */
export function useFila(getDs: () => Dataset) {
  const ref = useRef<EntradaFila[]>([]);
  const ocupado = useRef(false);
  const [itens, setItens] = useState<EntradaFila[]>([]);
  const [leitura, setLeitura] = useState<ProgressoLeitura | null>(null);
  const [avisos, setAvisos] = useState<string[]>([]);

  const gravar = (novo: EntradaFila[]) => {
    ref.current = novo;
    setItens(novo);
  };
  const patch = (id: string, p: Partial<EntradaFila>) => gravar(ref.current.map((e) => (e.id === id ? { ...e, ...p } : e)));

  async function processar() {
    if (ocupado.current) return;
    ocupado.current = true;
    let feitos = 0;
    try {
      for (;;) {
        const prox = ref.current.find((e) => e.status === "FILA");
        if (!prox) break;
        const total = feitos + ref.current.filter((e) => e.status === "FILA").length;
        const etapa = (pct: number, extra: Partial<EntradaFila> = {}, status: StatusFila = "LENDO") => {
          patch(prox.id, { status, pct, ...extra });
          setLeitura({ feitos, total, pct: Math.round(((feitos + (status === "LENDO" ? pct / 100 : 1)) / total) * 100) });
        };
        etapa(5);
        await ceder();
        try {
          const bytes = new Uint8Array(await prox.file.arrayBuffer());
          etapa(40);
          await ceder();
          const texto = decodificarTexto(bytes);
          etapa(70);
          await ceder();
          const r = interpretarTexto(texto);
          if (r.estado === "ERRO") etapa(100, { erro: r.erro }, "ERRO");
          else if (r.estado === "REVISAR") etapa(100, { texto, formato: "CSV", destino: null }, "REVISAR");
          else etapa(100, { texto, formato: r.formato, extrato: r.extrato, destino: sugerirDestino(getDs(), r.extrato) }, "PRONTO");
        } catch (e) {
          etapa(100, { erro: e instanceof Error ? e.message : "Não foi possível ler o arquivo." }, "ERRO");
        }
        feitos++;
        await ceder();
      }
    } finally {
      ocupado.current = false;
      setLeitura(null);
    }
  }

  function adicionar(files: File[]) {
    const duplicados: string[] = [];
    const novos: EntradaFila[] = [];
    for (const file of files) {
      const repetido = [...ref.current, ...novos].some((e) => e.nome === file.name && e.tamanho === file.size);
      if (repetido) {
        duplicados.push(file.name);
        continue;
      }
      const base = { id: `f${++contador}`, file, nome: file.name, tamanho: file.size, pct: 0 };
      novos.push(file.size > MAX_BYTES ? { ...base, status: "ERRO", pct: 100, erro: "Arquivo grande demais (máx. 25 MB)." } : { ...base, status: "FILA" });
    }
    setAvisos(duplicados.length ? [`Arquivo repetido (mesmo nome e tamanho), ignorado: ${duplicados.join(", ")}.`] : []);
    if (novos.length) gravar([...ref.current, ...novos]);
    void processar();
  }

  return {
    itens,
    leitura,
    avisos,
    adicionar,
    remover: (id: string) => gravar(ref.current.filter((e) => e.id !== id)),
    limpar: () => {
      gravar([]);
      setAvisos([]);
    },
    definirDestino: (id: string, destino: Destino | null) => patch(id, { destino }),
    /** conclui o mapeamento manual de colunas de um CSV */
    concluirMapeamento: (id: string, extrato: ArquivoExtrato) => patch(id, { status: "PRONTO", extrato, erro: undefined, destino: sugerirDestino(getDs(), extrato) }),
    fecharAvisos: () => setAvisos([]),
  };
}
