import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import {
  brlCentavos,
  calcularLimiteDiario,
  calendarioDoDataset,
  dataDeSerial,
  diaDaSemana,
  diaSerial,
  eventosPrevistos,
  limiteDiarioDoDataset,
  projetarMes,
  ultimoDiaSerial,
  type EventoProjecao,
  type TipoEvento,
} from "./projecao";
import type { DespesaFixa } from "./types";

const S = diaSerial;
const ev = (dia: number, centavos: number, tipo?: TipoEvento): EventoProjecao => ({
  dia,
  centavos,
  tipo: tipo ?? (centavos >= 0 ? "RECEITA" : "DESPESA"),
  descricao: "x",
  origem: "Banco A",
  atrasado: false,
  lancamentoId: null,
  cartaoId: null,
});

function regra(over: Partial<DespesaFixa> = {}): DespesaFixa {
  return {
    id: 1,
    descricao: "Aluguel",
    valor: 1000,
    conta: "111",
    categoria: "Moradia",
    pic: "",
    tipo: "DEBITO",
    diaVencimento: 20,
    ultimaDataLancamento: null,
    cartaoId: null,
    ...over,
  };
}

const entrada = (over: Partial<Parameters<typeof calcularLimiteDiario>[0]> = {}) => ({
  saldoCentavos: 0,
  eventos: [] as EventoProjecao[],
  hoje: S(2025, 3, 10),
  fimDoMes: S(2025, 3, 31),
  ateProximaReceita: false,
  gastosHoje: [],
  ...over,
});

describe("datas seriais", () => {
  it("converte e volta, inclusive bissexto e virada de ano", () => {
    expect(S(1970, 1, 1)).toBe(0);
    expect(dataDeSerial(S(2024, 2, 29))).toEqual({ ano: 2024, mes: 2, dia: 29 });
    expect(dataDeSerial(S(2025, 12, 31) + 1)).toEqual({ ano: 2026, mes: 1, dia: 1 });
    expect(ultimoDiaSerial(2, 2025) - S(2025, 2, 1) + 1).toBe(28);
    expect(ultimoDiaSerial(2, 2024) - S(2024, 2, 1) + 1).toBe(29);
    expect(ultimoDiaSerial(12, 2025)).toBe(S(2025, 12, 31));
    expect(diaDaSemana(S(2025, 3, 10))).toBe(1); // segunda
  });
  it("formata reais", () => {
    expect(brlCentavos(123456)).toBe("R$ 1.234,56");
    expect(brlCentavos(5)).toBe("R$ 0,05");
    expect(brlCentavos(-1000)).toBe("-R$ 10,00");
  });
});

describe("R51 limite diário (função pura)", () => {
  it("sem receitas: saldo menos contas a pagar dividido pelos dias restantes", () => {
    const r = calcularLimiteDiario(entrada({ saldoCentavos: 100000, eventos: [ev(S(2025, 3, 20), -30000)] }));
    expect(r.disponivelCentavos).toBe(70000);
    expect(r.diasRestantes).toBe(22);
    expect(r.limiteDiarioCentavos).toBe(3181);
    expect(r.restanteHojeCentavos).toBe(3181);
    expect(r.status).toBe("VERDE");
    expect(r.mensagem).toBe("Hoje você pode gastar até R$ 31,81.");
  });

  it("mês no vermelho explica quanto falta", () => {
    const r = calcularLimiteDiario(entrada({ saldoCentavos: 10000, eventos: [ev(S(2025, 3, 15), -25000)] }));
    expect(r.mesNoVermelho).toBe(true);
    expect(r.limiteDiarioCentavos).toBe(0);
    expect(r.faltaCentavos).toBe(15000);
    expect(r.status).toBe("VERMELHO");
    expect(r.mensagem).toBe("Mês no vermelho: faltam R$ 150,00 para cobrir tudo até 31/03.");
  });

  it("disponível exatamente zero também é mês no vermelho", () => {
    const r = calcularLimiteDiario(entrada({ saldoCentavos: 10000, eventos: [ev(S(2025, 3, 15), -10000)] }));
    expect(r.mesNoVermelho).toBe(true);
    expect(r.mensagem).toBe("Mês no vermelho: não sobra nada para gastar até 31/03.");
  });

  it("receitas previstas entram no disponível; as de depois do fim do mês, não", () => {
    const r = calcularLimiteDiario(
      entrada({ saldoCentavos: 0, eventos: [ev(S(2025, 3, 31), 220000), ev(S(2025, 4, 1), 999999)] }),
    );
    expect(r.disponivelCentavos).toBe(220000);
    expect(r.limiteDiarioCentavos).toBe(10000); // 22 dias
  });

  it("até a próxima receita: período termina no dia anterior", () => {
    const r = calcularLimiteDiario(
      entrada({
        saldoCentavos: 100000,
        ateProximaReceita: true,
        eventos: [ev(S(2025, 3, 12), -20000), ev(S(2025, 3, 15), 500000), ev(S(2025, 3, 20), -1000)],
      }),
    );
    expect(r.fimPeriodo).toBe(S(2025, 3, 14));
    expect(r.diasRestantes).toBe(5);
    expect(r.disponivelCentavos).toBe(80000);
    expect(r.limiteDiarioCentavos).toBe(16000);
  });

  it("até a próxima receita sem receita prevista cai no fim do mês", () => {
    const r = calcularLimiteDiario(entrada({ saldoCentavos: 22000, ateProximaReceita: true }));
    expect(r.fimPeriodo).toBe(S(2025, 3, 31));
  });

  it("último dia do mês: 1 dia restante", () => {
    const r = calcularLimiteDiario(entrada({ hoje: S(2025, 3, 31), saldoCentavos: 5000 }));
    expect(r.diasRestantes).toBe(1);
    expect(r.limiteDiarioCentavos).toBe(5000);
  });

  it("fevereiro bissexto e não bissexto", () => {
    const a = calcularLimiteDiario(entrada({ hoje: S(2024, 2, 15), fimDoMes: S(2024, 2, 29), saldoCentavos: 150000 }));
    expect(a.diasRestantes).toBe(15);
    expect(a.limiteDiarioCentavos).toBe(10000);
    const b = calcularLimiteDiario(entrada({ hoje: S(2025, 2, 15), fimDoMes: S(2025, 2, 28), saldoCentavos: 140000 }));
    expect(b.diasRestantes).toBe(14);
    expect(b.limiteDiarioCentavos).toBe(10000);
  });

  it("virada de ano: próxima receita em janeiro", () => {
    const r = calcularLimiteDiario(
      entrada({
        hoje: S(2025, 12, 28),
        fimDoMes: S(2025, 12, 31),
        saldoCentavos: 80000,
        ateProximaReceita: true,
        eventos: [ev(S(2026, 1, 5), 300000)],
      }),
    );
    expect(r.fimPeriodo).toBe(S(2026, 1, 4));
    expect(r.diasRestantes).toBe(8);
    expect(r.limiteDiarioCentavos).toBe(10000);
    expect(r.mensagem).toBe("Hoje você pode gastar até R$ 100,00.");
  });

  it("status AMARELO a partir de 80% e VERMELHO ao estourar (gasto já pago não é descontado duas vezes)", () => {
    const base = { hoje: S(2025, 3, 22) }; // 10 dias
    const amarelo = calcularLimiteDiario(entrada({ ...base, saldoCentavos: 92000, gastosHoje: [{ centavos: 8000, vencimentoFatura: null }] }));
    expect(amarelo.limiteDiarioCentavos).toBe(10000);
    expect(amarelo.status).toBe("AMARELO");
    expect(amarelo.restanteHojeCentavos).toBe(2000);
    expect(amarelo.mensagem).toBe("Atenção: restam R$ 20,00 do limite de hoje.");
    const verde = calcularLimiteDiario(entrada({ ...base, saldoCentavos: 93000, gastosHoje: [{ centavos: 7000, vencimentoFatura: null }] }));
    expect(verde.status).toBe("VERDE");
    const verm = calcularLimiteDiario(entrada({ ...base, saldoCentavos: 88000, gastosHoje: [{ centavos: 12000, vencimentoFatura: null }] }));
    expect(verm.status).toBe("VERMELHO");
    expect(verm.mesNoVermelho).toBe(false);
    expect(verm.restanteHojeCentavos).toBe(-2000);
    expect(verm.mensagem).toBe("Hoje você já passou do limite em R$ 20,00.");
  });

  it("compra de hoje no cartão: só é 'já deduzida' se a fatura vence dentro do período", () => {
    const hoje = S(2025, 3, 22);
    const fim = S(2025, 3, 31);
    const venceFora = calcularLimiteDiario(
      entrada({ hoje, saldoCentavos: 100000, gastosHoje: [{ centavos: 5000, vencimentoFatura: S(2025, 4, 5) }] }),
    );
    expect(venceFora.limiteDiarioCentavos).toBe(10000);
    expect(venceFora.gastoHojeCentavos).toBe(5000);
    expect(venceFora.status).toBe("VERDE");
    const venceDentro = calcularLimiteDiario(
      entrada({
        hoje,
        saldoCentavos: 100000,
        eventos: [ev(fim, -5000, "FATURA")],
        gastosHoje: [{ centavos: 5000, vencimentoFatura: fim }],
      }),
    );
    expect(venceDentro.disponivelCentavos).toBe(95000);
    expect(venceDentro.limiteDiarioCentavos).toBe(10000);
  });
});

describe("R52 calendário (função pura)", () => {
  const hoje = S(2025, 3, 10);

  it("acumula movimentos previstos; dias passados não têm projeção; menor saldo e alerta", () => {
    const r = projetarMes({
      saldoCentavos: 100000,
      hoje,
      mes: 3,
      ano: 2025,
      eventos: [ev(S(2025, 3, 20), -150000), ev(S(2025, 3, 28), 300000), ev(S(2025, 3, 31), -10000)],
    });
    expect(r.dias).toHaveLength(31);
    expect(r.dias[8].saldoProjetadoCentavos).toBeNull(); // 09/03
    expect(r.dias[9].saldoProjetadoCentavos).toBe(100000); // 10/03 (hoje)
    expect(r.dias[9].ehHoje).toBe(true);
    expect(r.dias[19].saldoProjetadoCentavos).toBe(-50000); // 20/03
    expect(r.dias[19].negativo).toBe(true);
    expect(r.dias[27].saldoProjetadoCentavos).toBe(250000);
    expect(r.saldoFimDoMesCentavos).toBe(240000);
    expect(r.menorSaldo).toEqual({ centavos: -50000, dia: S(2025, 3, 20) });
    expect(r.primeiroDiaNegativo).toBe(S(2025, 3, 20));
    expect(r.alerta).toBe("Conta ficará negativa em 20/03 antes do próximo recebimento.");
  });

  it("sem receitas: alerta informa que não há recebimento previsto", () => {
    const r = projetarMes({ saldoCentavos: 1000, hoje, mes: 3, ano: 2025, eventos: [ev(S(2025, 3, 12), -5000)] });
    expect(r.alerta).toBe("Conta ficará negativa em 12/03 e não há recebimento previsto.");
  });

  it("mês sem saldo negativo não gera alerta", () => {
    const r = projetarMes({ saldoCentavos: 50000, hoje, mes: 3, ano: 2025, eventos: [ev(S(2025, 3, 12), -5000)] });
    expect(r.alerta).toBeNull();
    expect(r.menorSaldo).toEqual({ centavos: 45000, dia: S(2025, 3, 12) });
  });

  it("mês futuro leva em conta o que acontece antes dele", () => {
    const r = projetarMes({
      saldoCentavos: 100000,
      hoje,
      mes: 4,
      ano: 2025,
      eventos: [ev(S(2025, 3, 20), -40000), ev(S(2025, 4, 5), -30000)],
    });
    expect(r.dias[0].saldoProjetadoCentavos).toBe(60000); // 01/04
    expect(r.dias[4].saldoProjetadoCentavos).toBe(30000); // 05/04
    expect(r.dias[0].passado).toBe(false);
  });

  it("mês passado não tem projeção", () => {
    const r = projetarMes({ saldoCentavos: 100000, hoje, mes: 2, ano: 2025, eventos: [] });
    expect(r.dias).toHaveLength(28);
    expect(r.dias.every((d) => d.saldoProjetadoCentavos === null && d.passado)).toBe(true);
    expect(r.menorSaldo).toBeNull();
    expect(r.alerta).toBeNull();
  });

  it("virada de ano e fevereiro bissexto", () => {
    const d = S(2025, 12, 20);
    const r = projetarMes({ saldoCentavos: 10000, hoje: d, mes: 1, ano: 2026, eventos: [ev(S(2026, 1, 1), -20000)] });
    expect(r.dias).toHaveLength(31);
    expect(r.dias[0].saldoProjetadoCentavos).toBe(-10000);
    expect(r.alerta).toBe("Conta ficará negativa em 01/01 e não há recebimento previsto.");
    expect(projetarMes({ saldoCentavos: 0, hoje: d, mes: 2, ano: 2028, eventos: [] }).dias).toHaveLength(29);
  });
});

describe("eventos previstos a partir dos dados", () => {
  const base = () => dataset({ contas: [conta({ id: 1, conta: "111", banco: "Banco A" })] });

  it("recorrência pausada é ignorada; ativa vira evento no dia (clamp no fim do mês)", () => {
    const hoje = dt(2025, 2, 10);
    const ate = S(2025, 3, 31);
    const ativa = dataset({ ...base(), despesasFixas: [regra({ diaVencimento: 31 })] });
    const e1 = eventosPrevistos(ativa, hoje, { ate });
    expect(e1.map((e) => [e.dia, e.centavos, e.tipo])).toEqual([
      [S(2025, 2, 28), -100000, "RECORRENCIA_DESPESA"],
      [S(2025, 3, 31), -100000, "RECORRENCIA_DESPESA"],
    ]);
    const pausada = dataset({ ...base(), despesasFixas: [regra({ diaVencimento: 31, pausada: true, pausadaAte: null })] });
    expect(eventosPrevistos(pausada, hoje, { ate })).toEqual([]);
    const ate15mar = dt(2025, 3, 15);
    const comPrazo = dataset({ ...base(), despesasFixas: [regra({ diaVencimento: 31, pausada: true, pausadaAte: ate15mar })] });
    expect(eventosPrevistos(comPrazo, hoje, { ate }).map((e) => e.dia)).toEqual([S(2025, 3, 31)]);
  });

  it("recorrência já lançada no mês não duplica; receita recorrente é recebimento", () => {
    const hoje = dt(2025, 12, 20);
    const ds = dataset({
      ...base(),
      despesasFixas: [
        regra({ id: 1, diaVencimento: 5, ultimaDataLancamento: dt(2025, 12, 5) }),
        regra({ id: 2, descricao: "Salário", valor: 3000, tipo: "CREDITO", diaVencimento: 5 }),
      ],
    });
    const e = eventosPrevistos(ds, hoje, { ate: S(2026, 1, 31) });
    expect(e.map((x) => [x.descricao, x.dia, x.centavos, x.tipo])).toEqual([
      ["Salário", S(2025, 12, 20), 300000, "RECORRENCIA_RECEITA"], // dezembro ainda não lançado: entra em hoje
      ["Salário", S(2026, 1, 5), 300000, "RECORRENCIA_RECEITA"],
      ["Aluguel", S(2026, 1, 5), -100000, "RECORRENCIA_DESPESA"],
    ]);
  });

  it("fatura de cartão entra no vencimento com o total; paga ou só estorno não entra", () => {
    const ds = dataset({
      ...base(),
      cartoes: [cartao({ id: 10, nome: "Nubank", diaFechamento: 25, diaVencimento: 5 })],
      despesas: [
        desp({ descricao: "Compra A", valor: 200, data: dt(2025, 3, 10), cartaoId: 10, pago: false }),
        desp({ descricao: "Compra B", valor: 100, data: dt(2025, 3, 11), cartaoId: 10, pago: false }),
        desp({ descricao: "Antiga", valor: 999, data: dt(2025, 1, 11), cartaoId: 10, pago: true }),
      ],
    });
    const e = eventosPrevistos(ds, dt(2025, 3, 12), { ate: S(2025, 4, 30) });
    expect(e).toHaveLength(1);
    expect(e[0]).toMatchObject({ dia: S(2025, 4, 5), centavos: -30000, tipo: "FATURA", descricao: "Fatura Nubank", cartaoId: 10 });
  });

  it("fatura vencida vira atrasada e cai em hoje; pendência de conta idem", () => {
    const ds = dataset({
      ...base(),
      cartoes: [cartao({ id: 10, diaFechamento: 25, diaVencimento: 5 })],
      despesas: [
        desp({ valor: 80, data: dt(2025, 2, 10), cartaoId: 10, pago: false }),
        desp({ valor: 50, data: dt(2025, 3, 1), pago: false, descricao: "Luz" }),
      ],
    });
    const e = eventosPrevistos(ds, dt(2025, 3, 12), { ate: S(2025, 4, 30) });
    expect(e.every((x) => x.dia === S(2025, 3, 12) && x.atrasado)).toBe(true);
    expect(e.map((x) => x.centavos).sort((a, b) => a - b)).toEqual([-8000, -5000]);
  });

  it("compra recorrente no crédito soma na fatura do ciclo, não na conta", () => {
    const ds = dataset({
      ...base(),
      cartoes: [cartao({ id: 10, nome: "Nubank", diaFechamento: 25, diaVencimento: 5 })],
      despesasFixas: [regra({ descricao: "Streaming", valor: 50, diaVencimento: 10, cartaoId: 10 })],
    });
    const e = eventosPrevistos(ds, dt(2025, 3, 1), { ate: S(2025, 4, 30) });
    expect(e.map((x) => [x.tipo, x.dia, x.centavos])).toEqual([
      ["FATURA", S(2025, 4, 5), -5000],
      ["FATURA", S(2025, 5, 5), -5000],
    ]);
  });

  it("ignora pendências que não são NORMAL e transferências", () => {
    const ds = dataset({ ...base(), despesas: [desp({ pago: false, natureza: "TRANSFERENCIA", data: dt(2025, 3, 20) })] });
    expect(eventosPrevistos(ds, dt(2025, 3, 12), { ate: S(2025, 4, 30) })).toEqual([]);
  });

  it("filtro de contas", () => {
    const ds = dataset({
      contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222", banco: "Banco B" })],
      despesas: [
        desp({ conta: "111", pago: false, valor: 10, data: dt(2025, 3, 20) }),
        desp({ conta: "222", pago: false, valor: 20, data: dt(2025, 3, 20) }),
      ],
    });
    const e = eventosPrevistos(ds, dt(2025, 3, 12), { ate: S(2025, 4, 30), contas: ["222"] });
    expect(e.map((x) => x.centavos)).toEqual([-2000]);
  });
});

describe("limite diário e calendário sobre o dataset", () => {
  it("cenário completo: saldo + receita pendente - despesa - fatura - recorrência", () => {
    const ds = dataset({
      contas: [conta({ id: 1, conta: "111" })],
      cartoes: [cartao({ id: 10, diaFechamento: 25, diaVencimento: 28 })],
      despesas: [
        desp({ tipo: "CREDITO", valor: 1000, pago: true, data: dt(2025, 3, 1), natureza: "SALDO_INICIAL" }),
        desp({ tipo: "CREDITO", valor: 500, pago: false, data: dt(2025, 3, 15), descricao: "Freela" }),
        desp({ valor: 200, pago: false, data: dt(2025, 3, 20), descricao: "Luz" }),
        desp({ valor: 300, pago: false, data: dt(2025, 3, 5), cartaoId: 10, descricao: "Compra" }),
      ],
      despesasFixas: [regra({ valor: 100, diaVencimento: 25 })],
    });
    // saldo 1000 + 500 - 200 - 300 (fatura vence 28/03) - 100 (recorrência) = 900; hoje 10/03 -> 22 dias
    const r = limiteDiarioDoDataset(ds, dt(2025, 3, 10));
    expect(r.disponivelCentavos).toBe(90000);
    expect(r.diasRestantes).toBe(22);
    expect(r.limiteDiarioCentavos).toBe(4090);
    const aoReceber = limiteDiarioDoDataset(ds, dt(2025, 3, 10), { ateProximaReceita: true });
    expect(aoReceber.fimPeriodo).toBe(S(2025, 3, 14));
    expect(aoReceber.disponivelCentavos).toBe(100000);
  });

  it("calendário do dataset: fatura em abril e saldo projetado", () => {
    const ds = dataset({
      contas: [conta({ id: 1, conta: "111" })],
      cartoes: [cartao({ id: 10, diaFechamento: 25, diaVencimento: 5 })],
      despesas: [
        desp({ tipo: "CREDITO", valor: 1000, pago: true, data: dt(2025, 3, 1) }),
        desp({ valor: 300, pago: false, data: dt(2025, 3, 10), cartaoId: 10 }),
      ],
    });
    const r = calendarioDoDataset(ds, dt(2025, 3, 12), 4, 2025);
    expect(r.dias[3].saldoProjetadoCentavos).toBe(100000);
    expect(r.dias[4].saldoProjetadoCentavos).toBe(70000);
    expect(r.dias[4].eventos[0].tipo).toBe("FATURA");
    expect(r.saldoFimDoMesCentavos).toBe(70000);
  });
});
