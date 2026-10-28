"use client";

import { FileImage, FileText } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { AVISO_COMPROVANTE, montarComprovante } from "@/lib/finance/comprovante";
import type { Despesa } from "@/lib/finance/types";
import { baixarBlob } from "@/lib/download";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";

/** R48 - comprovante do lançamento: prévia com as mesmas linhas do arquivo e download em PNG ou PDF. */
export function ComprovanteModal({ despesa, onFechar }: { despesa: Despesa | null; onFechar: () => void }) {
  const ds = useDataset();
  const agora = useAgora();
  const notificar = useNotificar();
  const [gerando, setGerando] = useState<"png" | "pdf" | null>(null);
  const modelo = useMemo(() => (despesa ? montarComprovante(despesa, ds, agora) : null), [despesa, ds, agora]);

  async function baixar(formato: "png" | "pdf") {
    if (!modelo) return;
    setGerando(formato);
    try {
      const { gerarComprovantePdf, gerarComprovantePng } = await import("@/lib/exportar/comprovante");
      const nome = `comprovante-${modelo.id.toLowerCase()}`;
      baixarBlob(`${nome}.${formato}`, formato === "png" ? await gerarComprovantePng(modelo) : await gerarComprovantePdf(modelo));
      notificar.sucesso(`Comprovante em ${formato.toUpperCase()} gerado.`);
    } catch (e) {
      notificar.erro(`Não foi possível gerar o comprovante: ${e instanceof Error ? e.message : "erro desconhecido"}`);
    } finally {
      setGerando(null);
    }
  }

  return (
    <Modal aberto={despesa !== null} onFechar={onFechar} titulo="Comprovante">
      {modelo && (
        <div className="flex flex-col gap-4">
          <div className="text-center">
            <p className="text-xs font-semibold tracking-wide text-primary">MeuDinheiro · {modelo.id}</p>
            <span className="mt-2 inline-block rounded-full px-3 py-1 text-xs font-bold text-white" style={{ backgroundColor: modelo.selo.cor }}>
              {modelo.selo.rotulo}
            </span>
            <p className="tabular mt-2 text-3xl font-extrabold">{modelo.valor}</p>
            <span className="mt-1 inline-block rounded-full px-3 py-0.5 text-xs font-bold text-white" style={{ backgroundColor: modelo.status.cor }}>
              {modelo.status.rotulo}
            </span>
          </div>
          <dl className="divide-y divide-line text-sm">
            {modelo.linhas.map((l) => (
              <div key={l.rotulo} className="flex justify-between gap-4 py-2">
                <dt className="text-muted">{l.rotulo}</dt>
                <dd className="text-right font-semibold [overflow-wrap:anywhere]">{l.valor}</dd>
              </div>
            ))}
          </dl>
          <p className="text-center text-xs text-muted">
            Emitido em {modelo.emitidoEm}
            <br />
            {AVISO_COMPROVANTE}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button icone={<FileImage size={16} aria-hidden />} disabled={gerando !== null} onClick={() => void baixar("png")}>
              Baixar PNG
            </Button>
            <Button icone={<FileText size={16} aria-hidden />} disabled={gerando !== null} onClick={() => void baixar("pdf")}>
              Baixar PDF
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
