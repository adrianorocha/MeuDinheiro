"use client";

import { FileText, Paperclip, RotateCw, X } from "lucide-react";
import { useId, useRef, useState } from "react";
import type { DragEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { anexoEhLocal, formatarTamanho, MAX_ANEXOS, MAX_BYTES_ANEXO, type Anexo } from "@/lib/finance/anexos";
import { prepararPendentes, type Pendente } from "@/lib/firebase/anexos";

interface Props {
  /** Anexos já gravados no lançamento (vazio em lançamento novo). */
  existentes: readonly Anexo[];
  pendentes: readonly Pendente[];
  onPendentes: (p: Pendente[]) => void;
  removidos: readonly string[];
  onRemover: (id: string) => void;
  /** false = sem nuvem (modo local): só exibe um aviso. */
  nuvem: boolean;
  /** Reenvia os pendentes com erro (só existe depois que o lançamento foi salvo). */
  onTentarDeNovo?: () => void;
  desabilitado?: boolean;
}

/** R60 - seção "Anexos" do formulário: arrastar/soltar ou escolher, prévia, remover, progresso e "Tentar de novo". */
export function AnexosCampo({ existentes, pendentes, onPendentes, removidos, onRemover, nuvem, onTentarDeNovo, desabilitado }: Props) {
  const inputId = useId();
  const ref = useRef<HTMLInputElement>(null);
  const [arrastando, setArrastando] = useState(false);
  const [erros, setErros] = useState<string[]>([]);
  const visiveis = existentes.filter((a) => !removidos.includes(a.id));
  const total = visiveis.length + pendentes.length;
  const comErro = pendentes.some((p) => p.status === "erro");

  function adicionar(lista: FileList | File[] | null) {
    if (!lista || !nuvem) return;
    const { aceitos, erros: e } = prepararPendentes(Array.from(lista), total);
    setErros(e);
    if (aceitos.length > 0) onPendentes([...pendentes, ...aceitos]);
  }

  function soltar(ev: DragEvent<HTMLDivElement>) {
    ev.preventDefault();
    setArrastando(false);
    if (!desabilitado) adicionar(ev.dataTransfer.files);
  }

  return (
    <fieldset className="flex flex-col gap-2 rounded-lg border border-line p-3">
      <legend className="px-1 text-sm font-medium">
        Anexos ({total}/{MAX_ANEXOS})
      </legend>
      {!nuvem ? (
        <p className="text-xs text-muted">Anexar fotos e comprovantes exige entrar com a conta na nuvem (Firebase). No modo local do portal não há onde guardar os arquivos.</p>
      ) : (
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setArrastando(true);
          }}
          onDragLeave={() => setArrastando(false)}
          onDrop={soltar}
          className={`flex flex-col items-center gap-2 rounded-lg border-2 border-dashed px-3 py-4 text-center text-xs ${arrastando ? "border-primary bg-primary-soft" : "border-line"}`}
        >
          <Paperclip size={18} aria-hidden className="text-muted" />
          <p className="text-muted">Arraste arquivos aqui ou escolha. JPEG, PNG ou PDF, até {Math.round(MAX_BYTES_ANEXO / 1024 / 1024)} MB cada.</p>
          <input
            id={inputId}
            ref={ref}
            type="file"
            multiple
            accept="image/jpeg,image/png,application/pdf"
            className="sr-only"
            disabled={desabilitado || total >= MAX_ANEXOS}
            onChange={(e) => {
              adicionar(e.target.files);
              e.target.value = "";
            }}
          />
          <Button tamanho="sm" disabled={desabilitado || total >= MAX_ANEXOS} onClick={() => ref.current?.click()}>
            Escolher arquivos
          </Button>
        </div>
      )}
      {erros.length > 0 && (
        <ul className="text-xs text-neg" role="alert">
          {erros.map((e) => (
            <li key={e}>{e}</li>
          ))}
        </ul>
      )}
      {(visiveis.length > 0 || pendentes.length > 0) && (
        <ul className="flex flex-col gap-2">
          {visiveis.map((a) => (
            <li key={a.id} className="flex items-center gap-2 rounded-lg bg-surface-2 px-2 py-1.5 text-sm">
              <FileText size={16} aria-hidden className="shrink-0 text-muted" />
              <span className="min-w-0 flex-1 truncate">{a.nome}</span>
              <span className="shrink-0 text-xs text-muted">
                {formatarTamanho(a.tamanho)}
                {anexoEhLocal(a.caminho) && " · só no aparelho"}
              </span>
              <IconButton rotulo={`Remover anexo ${a.nome}`} onClick={() => onRemover(a.id)} disabled={desabilitado}>
                <X size={16} aria-hidden />
              </IconButton>
            </li>
          ))}
          {pendentes.map((p) => (
            <li key={p.chave} className="flex flex-col gap-1 rounded-lg bg-surface-2 px-2 py-1.5 text-sm">
              <div className="flex items-center gap-2">
                <PreviaImagem file={p.file} />
                <span className="min-w-0 flex-1 truncate">{p.file.name}</span>
                <span className="shrink-0 text-xs text-muted">
                  {formatarTamanho(p.file.size)} · {p.status === "enviando" ? `enviando ${p.pct}%` : p.status === "erro" ? "não enviado" : "será enviado ao salvar"}
                </span>
                <IconButton rotulo={`Remover ${p.file.name}`} onClick={() => onPendentes(pendentes.filter((x) => x.chave !== p.chave))} disabled={p.status === "enviando"}>
                  <X size={16} aria-hidden />
                </IconButton>
              </div>
              {p.status === "enviando" && <progress className="h-1.5 w-full" max={100} value={p.pct} aria-label={`Progresso do envio de ${p.file.name}`} />}
              {p.status === "erro" && p.erro && (
                <p className="text-xs text-neg" role="alert">
                  {p.erro}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
      {comErro && onTentarDeNovo && (
        <div className="flex flex-col gap-1">
          <p className="text-xs text-warn" role="status">
            O lançamento foi salvo, mas {pendentes.filter((p) => p.status === "erro").length} anexo(s) não foi/foram enviado(s). Se fechar sem reenviar, esses arquivos serão descartados.
          </p>
          <Button tamanho="sm" icone={<RotateCw size={14} aria-hidden />} onClick={onTentarDeNovo} className="self-start">
            Tentar de novo
          </Button>
        </div>
      )}
    </fieldset>
  );
}

/** Miniatura de imagem escolhida (URL de objeto criada na renderização e liberada ao sair). */
function PreviaImagem({ file }: { file: File }) {
  const [url] = useState(() => (file.type.startsWith("image/") ? URL.createObjectURL(file) : null));
  if (!url) return <FileText size={16} aria-hidden className="shrink-0 text-muted" />;
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={url} alt={`Prévia de ${file.name}`} className="size-9 shrink-0 rounded object-cover" onLoad={() => URL.revokeObjectURL(url)} />;
}
