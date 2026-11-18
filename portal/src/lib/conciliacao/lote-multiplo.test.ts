import { describe, expect, it } from "vitest";
import { recalcularTudo } from "../finance/calc";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import type { Dataset } from "../finance/types";
import {
  arquivosComPendencias,
  executarLote,
  inversosEmOrdemInversa,
  percentualArquivo,
  percentualGeral,
  preverLote,
  taxaConciliacao,
  type ArquivoParaLote,
  type EventoLote,
} from "./lote-multiplo";
import { aplicarConciliacao, desfazerLote, type Plano } from "./plano";
import { csvRelatorioConsolidado } from "./relatorio";
import type { Destino, TransacaoBanco } from "./tipos";

const AGORA = dt(2025, 4, 1);
const DEST: Destino = { tipo: "CONTA", conta: "111" };
const T = (fitid: string, data: number, valor: number, descricao: string): TransacaoBanco => ({ fitid, data, valor, descricao });

function base(): Dataset {
  return recalcularTudo(
    dataset({
      contas: [conta({ id: 1, conta: "111" })],
      cartoes: [cartao({ id: 10, contaId: 1 })],
      despesas: [
        desp({ id: 1, tipo: "CREDITO", valor: 5000, natureza: "SALDO_INICIAL", data: dt(2025, 2, 1), descricao: "Saldo inicial" }),
        desp({ id: 2, descricao: "Mercado Alfa", valor: 100, data: dt(2025, 3, 10), pago: false }),
        desp({ id: 3, descricao: "Farmacia Beta", valor: 50, data: dt(2025, 3, 12), pago: false }),
        desp({ id: 4, descricao: "Padaria Gama", valor: 30, data: dt(2025, 3, 20), pago: false }),
      ],
    }),
  );
}

function arq(id: string, transacoes: TransacaoBanco[]): ArquivoParaLote {
  return { id, nome: `${id}.ofx`, destino: DEST, rotuloDestino: "Banco A · 111", extrato: { formato: "OFX", transacoes, periodo: null, avisos: [] } };
}

const A = () => arq("a", [T("a1", dt(2025, 3, 10), -100, "MERCADO ALFA"), T("a2", dt(2025, 3, 12), -50, "FARMACIA BETA"), T("x1", dt(2025, 3, 15), -12.5, "CAFE DA ESQUINA")]);
// sobreposição: repete a1/a2/x1 e traz b3
const B = () => arq("b", [T("a1", dt(2025, 3, 10), -100, "MERCADO ALFA"), T("a2", dt(2025, 3, 12), -50, "FARMACIA BETA"), T("x1", dt(2025, 3, 15), -12.5, "CAFE DA ESQUINA"), T("b3", dt(2025, 3, 20), -30, "PADARIA GAMA")]);

function ambiente(ds0: Dataset, falharEm?: string) {
  let atual = ds0;
  const chamadas: Plano[] = [];
  return {
    get ds() {
      return atual;
    },
    getDs: () => atual,
    aplicar: (plano: Plano) => {
      chamadas.push(plano);
      if (falharEm && chamadas.length === 1 && plano.acoes.some((a) => a.transacao.fitid === falharEm)) return { ok: false as const, erro: "falha simulada" };
      const r = aplicarConciliacao(atual, plano, AGORA);
      if (r.ok) atual = r.ds;
      return r;
    },
    chamadas,
  };
}

const sem = async () => {};

describe("R43 percentuais", () => {
  it("por etapa e geral", () => {
    expect(percentualArquivo("FILA")).toBe(0);
    expect(percentualArquivo("CONCLUIDO")).toBe(100);
    expect(percentualGeral(0, 4, "FILA")).toBe(0);
    expect(percentualGeral(2, 4, "FILA")).toBe(50);
    expect(percentualGeral(4, 4, "FILA")).toBe(100);
    expect(percentualGeral(1, 2, "APLICANDO")).toBeGreaterThan(percentualGeral(1, 2, "COMPARANDO"));
    expect(percentualGeral(0, 0, "FILA")).toBe(0);
  });
  it("taxa de conciliação", () => {
    expect(taxaConciliacao({ transacoes: 10, conciliados: 5, criados: 2, duplicados: 1 })).toBe(0.8);
    expect(taxaConciliacao({ transacoes: 0, conciliados: 0, criados: 0, duplicados: 0 })).toBe(0);
  });
});

describe("R43 executarLote", () => {
  it("arquivo B casa contra o estado atualizado por A: fitids repetidos viram DUPLICADO e nada é duplicado", async () => {
    const env = ambiente(base());
    const r = await executarLote({ arquivos: [A(), B()], opcoes: { sugeridos: false, criar: true }, getDs: env.getDs, aplicar: env.aplicar, ceder: sem });
    const [ra, rb] = r.arquivos;
    expect(ra.contadores).toMatchObject({ conciliados: 2, criados: 1, duplicados: 0 });
    expect(rb.classes.DUPLICADO).toBe(3);
    expect(rb.contadores).toMatchObject({ conciliados: 1, criados: 0, duplicados: 3, pendentes: 0 });
    // um único lançamento por fitid
    for (const f of ["a1", "a2", "x1", "b3"]) expect(env.ds.despesas.filter((d) => d.fitid === f)).toHaveLength(1);
    expect(env.ds.despesas.filter((d) => d.conciliadoEm != null)).toHaveLength(4);
    expect(r.contadores).toMatchObject({ transacoes: 7, conciliados: 3, criados: 1, duplicados: 3 });
    expect(taxaConciliacao(r.contadores)).toBe(1);
  });

  it("falha de um arquivo não aborta os demais", async () => {
    const env = ambiente(base(), "a1");
    const eventos: EventoLote[] = [];
    const r = await executarLote({ arquivos: [A(), B()], getDs: env.getDs, aplicar: env.aplicar, onProgresso: (e) => eventos.push(e), ceder: sem });
    expect(r.arquivos[0]).toMatchObject({ status: "ERRO", erro: "falha simulada" });
    expect(r.arquivos[0].contadores.erros).toBe(1);
    expect(r.arquivos[0].inverso).toBeUndefined();
    expect(r.arquivos[1].status).toBe("CONCLUIDO");
    // B (que repete a1) concilia normalmente porque A não aplicou nada
    expect(r.arquivos[1].contadores.conciliados).toBe(3);
    expect(r.contadores.erros).toBe(1);
    expect(eventos.at(-1)?.tipo).toBe("fim");
  });

  it("exceção inesperada vira erro do arquivo", async () => {
    const env = ambiente(base());
    const r = await executarLote({
      arquivos: [A(), B()],
      getDs: env.getDs,
      aplicar: (p) => {
        if (p.acoes.some((a) => a.transacao.fitid === "a1") && env.chamadas.length === 0) {
          env.chamadas.push(p);
          throw new Error("boom");
        }
        return env.aplicar(p);
      },
      ceder: sem,
    });
    expect(r.arquivos[0]).toMatchObject({ status: "ERRO", erro: "boom" });
    expect(r.arquivos[1].status).toBe("CONCLUIDO");
  });

  it("cancelamento para após o arquivo corrente; o aplicado permanece", async () => {
    const env = ambiente(base());
    let cancelar = false;
    const r = await executarLote({
      arquivos: [A(), B(), arq("c", [T("c1", dt(2025, 3, 1), -1, "X")])],
      getDs: env.getDs,
      aplicar: env.aplicar,
      sinalCancelar: () => cancelar,
      onProgresso: (e) => {
        if (e.tipo === "arquivo" && e.indice === 0) cancelar = true;
      },
      ceder: sem,
    });
    expect(r.cancelado).toBe(true);
    expect(r.arquivos.map((a) => a.status)).toEqual(["CONCLUIDO", "CANCELADO", "CANCELADO"]);
    expect(r.arquivos[1].contadores.pendentes).toBe(4);
    expect(env.ds.despesas.filter((d) => d.conciliadoEm != null)).toHaveLength(2);
    expect(env.chamadas).toHaveLength(1);
  });

  it("eventos: percentual geral é monotônico e contadores ao vivo somam por arquivo", async () => {
    const env = ambiente(base());
    const eventos: EventoLote[] = [];
    const r = await executarLote({ arquivos: [A(), B()], opcoes: { sugeridos: false, criar: true }, getDs: env.getDs, aplicar: env.aplicar, onProgresso: (e) => eventos.push(e), ceder: sem });
    const pcts = eventos.map((e) => e.pctGeral);
    expect([...pcts].sort((x, y) => x - y)).toEqual(pcts);
    expect(pcts[0]).toBe(0);
    expect(pcts.at(-1)).toBe(100);
    const ultimaLinhas = eventos.filter((e) => e.tipo === "linhas").at(-1);
    expect(ultimaLinhas && "contadores" in ultimaLinhas ? ultimaLinhas.contadores.conciliados : -1).toBe(r.contadores.conciliados);
    const soma = r.arquivos.reduce((s, a) => s + a.contadores.conciliados + a.contadores.criados, 0);
    expect(soma).toBe(r.contadores.conciliados + r.contadores.criados);
    const linhas = eventos.flatMap((e) => (e.tipo === "linhas" ? e.linhas : []));
    expect(linhas.some((l) => l.acao === "criar")).toBe(true);
    expect(linhas.find((l) => l.lancamento === "Mercado Alfa")?.mudancas).toContain("pago");
  });

  it("padrão não cria lançamentos nem inclui sugeridos; pendentes contados", async () => {
    const env = ambiente(base());
    const r = await executarLote({ arquivos: [A()], getDs: env.getDs, aplicar: env.aplicar, ceder: sem });
    expect(r.arquivos[0].contadores).toMatchObject({ conciliados: 2, criados: 0, pendentes: 1 });
    expect(arquivosComPendencias(r)).toHaveLength(1);
  });

  it("desfazer em ordem inversa devolve o dataset original", async () => {
    const original = base();
    const env = ambiente(original);
    const r = await executarLote({ arquivos: [A(), B()], opcoes: { sugeridos: false, criar: true }, getDs: env.getDs, aplicar: env.aplicar, ceder: sem });
    expect(env.ds).not.toEqual(original);
    let ds = env.ds;
    const inversos = inversosEmOrdemInversa(r);
    expect(inversos).toHaveLength(2);
    expect(inversos[0]).toBe(r.arquivos[1].inverso);
    for (const inv of inversos) ds = desfazerLote(ds, inv);
    expect(ds).toEqual(original);
  });
});

describe("R43 prévia e relatório", () => {
  it("prévia simula em sequência sem alterar o dataset de origem", () => {
    const ds = base();
    const copia = structuredClone(ds);
    const p = preverLote(ds, [A(), B()], { sugeridos: false, criar: true }, AGORA);
    expect(ds).toEqual(copia);
    expect(p[0].contadores.criados).toBe(1);
    expect(p[1].classes.DUPLICADO).toBe(3);
  });

  it("CSV consolidado tem coluna arquivo e uma linha por transação", async () => {
    const env = ambiente(base());
    const r = await executarLote({ arquivos: [A(), B()], opcoes: { sugeridos: false, criar: true }, getDs: env.getDs, aplicar: env.aplicar, ceder: sem });
    const csv = csvRelatorioConsolidado(r.arquivos);
    const linhas = csv.replace(/^﻿/, "").trim().split("\r\n");
    expect(linhas[0]).toBe("arquivo;data;descricao;valor;situacao;lancamento_id;acao");
    expect(linhas).toHaveLength(1 + 3 + 4);
    expect(linhas.filter((l) => l.startsWith("a.ofx;"))).toHaveLength(3);
    expect(linhas.find((l) => l.includes("CAFE DA ESQUINA") && l.startsWith("a.ofx"))).toMatch(/;automatico|;so_no_extrato/);
    expect(linhas.find((l) => l.startsWith("b.ofx;") && l.includes("a1") === false && l.includes("CAFE"))).toContain(";duplicado;");
    expect(csv.charCodeAt(0)).toBe(0xfeff);
  });
});
