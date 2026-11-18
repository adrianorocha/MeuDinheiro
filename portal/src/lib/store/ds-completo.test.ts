import { describe, expect, it } from "vitest";
import { emptyDataset, type Dataset } from "../finance/types";
import type { PersistenceAdapter } from "./adapter";
import { useStore } from "./store";

const T = { id: 7, descricao: "legado", valor: 1, tipo: "DEBITO", data: 1, categoria: "x" } as unknown as Dataset["transacoes"][number];

function adapter(comOcultos: boolean): PersistenceAdapter {
  return {
    mode: "firebase",
    start: (onData) => {
      onData(emptyDataset());
      return () => undefined;
    },
    save: async () => undefined,
    replaceAll: async () => undefined,
    clear: async () => undefined,
    ...(comOcultos ? { carregarDadosOcultos: async () => ({ transacoes: [T] }) } : {}),
  };
}

describe("R72 - dados sob demanda", () => {
  it("dsCompleto acrescenta as coleções que não são escutadas, sem alterar o ds do store", async () => {
    useStore.getState().iniciar(adapter(true));
    expect(useStore.getState().ds.transacoes).toEqual([]);
    const completo = await useStore.getState().dsCompleto();
    expect(completo.transacoes).toEqual([T]);
    expect(useStore.getState().ds.transacoes).toEqual([]);
    useStore.getState().parar();
  });

  it("sem suporte do adaptador (modo local) devolve o próprio ds", async () => {
    useStore.getState().iniciar(adapter(false));
    const ds = useStore.getState().ds;
    expect(await useStore.getState().dsCompleto()).toBe(ds);
    useStore.getState().parar();
  });
});
