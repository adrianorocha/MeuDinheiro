import { fromCents } from "../finance/money";
import { COLUNAS_DETALHE, type Documento } from "../finance/relatorios";

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function formatarCelula(v: string | number, coluna: number, doc: Documento): string {
  if (typeof v === "number") return doc.colunasMoeda.includes(coluna) ? brl.format(v) : String(v).replace(".", ",");
  return v;
}

type CelulaPdf = string | { content: string; colSpan?: number; styles?: Record<string, unknown> };

/** PDF A4 multipágina: cabeçalho com filtros, totais, gráfico de barras e tabela. Roda 100% no navegador. */
export async function gerarPdf(doc: Documento, geradoEm: Date = new Date()): Promise<Blob> {
  const { jsPDF } = await import("jspdf");
  const autoTable = (await import("jspdf-autotable")).default;
  const pdf = new jsPDF({ unit: "mm", format: "a4", orientation: "portrait" });
  const largura = pdf.internal.pageSize.getWidth();
  const margem = 14;
  let y = 18;

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(18);
  pdf.setTextColor(15, 118, 110);
  pdf.text(doc.titulo, margem, y);
  y += 6;
  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(9);
  pdf.setTextColor(90, 100, 105);
  pdf.text(`Meu Dinheiro - gerado em ${geradoEm.toLocaleString("pt-BR")}`, margem, y);
  y += 7;

  pdf.setTextColor(30, 40, 40);
  pdf.setFontSize(10);
  pdf.setFont("helvetica", "bold");
  pdf.text("Filtros aplicados", margem, y);
  y += 5;
  pdf.setFont("helvetica", "normal");
  for (const f of doc.filtros) {
    for (const linha of pdf.splitTextToSize(f, largura - 2 * margem) as string[]) {
      pdf.text(linha, margem, y);
      y += 4.5;
    }
  }
  y += 3;

  // Totais em caixas
  if (doc.totais.length > 0) {
    const colunas = Math.min(doc.totais.length, 3);
    const w = (largura - 2 * margem - (colunas - 1) * 3) / colunas;
    doc.totais.forEach((t, i) => {
      const x = margem + (i % colunas) * (w + 3);
      const yy = y + Math.floor(i / colunas) * 15;
      pdf.setFillColor(238, 245, 245);
      pdf.roundedRect(x, yy, w, 12, 2, 2, "F");
      pdf.setFontSize(8);
      pdf.setTextColor(90, 100, 105);
      pdf.text(t.rotulo, x + 3, yy + 4.5);
      pdf.setFontSize(10.5);
      pdf.setFont("helvetica", "bold");
      pdf.setTextColor(15, 31, 31);
      pdf.text(t.valor, x + 3, yy + 9.5);
      pdf.setFont("helvetica", "normal");
    });
    y += Math.ceil(doc.totais.length / colunas) * 15 + 3;
  }

  // Gráfico de barras horizontais
  if (doc.grafico && doc.grafico.dados.length > 0) {
    const dados = doc.grafico.dados.slice(0, 10);
    const alturaGrafico = 8 + dados.length * 7;
    if (y + alturaGrafico > pdf.internal.pageSize.getHeight() - 20) {
      pdf.addPage();
      y = 18;
    }
    pdf.setFontSize(10);
    pdf.setFont("helvetica", "bold");
    pdf.setTextColor(30, 40, 40);
    pdf.text(doc.grafico.titulo, margem, y);
    y += 4;
    const max = Math.max(...dados.map((d) => Math.abs(d.valor)), 1);
    const rotuloW = 42;
    const barraMax = largura - 2 * margem - rotuloW - 32;
    pdf.setFont("helvetica", "normal");
    pdf.setFontSize(8.5);
    for (const d of dados) {
      pdf.setTextColor(40, 50, 50);
      const nome = pdf.splitTextToSize(d.rotulo, rotuloW - 2)[0] as string;
      pdf.text(nome, margem, y + 4);
      const w = Math.max(0.6, (Math.abs(d.valor) / max) * barraMax);
      pdf.setFillColor(15, 118, 110);
      pdf.rect(margem + rotuloW, y, w, 5, "F");
      pdf.text(brl.format(d.valor), margem + rotuloW + w + 2, y + 4);
      y += 7;
    }
    y += 4;
  }

  const rodape = () => {
    pdf.setFontSize(8);
    pdf.setTextColor(120, 130, 135);
    pdf.text(doc.titulo, margem, pdf.internal.pageSize.getHeight() - 8);
  };

  if (doc.detalhamento && doc.detalhamento.secoes.length > 0) {
    // R48: itens que compõem cada total, com subtotais em centavos (cabeçalho repetido a cada página).
    const det = doc.detalhamento;
    const fmt = (c: number) => brl.format(fromCents(c));
    if (y + 40 > pdf.internal.pageSize.getHeight() - 20) {
      pdf.addPage();
      y = 18;
    }
    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(11);
    pdf.setTextColor(30, 40, 40);
    pdf.text("Detalhamento dos lançamentos", margem, y);
    y += 3;
    for (const sec of det.secoes) {
      const corpo: CelulaPdf[][] = [];
      if (sec.titulo) {
        corpo.push([{ content: sec.titulo.toUpperCase(), colSpan: 6, styles: { fontStyle: "bold", fillColor: [220, 236, 234], textColor: [15, 80, 74] } }]);
      }
      for (const g of sec.grupos) {
        corpo.push([{ content: `${g.titulo} (${g.linhas.length})`, colSpan: 6, styles: { fontStyle: "bold", fillColor: [238, 245, 245] } }]);
        for (const l of g.linhas) {
          corpo.push([new Date(l.data).toLocaleDateString("pt-BR"), l.descricao, l.parcela, l.origem, l.situacao, fmt(l.centavos)]);
        }
        corpo.push([
          { content: `Subtotal · ${g.titulo}`, colSpan: 5, styles: { fontStyle: "bold", halign: "right" } },
          { content: fmt(g.subtotal), styles: { fontStyle: "bold", halign: "right" } },
        ]);
      }
      if (sec.titulo) {
        corpo.push([
          { content: `Total de ${sec.titulo.toLowerCase()}`, colSpan: 5, styles: { fontStyle: "bold", halign: "right", fillColor: [220, 236, 234] } },
          { content: fmt(sec.total), styles: { fontStyle: "bold", halign: "right", fillColor: [220, 236, 234] } },
        ]);
      }
      autoTable(pdf, {
        startY: y + 2,
        head: [[...COLUNAS_DETALHE]],
        body: corpo,
        showHead: "everyPage",
        rowPageBreak: "avoid",
        styles: { fontSize: 8, cellPadding: 1.6, overflow: "linebreak" },
        headStyles: { fillColor: [15, 118, 110], textColor: 255 },
        columnStyles: { 0: { cellWidth: 20 }, 2: { cellWidth: 14, halign: "center" }, 3: { cellWidth: 34 }, 4: { cellWidth: 18 }, 5: { halign: "right", cellWidth: 26 } },
        margin: { left: margem, right: margem, bottom: 16 },
        didDrawPage: rodape,
      });
      y = (pdf as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 2;
    }
    if (y + 12 > pdf.internal.pageSize.getHeight() - 20) {
      pdf.addPage();
      rodape();
      y = 18;
    }
    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(10);
    pdf.setTextColor(15, 31, 31);
    pdf.text(det.secoes.length > 1 ? "Resultado (receitas - despesas)" : "Total", margem, y + 5);
    pdf.text(fmt(det.total), largura - margem, y + 5, { align: "right" });
  } else {
    autoTable(pdf, {
      startY: y,
      head: [doc.colunas],
      body: doc.linhas.map((l) => l.map((v, i) => formatarCelula(v, i, doc))),
      styles: { fontSize: 8, cellPadding: 1.6 },
      headStyles: { fillColor: [15, 118, 110], textColor: 255 },
      alternateRowStyles: { fillColor: [244, 248, 248] },
      columnStyles: Object.fromEntries(doc.colunasMoeda.map((i) => [i, { halign: "right" as const }])),
      margin: { left: margem, right: margem, bottom: 16 },
      didDrawPage: rodape,
    });
  }

  const total = pdf.getNumberOfPages();
  for (let i = 1; i <= total; i++) {
    pdf.setPage(i);
    pdf.setFontSize(8);
    pdf.setTextColor(120, 130, 135);
    pdf.text(`Emitido em ${geradoEm.toLocaleString("pt-BR")}`, largura / 2, pdf.internal.pageSize.getHeight() - 8, { align: "center" });
    pdf.text(`Página ${i} de ${total}`, largura - margem, pdf.internal.pageSize.getHeight() - 8, { align: "right" });
  }
  return pdf.output("blob");
}
