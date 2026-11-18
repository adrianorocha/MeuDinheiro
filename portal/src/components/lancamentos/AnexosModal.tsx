"use client";

import { Download, ExternalLink, FileText } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Spinner } from "@/components/ui/Misc";
import { anexoEhImagem, anexoEhLocal, formatarTamanho, normalizarAnexos, type Anexo } from "@/lib/finance/anexos";
import { urlDoAnexo } from "@/lib/firebase/anexosArquivos";
import type { Despesa } from "@/lib/finance/types";

/** R60 - visualizar/baixar os anexos de um lançamento (imagem em prévia; PDF abre em nova aba). */
export function AnexosModal({ despesa, onFechar }: { despesa: Despesa | null; onFechar: () => void }) {
  const anexos = normalizarAnexos(despesa?.anexos);
  return (
    <Modal aberto={despesa !== null} onFechar={onFechar} titulo={`Anexos${despesa ? ` · ${despesa.descricao}` : ""}`}>
      {despesa && (
        <div className="flex flex-col gap-4">
          {anexos.length === 0 && <p className="text-sm text-muted">Este lançamento não tem anexos.</p>}
          {anexos.map((a) => (
            <ItemAnexo key={a.id} anexo={a} />
          ))}
        </div>
      )}
    </Modal>
  );
}

function ItemAnexo({ anexo }: { anexo: Anexo }) {
  const [url, setUrl] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(!anexoEhLocal(anexo.caminho));
  const [tentativa, setTentativa] = useState(0);

  useEffect(() => {
    if (anexoEhLocal(anexo.caminho)) return;
    let vivo = true;
    urlDoAnexo(anexo)
      .then((u) => vivo && (setUrl(u), setErro(null)))
      .catch((e: unknown) => vivo && setErro(e instanceof Error ? e.message : "Não foi possível carregar o anexo."))
      .finally(() => vivo && setCarregando(false));
    return () => {
      vivo = false;
    };
  }, [anexo, tentativa]);

  return (
    <section className="flex flex-col gap-2 rounded-lg border border-line p-3" aria-label={`Anexo ${anexo.nome}`}>
      <div className="flex items-center gap-2">
        <FileText size={16} aria-hidden className="shrink-0 text-muted" />
        <p className="min-w-0 flex-1 truncate text-sm font-medium">{anexo.nome}</p>
        <span className="shrink-0 text-xs text-muted">{formatarTamanho(anexo.tamanho)}</span>
      </div>
      {anexoEhLocal(anexo.caminho) && (
        <p className="text-xs text-warn" role="status">
          Este arquivo está só no aparelho (app) onde foi anexado e não foi enviado para a nuvem; o portal não consegue abri-lo.
        </p>
      )}
      {carregando && <Spinner rotulo="Carregando anexo" />}
      {erro && (
        <div className="flex flex-col gap-1">
          <p className="text-xs text-neg" role="alert">
            {erro}
          </p>
          <Button tamanho="sm" className="self-start" onClick={() => { setCarregando(true); setTentativa((t) => t + 1); }}>
            Tentar de novo
          </Button>
        </div>
      )}
      {url && anexoEhImagem(anexo.tipo) && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={`Imagem do anexo ${anexo.nome}`} className="max-h-96 w-full rounded object-contain" />
      )}
      {url && (
        <div className="flex gap-2">
          <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-line bg-surface px-3 text-sm font-medium hover:bg-surface-2">
            <ExternalLink size={14} aria-hidden /> Abrir
          </a>
          <a href={url} download={anexo.nome} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-line bg-surface px-3 text-sm font-medium hover:bg-surface-2">
            <Download size={14} aria-hidden /> Baixar
          </a>
        </div>
      )}
    </section>
  );
}
