"use client";

import { X } from "lucide-react";
import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { Button, IconButton } from "./Button";

interface ModalProps {
  aberto: boolean;
  onFechar: () => void;
  titulo: string;
  children: ReactNode;
}

/** Diálogo modal baseado em <dialog> (foco preso, Esc fecha, aria-modal nativo). */
export function Modal({ aberto, onFechar, titulo, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const tituloId = useId();

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (aberto && !d.open) d.showModal();
    if (!aberto && d.open) d.close();
  }, [aberto]);

  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby={tituloId}
      onClose={onFechar}
      onClick={(e) => {
        if (e.target === ref.current) onFechar();
      }}
    >
      {aberto && (
        <div className="flex max-h-[calc(100dvh-2rem)] flex-col">
          <header className="flex items-center justify-between border-b border-line px-5 py-3">
            <h2 id={tituloId} className="text-base font-semibold">
              {titulo}
            </h2>
            <IconButton rotulo="Fechar" onClick={onFechar}>
              <X size={18} aria-hidden />
            </IconButton>
          </header>
          <div className="overflow-y-auto px-5 py-4">{children}</div>
        </div>
      )}
    </dialog>
  );
}

export function RodapeForm({ onCancelar, enviando, rotuloEnviar = "Salvar" }: { onCancelar: () => void; enviando?: boolean; rotuloEnviar?: string }) {
  return (
    <div className="mt-5 flex justify-end gap-2">
      <Button onClick={onCancelar}>Cancelar</Button>
      <Button type="submit" variante="primary" disabled={enviando}>
        {rotuloEnviar}
      </Button>
    </div>
  );
}

interface ConfirmarProps {
  aberto: boolean;
  titulo: string;
  mensagem: ReactNode;
  rotuloConfirmar?: string;
  perigo?: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
  children?: ReactNode;
}

export function Confirmar({ aberto, titulo, mensagem, rotuloConfirmar = "Confirmar", perigo, onConfirmar, onCancelar, children }: ConfirmarProps) {
  return (
    <Modal aberto={aberto} onFechar={onCancelar} titulo={titulo}>
      <div className="text-sm text-muted">{mensagem}</div>
      {children}
      <div className="mt-5 flex justify-end gap-2">
        <Button onClick={onCancelar}>Cancelar</Button>
        <Button variante={perigo ? "danger" : "primary"} onClick={onConfirmar}>
          {rotuloConfirmar}
        </Button>
      </div>
    </Modal>
  );
}
