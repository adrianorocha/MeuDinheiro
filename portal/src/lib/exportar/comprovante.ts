import { AVISO_COMPROVANTE, type ComprovanteModelo } from "../finance/comprovante";

const LARGURA = 900;
const MARGEM = 72;
const COL_ROTULO = 300;
const ALTURA_LINHA = 38;
const ESCALA = 2;
const MARCA = "#0f766e";
const TINTA = "#0f172a";
const CINZA = "#64748b";
const DIVISOR = "#e2e8f0";

const fonte = (peso: string, tam: number) => `${peso} ${tam}px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`;

/** Quebra o texto em linhas que cabem em `max` px (palavras maiores que a linha são cortadas por caractere, nunca descartadas). */
export function quebrarTexto(ctx: Pick<CanvasRenderingContext2D, "measureText">, texto: string, max: number): string[] {
  const linhas: string[] = [];
  let atual = "";
  const cabe = (t: string) => ctx.measureText(t).width <= max;
  for (const palavra of texto.split(/\s+/).filter(Boolean)) {
    let p = palavra;
    while (!cabe(p) && p.length > 1) {
      let n = p.length - 1;
      while (n > 1 && !cabe(p.slice(0, n))) n--;
      if (atual) {
        linhas.push(atual);
        atual = "";
      }
      linhas.push(p.slice(0, n));
      p = p.slice(n);
    }
    const tentativa = atual ? `${atual} ${p}` : p;
    if (cabe(tentativa)) atual = tentativa;
    else {
      if (atual) linhas.push(atual);
      atual = p;
    }
  }
  if (atual) linhas.push(atual);
  return linhas.length ? linhas : [""];
}

function pilula(ctx: CanvasRenderingContext2D, texto: string, cor: string, centroX: number, y: number, tam: number, alturaPilula: number): void {
  ctx.font = fonte("700", tam);
  const w = ctx.measureText(texto).width + 56;
  ctx.fillStyle = cor;
  ctx.beginPath();
  ctx.roundRect(centroX - w / 2, y, w, alturaPilula, alturaPilula / 2);
  ctx.fill();
  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "center";
  ctx.fillText(texto, centroX, y + alturaPilula / 2 + tam * 0.35);
  ctx.textAlign = "left";
}

/** Desenha o comprovante num canvas de altura dinâmica (uma linha por campo presente). */
export function desenharComprovante(m: ComprovanteModelo): HTMLCanvasElement {
  const medidor = document.createElement("canvas").getContext("2d");
  if (!medidor) throw new Error("Canvas indisponível neste navegador.");
  const larguraValor = LARGURA - 2 * MARGEM - COL_ROTULO - 24;
  medidor.font = fonte("700", 28);
  const valores = m.linhas.map((l) => quebrarTexto(medidor, l.valor, larguraValor));
  medidor.font = fonte("400", 26);
  const rotulos = m.linhas.map((l) => quebrarTexto(medidor, l.rotulo, COL_ROTULO));
  medidor.font = fonte("400", 22);
  const aviso = quebrarTexto(medidor, AVISO_COMPROVANTE, LARGURA - 2 * MARGEM);
  let corpo = 0;
  m.linhas.forEach((_, i) => {
    corpo += Math.max(valores[i].length, rotulos[i].length) * ALTURA_LINHA + 26;
  });
  const altura = 626 + corpo + aviso.length * 30;

  const canvas = document.createElement("canvas");
  canvas.width = LARGURA * ESCALA;
  canvas.height = altura * ESCALA;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas indisponível neste navegador.");
  ctx.scale(ESCALA, ESCALA);
  ctx.textBaseline = "alphabetic";

  ctx.fillStyle = "#f1f5f9";
  ctx.fillRect(0, 0, LARGURA, altura);
  ctx.save();
  ctx.shadowColor = "rgba(15,23,42,0.14)";
  ctx.shadowBlur = 14;
  ctx.shadowOffsetY = 4;
  ctx.fillStyle = "#ffffff";
  ctx.beginPath();
  ctx.roundRect(32, 32, LARGURA - 64, altura - 64, 28);
  ctx.fill();
  ctx.restore();
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(32, 32, LARGURA - 64, altura - 64, 28);
  ctx.clip();
  ctx.fillStyle = MARCA;
  ctx.fillRect(32, 32, LARGURA - 64, 20);
  ctx.restore();

  let y = 150;
  ctx.fillStyle = MARCA;
  ctx.font = fonte("800", 40);
  ctx.fillText("MeuDinheiro", MARGEM, y);
  ctx.fillStyle = CINZA;
  ctx.font = fonte("400", 24);
  ctx.fillText("Comprovante de lançamento", MARGEM, y + 34);
  ctx.textAlign = "right";
  ctx.font = fonte("700", 24);
  ctx.fillText(m.id, LARGURA - MARGEM, y - 6);
  ctx.textAlign = "left";
  y += 80;

  pilula(ctx, m.selo.rotulo, m.selo.cor, LARGURA / 2, y, 24, 48);
  y += 48 + 78;

  let tam = 84;
  ctx.font = fonte("800", tam);
  while (ctx.measureText(m.valor).width > LARGURA - 2 * MARGEM && tam > 36) {
    tam -= 4;
    ctx.font = fonte("800", tam);
  }
  ctx.fillStyle = m.entrada ? "#15803d" : TINTA;
  ctx.textAlign = "center";
  ctx.fillText(m.valor, LARGURA / 2, y);
  ctx.textAlign = "left";
  y += 52;

  pilula(ctx, m.status.rotulo, m.status.cor, LARGURA / 2, y - 26, 24, 44);
  y += 58;

  const divisor = () => {
    ctx.fillStyle = DIVISOR;
    ctx.fillRect(MARGEM, y, LARGURA - 2 * MARGEM, 2);
  };
  divisor();
  y += 46;

  m.linhas.forEach((_, i) => {
    const n = Math.max(valores[i].length, rotulos[i].length);
    ctx.fillStyle = CINZA;
    ctx.font = fonte("400", 26);
    rotulos[i].forEach((t, k) => ctx.fillText(t, MARGEM, y + k * ALTURA_LINHA));
    ctx.fillStyle = TINTA;
    ctx.font = fonte("700", 28);
    ctx.textAlign = "right";
    valores[i].forEach((t, k) => ctx.fillText(t, LARGURA - MARGEM, y + k * ALTURA_LINHA));
    ctx.textAlign = "left";
    y += (n - 1) * ALTURA_LINHA + 26;
    divisor();
    y += 38;
  });

  y += 12;
  ctx.fillStyle = TINTA;
  ctx.font = fonte("700", 24);
  ctx.textAlign = "center";
  ctx.fillText(`Emitido em ${m.emitidoEm}`, LARGURA / 2, y);
  y += 40;
  ctx.fillStyle = CINZA;
  ctx.font = fonte("400", 22);
  for (const l of aviso) {
    ctx.fillText(l, LARGURA / 2, y);
    y += 30;
  }
  ctx.textAlign = "left";
  return canvas;
}

export async function gerarComprovantePng(m: ComprovanteModelo): Promise<Blob> {
  const canvas = desenharComprovante(m);
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Falha ao gerar a imagem."))), "image/png");
  });
}

/** PDF de uma página (largura A5, altura proporcional ao conteúdo) com o mesmo desenho do PNG. */
export async function gerarComprovantePdf(m: ComprovanteModelo): Promise<Blob> {
  const { jsPDF } = await import("jspdf");
  const canvas = desenharComprovante(m);
  const larguraMm = 148;
  const alturaMm = (canvas.height / canvas.width) * larguraMm;
  const pdf = new jsPDF({ unit: "mm", format: [larguraMm, alturaMm], orientation: alturaMm > larguraMm ? "portrait" : "landscape" });
  pdf.addImage(canvas.toDataURL("image/png"), "PNG", 0, 0, larguraMm, alturaMm);
  return pdf.output("blob");
}
