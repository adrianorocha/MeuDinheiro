import { useState } from "react";
import type { Dataset } from "./finance/types";
import { useStore } from "./store/store";

/** Dataset em memória (as páginas autenticadas só renderizam com o store pronto). */
export function useDataset(): Dataset {
  return useStore((s) => s.ds);
}

/** Instante fixo durante o ciclo de vida do componente (evita valores instáveis entre renders). */
export function useAgora(): number {
  const [agora] = useState(() => Date.now());
  return agora;
}

/** Aviso visual padronizado para resultados de operações. */
export function useNotificar() {
  const avisar = useStore((s) => s.avisar);
  return {
    erro: (texto: string) => avisar("erro", texto),
    sucesso: (texto: string) => avisar("sucesso", texto),
    info: (texto: string) => avisar("info", texto),
  };
}
