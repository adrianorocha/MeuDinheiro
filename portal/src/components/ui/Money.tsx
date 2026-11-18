"use client";

import { formatBRL, MASCARA } from "@/lib/format";
import { useSession } from "@/lib/session";

interface Props {
  valor: number;
  /** "auto": verde se positivo, vermelho se negativo. */
  tom?: "auto" | "neutro";
  className?: string;
}

/** Valor em R$ respeitando o modo privado. */
export function Money({ valor, tom = "neutro", className = "" }: Props) {
  const privado = useSession((s) => s.privado);
  const cor = tom === "auto" && !privado ? (valor > 0 ? "text-pos" : valor < 0 ? "text-neg" : "") : "";
  return <span className={`tabular whitespace-nowrap ${cor} ${className}`}>{privado ? MASCARA : formatBRL(valor)}</span>;
}

/** Texto genérico sensível (ex.: quantidade) respeitando o modo privado. */
export function useTextoPrivado(): (texto: string) => string {
  const privado = useSession((s) => s.privado);
  return (texto) => (privado ? "••••••" : texto);
}
