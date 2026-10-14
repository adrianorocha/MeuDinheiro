/**
 * R69 - PNG/JPEG do relatório de pessoas, desenhado em canvas (sem dependências).
 *
 * Decisão para listas longas: a imagem NUNCA passa de MAX_ALTURA px (6000, escala 1:1; 1200 x 6000 cabe em qualquer
 * navegador, inclusive móvel). Acima disso o conteúdo é dividido em várias imagens numeradas (`pessoas_AAAA-MM-DD-1.png`,
 * `-2`…), cada uma com cabeçalho de continuação e rodapé "Parte i de N"; nenhum acerto é omitido (o PDF continua sendo o
 * formato paginado). Cabeçalho de pessoa nunca fica sozinho no fim de uma parte.
 *
 * `planejarImagens` é PURA (recebe um medidor de texto) e é o que os testes cobrem; `gerarImagensPessoas` só desenha o plano.
 */
import type { LinhaRelatorioPessoas, PessoaRelatorio, RelatorioPessoas } from "../finance/relatorioPessoas";
import { brlCentavos } from "../finance/centavos";
import type { ItemRateio } from "../finance/rateios";
import { dataBR } from "../finance/relatorioPessoas";
import { quebrarTexto } from "./comprovante";
import { alvoCanvas, COR, corAvatar, desenharIcone, type NomeIcone } from "./pessoasIcones";

export const LARGURA_IMG = 1200;
export const MAX_ALTURA = 6000;
const M = 48;
const CW = LARGURA_IMG - 2 * M;
const H_CONT = 96;
const H_RODAPE = 64;

export const fonteImg = (peso: string | number, tam: number) => `${peso} ${tam}px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`;

export type Medidor = Pick<CanvasRenderingContext2D, "measureText" | "font">;

export type Bloco =
  | { tipo: "titulo"; h: number; texto: string; manter: true }
  | { tipo: "pessoa"; h: number; pessoa: PessoaRelatorio; manter: true }
  | { tipo: "linha"; h: number; linha: LinhaRelatorioPessoas; desc: string[]; origem: string[]; rateio: string[]; mostrarPessoa: boolean; corPessoa: string; iniciais: string }
  | { tipo: "rateio"; h: number; item: ItemRateio; l1: string[]; l2: string[] }
  | { tipo: "rateioTotais"; h: number; total: number; terceiros: number; liquido: number };

export interface PaginaImagem {
  indice: number;
  total: number;
  blocos: Bloco[];
  altura: number;
  /** altura do cabeçalho desta parte (completo na primeira) */
  hCabecalho: number;
}

export interface PlanoImagens {
  paginas: PaginaImagem[];
  filtrosLinhas: string[];
  hCabecalho1: number;
}

function quebrar(m: Medidor, texto: string, peso: string | number, tam: number, max: number): string[] {
  m.font = fonteImg(peso, tam);
  return quebrarTexto(m, texto, max);
}

const H_RESUMO = 2 * 118 + 20;

export function planejarImagens(rel: RelatorioPessoas, m: Medidor, maxAltura = MAX_ALTURA): PlanoImagens {
  const filtrosLinhas = quebrar(m, rel.filtros.join("   ·   "), 400, 18, CW);
  const hCabecalho1 = 12 + 28 + 64 + 18 + 48 + 34 + filtrosLinhas.length * 26 + 22 + H_RESUMO + 24;

  const blocos: Bloco[] = [];
  const cronologico = rel.agrupar === "CRONOLOGICO" && rel.incluirDetalhes;
  const textoW = CW - 72 - 250;

  const linhaBloco = (l: LinhaRelatorioPessoas, p: PessoaRelatorio | undefined, mostrarPessoa: boolean): Bloco => {
    const desc = quebrar(m, l.descricao, 600, 22, textoW);
    const origem = quebrar(m, `${l.dataTexto} · ${l.origem}`, 400, 18, textoW);
    const rateio = l.rateio ? quebrar(m, l.rateio.texto, 500, 17, textoW - 26) : [];
    const texto = (mostrarPessoa ? 34 : 0) + desc.length * 28 + origem.length * 24 + rateio.length * 24;
    return {
      tipo: "linha",
      h: Math.max(texto + 30, 104),
      linha: l,
      desc,
      origem,
      rateio,
      mostrarPessoa,
      corPessoa: corAvatar(p?.corIndice ?? 0),
      iniciais: p?.iniciais ?? "?",
    };
  };

  const pessoaPorId = new Map(rel.pessoas.map((p) => [p.pessoaId, p]));
  if (cronologico) {
    blocos.push({ tipo: "titulo", h: 64, texto: "Saldo por pessoa", manter: true });
    for (const p of rel.pessoas) blocos.push({ tipo: "pessoa", h: 156, pessoa: p, manter: true });
    blocos.push({ tipo: "titulo", h: 64, texto: "Acertos em ordem cronológica", manter: true });
    for (const l of rel.cronologico) blocos.push(linhaBloco(l, pessoaPorId.get(l.pessoaId), true));
  } else {
    for (const p of rel.pessoas) {
      blocos.push({ tipo: "pessoa", h: 156, pessoa: p, manter: true });
      for (const l of p.linhas) blocos.push(linhaBloco(l, p, false));
    }
  }
  if (rel.rateios) {
    blocos.push({ tipo: "titulo", h: 64, texto: "Rateios · quanto você realmente gastou (informativo)", manter: true });
    for (const item of rel.rateios.itens) {
      const l1 = quebrar(m, `${dataBR(item.data)} · ${item.descricao}${item.orfao ? " (lançamento removido)" : ""}`, 600, 21, CW - 16);
      const l2 = quebrar(
        m,
        item.orfao ? `A receber de terceiros ${brlCentavos(item.aReceber)}` : `Total ${brlCentavos(item.total)} · A receber de terceiros ${brlCentavos(item.aReceber)} · Você gastou ${brlCentavos(item.liquido)}`,
        400,
        19,
        CW - 16,
      );
      blocos.push({ tipo: "rateio", h: 20 + l1.length * 28 + l2.length * 26 + 14, item, l1, l2 });
    }
    blocos.push({ tipo: "rateioTotais", h: 112, total: rel.rateios.totalRateado, terceiros: rel.rateios.aReceberTerceiros, liquido: rel.rateios.liquido });
  }

  // Empacota em partes de no máximo `maxAltura` (cabeçalho + conteúdo + rodapé); "manter" não fica sozinho no fim da parte.
  const partes: Bloco[][] = [[]];
  let usado = 0;
  let capacidade = maxAltura - hCabecalho1 - H_RODAPE;
  for (let i = 0; i < blocos.length; i++) {
    const b = blocos[i];
    let precisa = b.h;
    if ("manter" in b && b.manter) {
      // cabeçalho/título + o próximo bloco (e, se o próximo também for "manter", só ele)
      const prox = blocos[i + 1];
      if (prox) precisa += prox.h;
    }
    if (usado + precisa > capacidade && partes[partes.length - 1].length > 0) {
      partes.push([]);
      usado = 0;
      capacidade = maxAltura - H_CONT - H_RODAPE;
    }
    partes[partes.length - 1].push(b);
    usado += b.h;
  }

  const total = partes.length;
  const paginas = partes.map((bl, i): PaginaImagem => {
    const hCab = i === 0 ? hCabecalho1 : H_CONT;
    return { indice: i + 1, total, blocos: bl, hCabecalho: hCab, altura: hCab + bl.reduce((s, b) => s + b.h, 0) + H_RODAPE };
  });
  return { paginas, filtrosLinhas, hCabecalho1 };
}

// ------------------------------------------------------------------ desenho

type Ctx = CanvasRenderingContext2D;

function icone(ctx: Ctx, nome: NomeIcone, x: number, y: number, tam: number, cor: string, esp = 2) {
  desenharIcone(alvoCanvas(ctx, cor, esp), nome, x, y, tam);
}

function texto(ctx: Ctx, t: string, x: number, y: number, peso: string | number, tam: number, cor: string, alinha: CanvasTextAlign = "left") {
  ctx.font = fonteImg(peso, tam);
  ctx.fillStyle = cor;
  ctx.textAlign = alinha;
  ctx.fillText(t, x, y);
  ctx.textAlign = "left";
}

function larg(ctx: Ctx, t: string, peso: string | number, tam: number) {
  ctx.font = fonteImg(peso, tam);
  return ctx.measureText(t).width;
}

function avatar(ctx: Ctx, cx: number, cy: number, r: number, iniciais: string, cor: string) {
  ctx.fillStyle = cor;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.font = fonteImg(700, Math.round(r * 0.85));
  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "center";
  ctx.fillText(iniciais, cx, cy + r * 0.3);
  ctx.textAlign = "left";
}

function pilula(ctx: Ctx, t: string, xDireita: number, y: number, fundo: string, cor: string, nome: NomeIcone) {
  const w = larg(ctx, t, 700, 17) + 52;
  ctx.fillStyle = fundo;
  ctx.beginPath();
  ctx.roundRect(xDireita - w, y, w, 28, 14);
  ctx.fill();
  icone(ctx, nome, xDireita - w + 10, y + 5, 18, cor, 2);
  texto(ctx, t, xDireita - w + 34, y + 20, 700, 17, cor);
}

function corSaldo(s: PessoaRelatorio["sentido"]) {
  return s === "TE_DEVE" ? COR.verde : s === "VOCE_DEVE" ? COR.laranja : COR.cinza;
}

function desenharPessoa(ctx: Ctx, p: PessoaRelatorio, y0: number) {
  const y = y0 + 20;
  const h = 136;
  const cor = corAvatar(p.corIndice);
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(M, y, CW, h, 18);
  ctx.clip();
  ctx.fillStyle = COR.cinzaClaro;
  ctx.fillRect(M, y, CW, h);
  ctx.fillStyle = cor;
  ctx.fillRect(M, y, 8, h);
  ctx.restore();
  avatar(ctx, M + 64, y + 50, 32, p.iniciais, cor);
  // nome (reduz a fonte até caber; nunca corta)
  let tam = 32;
  const maxNome = CW - 116 - 20 - 380;
  while (tam > 20 && larg(ctx, p.nome, 700, tam) > maxNome) tam -= 2;
  texto(ctx, p.nome, M + 112, y + 50, 700, tam, COR.tinta);
  if (p.contato) {
    let t2 = 19;
    while (t2 > 14 && larg(ctx, p.contato, 400, t2) > maxNome) t2 -= 1;
    texto(ctx, p.contato, M + 112, y + 80, 400, t2, COR.cinza);
  }
  // saldo (ícone + texto, nunca só cor)
  const cs = corSaldo(p.sentido);
  const direita = M + CW - 24;
  let ts = 30;
  while (ts > 18 && larg(ctx, p.saldoTexto, 700, ts) > 340) ts -= 2;
  const wSaldo = larg(ctx, p.saldoTexto, 700, ts);
  texto(ctx, p.saldoTexto, direita, y + 46, 700, ts, cs, "right");
  icone(ctx, p.sentido === "QUITES" ? "check" : "balanca", direita - wSaldo - 40, y + 20, 30, cs, 2.2);
  texto(ctx, "saldo pendente (a receber − a pagar)", direita, y + 74, 400, 16, COR.cinza, "right");
  // subtotais
  const itens: [NomeIcone, string, string][] = [
    ["entrada", `A receber ${brlCentavos(p.aReceber)}`, COR.verde],
    ["saida", `A pagar ${brlCentavos(p.aPagar)}`, COR.laranja],
    ["check", `Quitado ${brlCentavos(p.quitadoAReceber + p.quitadoAPagar)}`, COR.quitado],
  ];
  let x = M + 28;
  for (const [ic, t, c] of itens) {
    icone(ctx, ic, x, y + h - 42, 22, c, 2);
    texto(ctx, t, x + 30, y + h - 24, 600, 19, c);
    x += 30 + larg(ctx, t, 600, 19) + 36;
  }
}

function desenharLinha(ctx: Ctx, b: Extract<Bloco, { tipo: "linha" }>, y0: number) {
  const l = b.linha;
  const recebe = l.tipo === "A_RECEBER";
  const cor = recebe ? COR.verde : COR.laranja;
  const fundo = recebe ? COR.verdeFundo : COR.laranjaFundo;
  const textoX = M + 72;
  // ícone do tipo
  ctx.fillStyle = fundo;
  ctx.beginPath();
  ctx.arc(M + 26, y0 + 38, 22, 0, Math.PI * 2);
  ctx.fill();
  icone(ctx, recebe ? "entrada" : "saida", M + 15, y0 + 27, 22, cor, 2.2);

  let ty = y0 + 12;
  if (b.mostrarPessoa) {
    avatar(ctx, textoX + 14, ty + 15, 14, b.iniciais, b.corPessoa);
    texto(ctx, l.pessoaNome, textoX + 38, ty + 21, 600, 20, b.corPessoa);
    ty += 34;
  }
  for (const t of b.desc) {
    texto(ctx, t, textoX, ty + 22, 600, 22, COR.tinta);
    ty += 28;
  }
  for (const t of b.origem) {
    texto(ctx, t, textoX, ty + 18, 400, 18, COR.cinza);
    ty += 24;
  }
  for (const t of b.rateio) {
    icone(ctx, "recibo", textoX, ty + 3, 18, COR.marca, 1.8);
    texto(ctx, t, textoX + 26, ty + 18, 500, 17, COR.marca);
    ty += 24;
  }
  // coluna de valor
  const dir = M + CW;
  texto(ctx, brlCentavos(l.centavos), dir, y0 + 38, 700, 26, cor, "right");
  texto(ctx, l.tipoTexto, dir, y0 + 62, 600, 17, cor, "right");
  if (l.status === "PENDENTE") pilula(ctx, l.statusTexto, dir, y0 + 72, COR.ambarFundo, COR.ambar, "relogio");
  else pilula(ctx, l.statusTexto, dir, y0 + 72, COR.quitadoFundo, COR.quitado, "check");
  ctx.fillStyle = COR.divisor;
  ctx.fillRect(M, y0 + b.h - 2, CW, 2);
}

function desenharBloco(ctx: Ctx, b: Bloco, y: number) {
  switch (b.tipo) {
    case "titulo":
      ctx.fillStyle = COR.marca;
      ctx.fillRect(M, y + 34, 6, 24);
      texto(ctx, b.texto, M + 18, y + 54, 700, 26, COR.marcaEscura);
      break;
    case "pessoa":
      desenharPessoa(ctx, b.pessoa, y);
      break;
    case "linha":
      desenharLinha(ctx, b, y);
      break;
    case "rateio": {
      let ty = y + 20;
      for (const t of b.l1) {
        texto(ctx, t, M + 8, ty + 18, 600, 21, COR.tinta);
        ty += 28;
      }
      for (const t of b.l2) {
        texto(ctx, t, M + 8, ty + 16, 400, 19, COR.cinza);
        ty += 26;
      }
      ctx.fillStyle = COR.divisor;
      ctx.fillRect(M, y + b.h - 2, CW, 2);
      break;
    }
    case "rateioTotais": {
      ctx.fillStyle = "#e6f4f1";
      ctx.beginPath();
      ctx.roundRect(M, y + 10, CW, 92, 16);
      ctx.fill();
      const col = CW / 3;
      const cel: [string, number, string][] = [
        ["Total das despesas divididas", b.total, COR.tinta],
        ["A receber de terceiros", b.terceiros, COR.verde],
        ["Você realmente gastou", b.liquido, COR.marcaEscura],
      ];
      cel.forEach(([r, v, c], i) => {
        texto(ctx, r, M + 24 + i * col, y + 44, 400, 18, COR.cinza);
        texto(ctx, brlCentavos(v), M + 24 + i * col, y + 84, 700, 28, c);
      });
      break;
    }
  }
}

function desenharResumo(ctx: Ctx, rel: RelatorioPessoas, y0: number) {
  const r = rel.resumo;
  const w = (CW - 40) / 3;
  const sentido = r.saldoLiquido > 0 ? "a seu favor" : r.saldoLiquido < 0 ? "contra você" : "quites";
  const corSal = r.saldoLiquido > 0 ? COR.verde : r.saldoLiquido < 0 ? COR.laranja : COR.cinza;
  const caixas: { ic: NomeIcone; rot: string; val: string; sub: string; cor: string }[] = [
    { ic: "entrada", rot: "A receber (pendente)", val: brlCentavos(r.aReceberPendente), sub: "quem te deve", cor: COR.verde },
    { ic: "saida", rot: "A pagar (pendente)", val: brlCentavos(r.aPagarPendente), sub: "o que você deve", cor: COR.laranja },
    { ic: "balanca", rot: "Saldo líquido", val: brlCentavos(Math.abs(r.saldoLiquido)), sub: sentido, cor: corSal },
    { ic: "check", rot: "Quitado · a receber", val: brlCentavos(r.quitadoAReceber), sub: "já recebido", cor: COR.quitado },
    { ic: "check", rot: "Quitado · a pagar", val: brlCentavos(r.quitadoAPagar), sub: "já pago", cor: COR.quitado },
    { ic: "carteira", rot: "Acertos", val: `${r.nAcertos}`, sub: `${r.nPessoas} pessoa${r.nPessoas === 1 ? "" : "s"} · ${r.nPessoasComSaldo} com saldo`, cor: COR.marca },
  ];
  caixas.forEach((c, i) => {
    const x = M + (i % 3) * (w + 20);
    const y = y0 + Math.floor(i / 3) * 138;
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(x, y, w, 118, 16);
    ctx.clip();
    ctx.fillStyle = COR.cinzaClaro;
    ctx.fillRect(x, y, w, 118);
    ctx.fillStyle = c.cor;
    ctx.fillRect(x, y, 8, 118);
    ctx.restore();
    icone(ctx, c.ic, x + 24, y + 16, 24, c.cor, 2);
    texto(ctx, c.rot, x + 56, y + 34, 600, 17, COR.cinza);
    let t = 34;
    while (t > 20 && larg(ctx, c.val, 700, t) > w - 44) t -= 2;
    texto(ctx, c.val, x + 24, y + 80, 700, t, c.cor);
    texto(ctx, c.sub, x + 24, y + 104, 500, 16, COR.cinza);
  });
}

function desenharCabecalho1(ctx: Ctx, rel: RelatorioPessoas, plano: PlanoImagens) {
  ctx.fillStyle = COR.marca;
  ctx.fillRect(0, 0, LARGURA_IMG, 12);
  let y = 12 + 28;
  ctx.fillStyle = COR.marca;
  ctx.beginPath();
  ctx.roundRect(M, y, 56, 56, 14);
  ctx.fill();
  icone(ctx, "carteira", M + 12, y + 12, 32, "#ffffff", 2.4);
  texto(ctx, "MeuDinheiro", M + 72, y + 40, 800, 38, COR.marca);
  texto(ctx, `Emitido em ${rel.emitidoEm}`, M + CW, y + 38, 400, 18, COR.cinza, "right");
  y += 64 + 18 + 36;
  texto(ctx, rel.titulo, M, y, 700, 42, COR.tinta);
  y += 34;
  texto(ctx, `Período: ${rel.periodoTexto}`, M, y, 600, 22, COR.marcaEscura);
  y += 34;
  for (const t of plano.filtrosLinhas) {
    texto(ctx, t, M, y, 400, 18, COR.cinza);
    y += 26;
  }
  y += 8;
  desenharResumo(ctx, rel, y);
}

function desenharPagina(ctx: Ctx, rel: RelatorioPessoas, plano: PlanoImagens, p: PaginaImagem) {
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, LARGURA_IMG, p.altura);
  if (p.indice === 1) desenharCabecalho1(ctx, rel, plano);
  else {
    ctx.fillStyle = COR.marca;
    ctx.fillRect(0, 0, LARGURA_IMG, 12);
    texto(ctx, "MeuDinheiro", M, 58, 800, 24, COR.marca);
    texto(ctx, `${rel.titulo} · ${rel.periodoTexto} (continuação)`, M + 190, 58, 400, 20, COR.cinza);
  }
  let y = p.hCabecalho;
  for (const b of p.blocos) {
    desenharBloco(ctx, b, y);
    y += b.h;
  }
  const yr = p.altura - H_RODAPE;
  ctx.fillStyle = COR.divisor;
  ctx.fillRect(M, yr + 8, CW, 2);
  texto(ctx, `MeuDinheiro · emitido em ${rel.emitidoEm}`, M, yr + 42, 400, 17, COR.cinza);
  texto(ctx, `Parte ${p.indice} de ${p.total}`, M + CW, yr + 42, 400, 17, COR.cinza, "right");
}

export type FormatoImagem = "png" | "jpeg";

/** Uma imagem (Blob) por parte; JPEG com fundo branco opaco e qualidade 0,9. */
export async function gerarImagensPessoas(rel: RelatorioPessoas, formato: FormatoImagem): Promise<Blob[]> {
  const medidor = document.createElement("canvas").getContext("2d");
  if (!medidor) throw new Error("Canvas indisponível neste navegador.");
  const plano = planejarImagens(rel, medidor);
  const saida: Blob[] = [];
  for (const p of plano.paginas) {
    const canvas = document.createElement("canvas");
    canvas.width = LARGURA_IMG;
    canvas.height = p.altura;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Canvas indisponível neste navegador.");
    ctx.textBaseline = "alphabetic";
    desenharPagina(ctx, rel, plano, p);
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Falha ao gerar a imagem."))), formato === "png" ? "image/png" : "image/jpeg", 0.9);
    });
    saida.push(blob);
  }
  return saida;
}
