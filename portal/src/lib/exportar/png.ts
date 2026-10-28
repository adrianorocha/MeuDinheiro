import { fromCents } from "../finance/money";
import { resumirParaImagem, type Documento } from "../finance/relatorios";

const PALETA = ["#0f766e", "#0e7490", "#6366f1", "#d97706", "#be185d", "#65a30d", "#7c3aed", "#64748b"];

/** Imagem-resumo (título, filtros, totais e gráfico de categorias) desenhada em canvas, sem dependências. */
export async function gerarPng(doc: Documento, geradoEm: Date = new Date(), maxPorGrupo = 5): Promise<Blob> {
  const largura = 1200;
  const dados = doc.grafico?.dados.slice(0, 8) ?? [];
  const linhasFiltro = doc.filtros.length;
  const linhasTotais = Math.ceil(doc.totais.length / 3);
  // R48: detalhamento resumido (maiores itens por grupo); PDF e CSV levam tudo.
  const secoes = (doc.detalhamento?.secoes ?? []).map((sec) => ({ sec, res: resumirParaImagem(sec, maxPorGrupo) }));
  let alturaDet = 0;
  if (secoes.length > 0) {
    alturaDet += 80;
    for (const { sec, res } of secoes) {
      if (sec.titulo) alturaDet += 44;
      for (const g of res.grupos) alturaDet += 50 + g.linhas.length * 38 + (g.restantes > 0 ? 34 : 0) + 12;
      if (res.gruposOmitidos > 0) alturaDet += 40;
    }
  }
  const altura = 150 + linhasFiltro * 26 + linhasTotais * 96 + (dados.length > 0 ? 70 + dados.length * 44 : 0) + alturaDet + 60;

  const escala = 2;
  const canvas = document.createElement("canvas");
  canvas.width = largura * escala;
  canvas.height = altura * escala;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas indisponível neste navegador.");
  ctx.scale(escala, escala);

  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, largura, altura);
  ctx.fillStyle = "#0f766e";
  ctx.fillRect(0, 0, largura, 8);

  const fonte = (peso: string, tam: number) => `${peso} ${tam}px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`;
  let y = 64;
  ctx.fillStyle = "#0f1f1f";
  ctx.font = fonte("700", 36);
  ctx.fillText(doc.titulo, 48, y);
  y += 30;
  ctx.fillStyle = "#4d6265";
  ctx.font = fonte("400", 16);
  ctx.fillText(`Meu Dinheiro · gerado em ${geradoEm.toLocaleString("pt-BR")}`, 48, y);
  y += 38;

  ctx.font = fonte("400", 18);
  for (const f of doc.filtros) {
    ctx.fillStyle = "#33474a";
    ctx.fillText(f, 48, y);
    y += 26;
  }
  y += 12;

  const w = (largura - 96 - 2 * 16) / 3;
  doc.totais.forEach((t, i) => {
    const x = 48 + (i % 3) * (w + 16);
    const yy = y + Math.floor(i / 3) * 96;
    ctx.fillStyle = "#eef5f5";
    ctx.beginPath();
    ctx.roundRect(x, yy, w, 80, 12);
    ctx.fill();
    ctx.fillStyle = "#4d6265";
    ctx.font = fonte("500", 15);
    ctx.fillText(t.rotulo, x + 18, yy + 28);
    ctx.fillStyle = "#0f1f1f";
    ctx.font = fonte("700", 24);
    ctx.fillText(t.valor, x + 18, yy + 62, w - 36);
  });
  y += linhasTotais * 96 + 20;

  if (dados.length > 0 && doc.grafico) {
    ctx.fillStyle = "#0f1f1f";
    ctx.font = fonte("600", 20);
    ctx.fillText(doc.grafico.titulo, 48, y);
    y += 24;
    const max = Math.max(...dados.map((d) => Math.abs(d.valor)), 1);
    const rotuloW = 240;
    const barraMax = largura - 96 - rotuloW - 190;
    const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
    dados.forEach((d, i) => {
      ctx.fillStyle = "#33474a";
      ctx.font = fonte("400", 17);
      ctx.fillText(d.rotulo, 48, y + 24, rotuloW - 12);
      const bw = Math.max(3, (Math.abs(d.valor) / max) * barraMax);
      ctx.fillStyle = PALETA[i % PALETA.length];
      ctx.beginPath();
      ctx.roundRect(48 + rotuloW, y + 6, bw, 26, 5);
      ctx.fill();
      ctx.fillStyle = "#0f1f1f";
      ctx.fillText(brl.format(d.valor), 48 + rotuloW + bw + 10, y + 25);
      y += 44;
    });
  }

  if (secoes.length > 0) {
    const brl2 = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
    const fmt = (c: number) => brl2.format(fromCents(c));
    y += 6;
    ctx.fillStyle = "#0f1f1f";
    ctx.font = fonte("700", 24);
    ctx.fillText("Detalhamento", 48, y);
    y += 40;
    for (const { sec, res } of secoes) {
      if (sec.titulo) {
        ctx.fillStyle = "#0f766e";
        ctx.font = fonte("700", 17);
        ctx.fillText(sec.titulo.toUpperCase(), 48, y);
        y += 44;
      }
      for (const g of res.grupos) {
        ctx.fillStyle = "#0f1f1f";
        ctx.font = fonte("600", 19);
        ctx.fillText(g.titulo, 48, y, 640);
        ctx.textAlign = "right";
        ctx.fillText(fmt(g.subtotal), largura - 48, y);
        ctx.textAlign = "left";
        y += 10;
        ctx.fillStyle = "#d5e0e0";
        ctx.fillRect(48, y, largura - 96, 2);
        y += 30;
        for (const l of g.linhas) {
          ctx.font = fonte("400", 16);
          ctx.fillStyle = "#4d6265";
          ctx.fillText(new Date(l.data).toLocaleDateString("pt-BR"), 48, y);
          ctx.fillStyle = "#33474a";
          ctx.fillText(l.parcela ? `${l.descricao} (${l.parcela})` : l.descricao, 170, y, 680);
          ctx.textAlign = "right";
          ctx.fillText(fmt(l.centavos), largura - 48, y);
          ctx.textAlign = "left";
          y += 38;
        }
        if (g.restantes > 0) {
          ctx.font = fonte("400", 15);
          ctx.fillStyle = "#4d6265";
          ctx.fillText(`… e mais ${g.restantes} lançamento${g.restantes > 1 ? "s" : ""}`, 170, y);
          y += 34;
        }
        y += 12;
      }
      if (res.gruposOmitidos > 0) {
        ctx.font = fonte("400", 15);
        ctx.fillStyle = "#4d6265";
        ctx.fillText(`… e mais ${res.gruposOmitidos} grupo${res.gruposOmitidos > 1 ? "s" : ""} (veja o PDF ou o CSV)`, 48, y);
        y += 40;
      }
    }
  }

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Falha ao gerar a imagem."))), "image/png");
  });
}
