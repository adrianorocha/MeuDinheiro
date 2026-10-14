/**
 * R69 - paleta e ícones vetoriais do relatório de pessoas (sem dependências: formas simples numa grade 24x24,
 * desenhadas por um "alvo" mínimo; o mesmo desenho serve ao canvas (PNG/JPEG) e ao jsPDF).
 */
export const COR = {
  marca: "#0f766e",
  marcaEscura: "#115e59",
  tinta: "#0f172a",
  cinza: "#64748b",
  cinzaClaro: "#f1f5f9",
  divisor: "#e2e8f0",
  verde: "#15803d",
  verdeFundo: "#dcfce7",
  laranja: "#c2410c",
  laranjaFundo: "#ffedd5",
  ambar: "#b45309",
  ambarFundo: "#fef3c7",
  quitado: "#4b6b62",
  quitadoFundo: "#e3ece9",
  branco: "#ffffff",
} as const;

/** Cores dos avatares (índice vem do modelo: `corIndiceDaPessoa`). Todas com contraste >= 4,5:1 sobre branco. */
export const PALETA_AVATAR = ["#0f766e", "#0e7490", "#4f46e5", "#b45309", "#be185d", "#4d7c0f", "#7c3aed", "#475569"] as const;

export const corAvatar = (indice: number) => PALETA_AVATAR[((indice % PALETA_AVATAR.length) + PALETA_AVATAR.length) % PALETA_AVATAR.length];

export type NomeIcone = "entrada" | "saida" | "relogio" | "check" | "recibo" | "carteira" | "balanca";

type Forma = { k: "L"; p: readonly (readonly [number, number])[]; fechar?: boolean } | { k: "C"; cx: number; cy: number; r: number };

const ICONES: Record<NomeIcone, readonly Forma[]> = {
  // seta diagonal para dentro (recebe) / para fora (paga)
  entrada: [
    { k: "L", p: [[18, 6], [6, 18]] },
    { k: "L", p: [[6, 9], [6, 18], [15, 18]] },
  ],
  saida: [
    { k: "L", p: [[6, 18], [18, 6]] },
    { k: "L", p: [[9, 6], [18, 6], [18, 15]] },
  ],
  relogio: [
    { k: "C", cx: 12, cy: 12, r: 9 },
    { k: "L", p: [[12, 7], [12, 12], [16, 14]] },
  ],
  check: [
    { k: "C", cx: 12, cy: 12, r: 9 },
    { k: "L", p: [[8, 12.5], [11, 15.5], [16.5, 9]] },
  ],
  recibo: [
    { k: "L", p: [[6, 3], [18, 3], [18, 21], [15, 19], [12, 21], [9, 19], [6, 21]], fechar: true },
    { k: "L", p: [[9, 8], [15, 8]] },
    { k: "L", p: [[9, 12], [15, 12]] },
  ],
  carteira: [
    { k: "L", p: [[3, 8], [21, 8], [21, 20], [3, 20]], fechar: true },
    { k: "L", p: [[5, 8], [5, 5], [18, 5], [18, 8]] },
    { k: "C", cx: 16.5, cy: 14, r: 1.3 },
  ],
  balanca: [
    { k: "L", p: [[12, 4], [12, 20]] },
    { k: "L", p: [[7, 20], [17, 20]] },
    { k: "L", p: [[4, 8], [20, 8]] },
    { k: "L", p: [[4, 8], [2, 14], [6, 14]], fechar: true },
    { k: "L", p: [[20, 8], [18, 14], [22, 14]], fechar: true },
  ],
};

/** Superfície mínima: traço de segmentos e círculos (cor e espessura são definidas por quem chama). */
export interface AlvoIcone {
  segmento(x1: number, y1: number, x2: number, y2: number): void;
  circulo(cx: number, cy: number, r: number): void;
}

/** Desenha o ícone em (x, y) (canto superior esquerdo) com `tam` de lado. */
export function desenharIcone(alvo: AlvoIcone, nome: NomeIcone, x: number, y: number, tam: number): void {
  const e = tam / 24;
  for (const f of ICONES[nome]) {
    if (f.k === "C") {
      alvo.circulo(x + f.cx * e, y + f.cy * e, f.r * e);
      continue;
    }
    const n = f.fechar ? f.p.length : f.p.length - 1;
    for (let i = 0; i < n; i++) {
      const a = f.p[i];
      const b = f.p[(i + 1) % f.p.length];
      alvo.segmento(x + a[0] * e, y + a[1] * e, x + b[0] * e, y + b[1] * e);
    }
  }
}

/** Adaptador para canvas 2D (traço arredondado). */
export function alvoCanvas(ctx: CanvasRenderingContext2D, cor: string, espessura: number): AlvoIcone {
  ctx.strokeStyle = cor;
  ctx.lineWidth = espessura;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  return {
    segmento: (x1, y1, x2, y2) => {
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
    },
    circulo: (cx, cy, r) => {
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.stroke();
    },
  };
}

/** Adaptador para jsPDF (coordenadas em mm). */
export interface PdfTracado {
  setDrawColor(r: number, g: number, b: number): unknown;
  setLineWidth(w: number): unknown;
  setLineCap(c: "round" | "butt" | "square" | number): unknown;
  line(x1: number, y1: number, x2: number, y2: number): unknown;
  circle(x: number, y: number, r: number, style?: string): unknown;
}

export function hexParaRgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

export function alvoPdf(pdf: PdfTracado, cor: string, espessuraMm: number): AlvoIcone {
  const [r, g, b] = hexParaRgb(cor);
  pdf.setDrawColor(r, g, b);
  pdf.setLineWidth(espessuraMm);
  pdf.setLineCap("round");
  return {
    segmento: (x1, y1, x2, y2) => void pdf.line(x1, y1, x2, y2),
    circulo: (cx, cy, r2) => void pdf.circle(cx, cy, r2, "S"),
  };
}
