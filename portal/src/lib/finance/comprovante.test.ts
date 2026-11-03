import { describe, expect, it } from "vitest";
import { idDoNumeroComprovante, montarComprovante, numeroComprovante } from "./comprovante";
import { cartao, conta, dataset, desp, dt } from "./fixtures";

const agora = dt(2025, 9, 20);
const ds = dataset({
  contas: [conta({ conta: "123456-7", banco: "Itaú", agencia: "0001" }), conta({ id: 2, conta: "999", banco: "Nubank" })],
  cartoes: [
    cartao({ id: 10, nome: "Visa", finalCartao: "9876", diaFechamento: 25, diaVencimento: 5 }),
    cartao({ id: 11, nome: "Virtual", finalCartao: "1111", cartaoPrincipalId: 10 }),
  ],
  despesasFixas: [{ id: 1, descricao: "Netflix", valor: 55.9, conta: "123456-7", categoria: "Lazer", pic: "", tipo: "DEBITO", diaVencimento: 12, ultimaDataLancamento: null, cartaoId: null }],
  despesas: [
    desp({ id: 1, descricao: "Sofá (1/3)", valor: 100, cartaoId: 10, pago: false, grupoId: "parc:a", data: dt(2025, 8, 10) }),
    desp({ id: 2, descricao: "Sofá (2/3)", valor: 100, cartaoId: 10, pago: false, grupoId: "parc:a", data: dt(2025, 9, 10) }),
    desp({ id: 3, descricao: "Sofá (3/3)", valor: 100.01, cartaoId: 10, pago: false, grupoId: "parc:a", data: dt(2025, 10, 10) }),
    desp({ id: 4, descricao: "Transf", valor: 50, natureza: "TRANSFERENCIA", grupoId: "transf:x", conta: "123456-7" }),
    desp({ id: 5, descricao: "Transf", valor: 50, natureza: "TRANSFERENCIA", grupoId: "transf:x", conta: "999", tipo: "CREDITO" }),
    desp({ id: 6, descricao: "Netflix", valor: 55.9, categoria: "Lazer", conta: "123456-7", pago: false, data: dt(2025, 9, 28) }),
    desp({ id: 7, descricao: "Almoço", valor: 30, conta: "123456-7", grupoId: "debito:11", data: dt(2025, 9, 11) }),
  ],
});
const por = (id: number) => ds.despesas.find((d) => d.id === id)!;
const v = (m: ReturnType<typeof montarComprovante>, r: string) => m.linhas.find((l) => l.rotulo === r)?.valor;

describe("R48 montarComprovante", () => {
  it("compra parcelada no cartão: fatura, parcela, total e restantes", () => {
    const m = montarComprovante(por(2), ds, agora);
    expect(m.selo.id).toBe("DESPESA");
    expect(m.status.id).toBe("PENDENTE");
    expect(v(m, "Descrição")).toBe("Sofá");
    expect(v(m, "Parcela")).toBe("2/3");
    expect(v(m, "Parcelas restantes")).toBe("1");
    expect(v(m, "Valor total da compra")).toContain("300,01");
    expect(v(m, "Fatura de referência")).toBe("09/2025"); // dia 10 <= fechamento 25
    expect(v(m, "Cartão")).toBe("Visa •••• 9876");
    expect(v(m, "Tipo do cartão")).toBe("Físico");
    expect(v(m, "Modalidade")).toBe("Crédito");
    expect(v(m, "Vencimento da fatura")).toBeDefined();
    expect(m.valor.startsWith("−")).toBe(true);
    expect(m.id).toBe("MD-000002");
  });

  it("não emite linhas vazias, mascara a conta e acha a recorrência fixa", () => {
    const m = montarComprovante(por(6), ds, agora);
    expect(m.linhas.every((l) => l.valor.trim() !== "")).toBe(true);
    expect(v(m, "Conta de origem")).toBe("Itaú · ag. 0001 · •••• 4567");
    expect(v(m, "Recorrência")).toBe("Despesa fixa · todo dia 12");
    expect(m.linhas.some((l) => l.rotulo === "Parcela" || l.rotulo === "Cartão")).toBe(false);
    expect(v(m, "Vencimento")).toBeDefined();
  });

  it("status vencido/pago e transferência com origem → destino", () => {
    expect(montarComprovante(por(6), ds, dt(2025, 10, 5)).status.id).toBe("VENCIDO");
    expect(montarComprovante(por(4), ds, agora).status.id).toBe("PAGO");
    const t = montarComprovante(por(4), ds, agora);
    expect(t.selo.id).toBe("TRANSFERENCIA");
    expect(v(t, "Transferência")).toBe("Itaú → Nubank");
    expect(v(montarComprovante(por(5), ds, agora), "Transferência")).toBe("Itaú → Nubank");
  });

  it("débito no cartão mostra modalidade Débito e cartão virtual", () => {
    const m = montarComprovante(por(7), ds, agora);
    expect(v(m, "Modalidade")).toBe("Débito");
    expect(v(m, "Cartão")).toBe("Virtual •••• 1111");
    expect(v(m, "Tipo do cartão")).toBe("Virtual");
  });
});

describe("R81 número de controle do comprovante", () => {
  it("usa o id completo (sem truncar) e é único por lançamento", () => {
    expect(numeroComprovante(7)).toBe("MD-000007");
    expect(numeroComprovante(1_234_567_890)).toBe("MD-1234567890");
    expect(numeroComprovante(1_234_567_890)).not.toBe(numeroComprovante(1_334_567_890)); // antes truncava nos 6 últimos dígitos
    expect(montarComprovante(por(2), ds, agora).id).toBe("MD-000002");
    const todos = ds.despesas.map((d) => numeroComprovante(d.id));
    expect(new Set(todos).size).toBe(todos.length);
  });
  it("id 0 (não salvo) não ganha número", () => {
    expect(numeroComprovante(0)).toBe("MD-PENDENTE");
  });
  it("ida e volta: aceita MD-000123, md123 e 123", () => {
    expect(idDoNumeroComprovante("MD-000123")).toBe(123);
    expect(idDoNumeroComprovante(" md123 ")).toBe(123);
    expect(idDoNumeroComprovante("123")).toBe(123);
    expect(idDoNumeroComprovante("MD-PENDENTE")).toBeNull();
    expect(idDoNumeroComprovante("almoço")).toBeNull();
    expect(idDoNumeroComprovante("0")).toBeNull();
    expect(idDoNumeroComprovante(numeroComprovante(1_234_567_890))).toBe(1_234_567_890);
  });
  it("débito vinculado ao cartão sai como Débito, nunca Crédito", () => {
    const m = montarComprovante(por(7), ds, agora);
    expect(v(m, "Modalidade")).toBe("Débito");
  });
});
