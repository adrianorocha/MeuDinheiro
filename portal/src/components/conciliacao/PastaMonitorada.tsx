"use client";

import { FolderOpen, FolderSync } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import type { ArquivoDePasta } from "@/lib/conciliacao/importacao-arquivos";
import { escolherPasta, esquecerPasta, marcarVistos, pastaGuardada, pastaSuportada, pedirPermissao, permissaoAtual, varrerPasta, type ItemPasta, type PastaHandle } from "@/lib/conciliacao/pasta-monitorada";

type Estado = "carregando" | "sem-pasta" | "precisa-permissao" | "pronta";

/**
 * R63 - pasta monitorada (Chromium). O navegador não agenda tarefas em segundo plano: a pasta é verificada
 * quando esta página abre ou quando o usuário clica em "Verificar". Nada é enviado a servidores.
 */
export function PastaMonitorada({ onArquivos }: { onArquivos: (files: File[]) => void }) {
  const suportada = pastaSuportada();
  const [estado, setEstado] = useState<Estado>("carregando");
  const [pasta, setPasta] = useState<PastaHandle | null>(null);
  const [novos, setNovos] = useState<ItemPasta[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  const verificar = useCallback(async (h: PastaHandle) => {
    setErro(null);
    try {
      setNovos(await varrerPasta(h));
      setEstado("pronta");
    } catch {
      setErro("Não foi possível ler a pasta. Escolha-a novamente.");
      setEstado("sem-pasta");
    }
  }, []);

  useEffect(() => {
    if (!suportada) return;
    let ativo = true;
    void (async () => {
      const h = await pastaGuardada().catch(() => null);
      if (!ativo) return;
      if (!h) return setEstado("sem-pasta");
      setPasta(h);
      // não insiste: se o navegador exigir nova permissão, espera o clique do usuário
      if ((await permissaoAtual(h)) === "granted") await verificar(h);
      else if (ativo) setEstado("precisa-permissao");
    })();
    return () => {
      ativo = false;
    };
  }, [suportada, verificar]);

  if (!suportada) {
    return (
      <p className="mt-3 rounded-lg bg-surface-2 px-3 py-2 text-xs text-muted">
        Pasta monitorada: este navegador não permite acessar pastas (use Chrome ou Edge no computador). Arraste os arquivos para a área acima ou escolha-os manualmente. O navegador também não consegue verificar a pasta em segundo plano.
      </p>
    );
  }

  async function escolher() {
    const h = await escolherPasta();
    if (!h) return;
    setPasta(h);
    await verificar(h);
  }

  async function autorizar() {
    if (!pasta) return;
    if (await pedirPermissao(pasta)) await verificar(pasta);
    else setErro("Permissão negada. Você pode escolher a pasta de novo.");
  }

  async function adicionar() {
    onArquivos(novos.map((n) => n.file));
    await marcarVistos(novos.map<ArquivoDePasta>((n) => n.info));
    setNovos([]);
  }

  async function esquecer() {
    await esquecerPasta();
    setPasta(null);
    setNovos([]);
    setEstado("sem-pasta");
  }

  return (
    <div className="mt-3 rounded-xl border border-line bg-surface-2 p-3" aria-label="Pasta monitorada">
      <p className="flex items-center gap-2 text-sm font-medium">
        <FolderSync size={16} className="text-primary" aria-hidden />
        Pasta de extratos {pasta ? `· ${pasta.name}` : ""}
      </p>
      <p className="mt-1 text-xs text-muted">A pasta é verificada quando você abre esta página ou clica em &quot;Verificar&quot;. Os arquivos são lidos só no seu navegador.</p>
      {erro && (
        <p role="alert" className="mt-2 text-xs text-neg">
          {erro}
        </p>
      )}
      {estado === "pronta" && (
        <p role="status" className="mt-2 text-sm">
          {novos.length === 0 ? "Nenhum arquivo novo desde a última importação." : `${novos.length} ${novos.length === 1 ? "arquivo novo" : "arquivos novos"} desde a última importação.`}
        </p>
      )}
      {estado === "precisa-permissao" && <p className="mt-2 text-xs text-warn">O navegador pede autorização de novo para ler esta pasta.</p>}
      <div className="mt-2 flex flex-wrap gap-2">
        {estado === "precisa-permissao" && (
          <Button tamanho="sm" variante="primary" onClick={() => void autorizar()}>
            Autorizar pasta
          </Button>
        )}
        {estado === "pronta" && novos.length > 0 && (
          <Button tamanho="sm" variante="primary" onClick={() => void adicionar()}>
            Adicionar {novos.length} à fila
          </Button>
        )}
        {estado === "pronta" && pasta && (
          <Button tamanho="sm" onClick={() => void verificar(pasta)}>
            Verificar
          </Button>
        )}
        <Button tamanho="sm" icone={<FolderOpen size={14} aria-hidden />} onClick={() => void escolher()}>
          {pasta ? "Trocar pasta" : "Escolher pasta"}
        </Button>
        {pasta && (
          <Button tamanho="sm" onClick={() => void esquecer()}>
            Esquecer pasta
          </Button>
        )}
      </div>
    </div>
  );
}
