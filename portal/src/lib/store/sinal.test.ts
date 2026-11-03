import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AgendadorSinal } from "./sinal";

describe("AgendadorSinal (R86)", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("funde pedidos seguidos em um único sinal, enviado após o último", async () => {
    const escrever = vi.fn().mockResolvedValue(undefined);
    const a = new AgendadorSinal(escrever, 4000);
    a.pedir();
    await vi.advanceTimersByTimeAsync(3000);
    a.pedir();
    await vi.advanceTimersByTimeAsync(3000);
    expect(escrever).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1500);
    expect(escrever).toHaveBeenCalledTimes(1);
  });

  it("um novo pedido depois do envio gera outro sinal", async () => {
    const escrever = vi.fn().mockResolvedValue(undefined);
    const a = new AgendadorSinal(escrever, 1000);
    a.pedir();
    await vi.advanceTimersByTimeAsync(1100);
    a.pedir();
    await vi.advanceTimersByTimeAsync(1100);
    expect(escrever).toHaveBeenCalledTimes(2);
  });

  it("cancelar descarta o pendente e falha de escrita não propaga", async () => {
    const escrever = vi.fn().mockRejectedValue(new Error("offline"));
    const a = new AgendadorSinal(escrever, 1000);
    a.pedir();
    a.cancelar();
    await vi.advanceTimersByTimeAsync(2000);
    expect(escrever).not.toHaveBeenCalled();
    a.pedir();
    await expect(vi.advanceTimersByTimeAsync(1100)).resolves.not.toThrow();
    expect(escrever).toHaveBeenCalledTimes(1);
  });
});
