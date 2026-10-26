/**
 * R69 - PDF A4 multipágina do relatório de pessoas (jsPDF + autotable, 100% no navegador).
 * Faixa de marca no topo, caixas de resumo, um cartão por pessoa (avatar com iniciais, saldo com sentido, subtotais) e tabela com
 * cabeçalho repetido; rodapé "Página X de Y" + emissão em todas as páginas. Cor nunca é a única informação: sempre há texto e ícone.
 */
import type { CellHookData } from "jspdf-autotable";
import { brlCentavos } from "../finance/centavos";
import type { LinhaRelatorioPessoas, PessoaRelatorio, RelatorioPessoas } from "../finance/relatorioPessoas";
import { dataBR } from "../finance/relatorioPessoas";
import { alvoPdf, COR, corAvatar, desenharIcone, hexParaRgb, type NomeIcone } from "./pessoasIcones";

/** Helvetica padrão do PDF não tem "—", "−", "→" nem "…" garantidos: troca por equivalentes seguros. */
export function textoPdf(s: string): string {
  return s.replace(/[—–]/g, "-").replace(/−/g, "-").replace(/→/g, "->").replace(/…/g, "...");
}

const MARGEM = 14;
const TOPO_CONT = 22;
const RODAPE = 16;

type Rgb = [number, number, number];
const rgb = (hex: string): Rgb => hexParaRgb(hex);

export async function gerarPdfPessoas(rel: RelatorioPessoas): Promise<Blob> {
  const { jsPDF } = await import("jspdf");
  const autoTable = (await import("jspdf-autotable")).default;
  const pdf = new jsPDF({ unit: "mm", format: "a4", orientation: "portrait" });
  const L = pdf.internal.pageSize.getWidth();
  const A = pdf.internal.pageSize.getHeight();
  const CW = L - 2 * MARGEM;
  let y = 0;

  const cor = (hex: string) => pdf.setTextColor(...rgb(hex));
  const fundo = (hex: string) => pdf.setFillColor(...rgb(hex));
  const fonte = (peso: "normal" | "bold", tam: number) => {
    pdf.setFont("helvetica", peso);
    pdf.setFontSize(tam);
  };
  const t = (s: string, x: number, yy: number, opt?: { align?: "left" | "right" | "center"; maxWidth?: number }) => pdf.text(textoPdf(s), x, yy, opt);
  const icone = (nome: NomeIcone, x: number, yy: number, tam: number, hex: string, esp = 0.25) => desenharIcone(alvoPdf(pdf, hex, esp), nome, x, yy, tam);
  const avatar = (cx: number, cy: number, r: number, iniciais: string, hex: string) => {
    fundo(hex);
    pdf.circle(cx, cy, r, "F");
    fonte("bold", r * 1.7);
    cor("#ffffff");
    t(iniciais, cx, cy + r * 0.33, { align: "center" });
  };
  const garantir = (h: number) => {
    if (y + h > A - RODAPE) {
      pdf.addPage();
      y = TOPO_CONT;
    }
  };

  // ---- cabeçalho (faixa de marca)
  fundo(COR.marca);
  pdf.rect(0, 0, L, 36, "F");
  fundo("#ffffff");
  pdf.roundedRect(MARGEM, 7, 10, 10, 2, 2, "F");
  icone("carteira", MARGEM + 1.5, 8.5, 7, COR.marca, 0.35);
  fonte("bold", 17);
  cor("#ffffff");
  t("MeuDinheiro", MARGEM + 13, 14.5);
  fonte("normal", 8);
  t(`Emitido em ${rel.emitidoEm}`, L - MARGEM, 14, { align: "right" });
  fonte("bold", 14);
  t(rel.titulo, MARGEM, 26);
  fonte("normal", 9.5);
  t(`Período: ${rel.periodoTexto}`, MARGEM, 32);
  y = 42;
  fonte("normal", 8);
  cor(COR.cinza);
  for (const l of pdf.splitTextToSize(textoPdf(rel.filtros.join("   ·   ")), CW) as string[]) {
    pdf.text(l, MARGEM, y);
    y += 4;
  }
  y += 3;

  // ---- cartão da pessoa
  const cartaoPessoa = (p: PessoaRelatorio) => {
    garantir(p.proximoVencimento ? 30 : 18);
    const hex = corAvatar(p.corIndice);
    fundo(COR.cinzaClaro);
    pdf.roundedRect(MARGEM, y, CW, 14, 2, 2, "F");
    fundo(hex);
    pdf.roundedRect(MARGEM, y, 1.8, 14, 0.9, 0.9, "F");
    avatar(MARGEM + 9, y + 7, 4.6, p.iniciais, hex);
    fonte("bold", 11.5);
    cor(COR.tinta);
    t(p.nome, MARGEM + 16, y + 7, { maxWidth: CW - 90 });
    if (p.contato) {
      fonte("normal", 7.5);
      cor(COR.cinza);
      t(p.contato, MARGEM + 16, y + 11.2, { maxWidth: CW - 90 });
    }
    const cs = p.sentido === "TE_DEVE" ? COR.verde : p.sentido === "VOCE_DEVE" ? COR.laranja : COR.cinza;
    fonte("bold", 11);
    cor(cs);
    const saldo = textoPdf(p.saldoTexto);
    const wSaldo = pdf.getTextWidth(saldo);
    t(p.saldoTexto, L - MARGEM - 3, y + 7, { align: "right" });
    icone(p.sentido === "QUITES" ? "check" : "balanca", L - MARGEM - 3 - wSaldo - 7, y + 2.6, 5, cs, 0.3);
    fonte("normal", 6.5);
    cor(COR.cinza);
    t("saldo pendente (a receber - a pagar)", L - MARGEM - 3, y + 11, { align: "right" });
    y += 17;
    if (p.proximoVencimento) {
      const v = p.proximoVencimento;
      const c = v.vencido ? COR.laranja : COR.ambar;
      fundo(v.vencido ? COR.laranjaFundo : COR.ambarFundo);
      pdf.roundedRect(MARGEM, y - 1, CW, 10, 2, 2, "F");
      fundo(c);
      pdf.roundedRect(MARGEM, y - 1, 1.8, 10, 0.9, 0.9, "F");
      icone("calendario", MARGEM + 4.5, y + 0.9, 5.2, c, 0.3);
      fonte("bold", 10);
      cor(c);
      t(`VENCIMENTO DA FATURA DO CARTÃO: ${dataBR(v.ms)}${v.vencido ? " (vencida)" : ""}`, MARGEM + 12, y + 5.4);
      fonte("normal", 8);
      t(`valor ligado à fatura: ${brlCentavos(v.centavos)}`, L - MARGEM - 3, y + 5.4, { align: "right" });
      y += 12;
    }
  };

  const titulo = (s: string) => {
    garantir(16);
    fundo(COR.marca);
    pdf.rect(MARGEM, y - 0.5, 1.4, 6, "F");
    fonte("bold", 11);
    cor(COR.marcaEscura);
    t(s, MARGEM + 4, y + 4);
    y += 8;
  };

  // ---- tabela de acertos
  type Linha = LinhaRelatorioPessoas;
  const tabela = (linhas: Linha[], opc: { mostrarPessoa: boolean; nomeContinuacao?: string; corPorPessoa?: Map<number, string>; iniciais?: Map<number, string> }) => {
    const colStatus = opc.mostrarPessoa ? 3 : 2;
    const colPessoa = 1;
    const cab = opc.mostrarPessoa ? ["Data", "Pessoa", "Descrição / origem", "Status", "Valor"] : ["Data", "Descrição / origem", "Status", "Valor"];
    const corpo: (string | { content: string; colSpan: number; styles: Record<string, unknown> })[][] = linhas.map((l) => {
      const desc = [l.descricao, l.origem, ...(l.rateio ? [l.rateio.texto] : [])].map(textoPdf).join("\n");
      const status = textoPdf(l.statusTexto) + (l.vencimento ? `
${l.vencimento.vencido ? "VENCEU" : "VENCE"} ${dataBR(l.vencimento.ms)}` : "");
      const base = [l.dataTexto, desc, status, brlCentavos(l.centavos)];
      return opc.mostrarPessoa ? [base[0], textoPdf(l.pessoaNome), ...base.slice(1)] : base;
    });
    const larguraFixa = opc.mostrarPessoa ? { 0: 19, 1: 28, 3: 32, 4: 26 } : { 0: 19, 2: 32, 3: 26 };
    const estilos: Record<number, Record<string, unknown>> = {};
    for (const [k, w] of Object.entries(larguraFixa)) estilos[Number(k)] = { cellWidth: w };
    const pad = { top: 1.8, right: 1.6, bottom: 1.8, left: 7 };
    estilos[colStatus] = { ...estilos[colStatus], cellPadding: pad };
    estilos[cab.length - 1] = { ...estilos[cab.length - 1], halign: "right", fontStyle: "bold" };
    if (opc.mostrarPessoa) estilos[colPessoa] = { ...estilos[colPessoa], cellPadding: { ...pad, left: 8 } };
    let primeira = -1;

    autoTable(pdf, {
      startY: y,
      head: [cab],
      body: corpo,
      showHead: "everyPage",
      rowPageBreak: "avoid",
      styles: { fontSize: 8, cellPadding: 1.8, overflow: "linebreak", textColor: rgb(COR.tinta), lineColor: rgb(COR.divisor), lineWidth: 0.1 },
      headStyles: { fillColor: rgb(COR.marca), textColor: 255, fontStyle: "bold" },
      alternateRowStyles: { fillColor: [248, 250, 252] },
      columnStyles: estilos,
      margin: { top: TOPO_CONT, left: MARGEM, right: MARGEM, bottom: RODAPE },
      didParseCell: (d: CellHookData) => {
        if (d.section !== "body" || d.row.index >= linhas.length) return;
        const l = linhas[d.row.index];
        const verde = l.tipo === "A_RECEBER";
        if (d.column.index === cab.length - 1) d.cell.styles.textColor = rgb(verde ? COR.verde : COR.laranja);
        if (d.column.index === colStatus) {
          d.cell.styles.textColor = rgb(l.vencimento?.vencido ? COR.laranja : l.status === "PENDENTE" ? COR.ambar : COR.quitado);
          if (l.vencimento) d.cell.styles.fontStyle = "bold";
        }
        if (opc.mostrarPessoa && d.column.index === colPessoa) d.cell.styles.textColor = rgb(opc.corPorPessoa?.get(l.pessoaId) ?? COR.tinta);
      },
      didDrawCell: (d: CellHookData) => {
        if (d.section !== "body" || d.row.index >= linhas.length) return;
        const l = linhas[d.row.index];
        const x = d.cell.x + 1.6;
        const yy = d.cell.y + 1.7;
        if (d.column.index === colStatus) icone(l.status === "PENDENTE" ? "relogio" : "check", x, yy, 4, l.status === "PENDENTE" ? COR.ambar : COR.quitado, 0.28);
        else if (opc.mostrarPessoa && d.column.index === colPessoa) avatar(d.cell.x + 4.2, yy + 2, 2.4, (opc.iniciais?.get(l.pessoaId) ?? "?").slice(0, 1), opc.corPorPessoa?.get(l.pessoaId) ?? COR.cinza);
        else if (d.column.index === (opc.mostrarPessoa ? 2 : 1) && l.rateio) {
          // ícone de recibo no canto inferior direito da célula (rateio/vínculo)
          icone("recibo", d.cell.x + d.cell.width - 5, d.cell.y + d.cell.height - 5.4, 3.6, COR.marca, 0.25);
        }
      },
      didDrawPage: (d: { pageNumber: number }) => {
        if (primeira < 0) primeira = d.pageNumber;
        else if (opc.nomeContinuacao && d.pageNumber > primeira) {
          fonte("bold", 8);
          cor(COR.marcaEscura);
          t(`${opc.nomeContinuacao} (continuação)`, MARGEM, 19);
        }
      },
    });
    y = (pdf as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 5;
  };

  if (rel.agrupar === "CRONOLOGICO" && rel.incluirDetalhes) {
    titulo("Saldo por pessoa");
    for (const p of rel.pessoas) cartaoPessoa(p);
    y += 2;
    titulo("Acertos em ordem cronológica");
    tabela(rel.cronologico, {
      mostrarPessoa: true,
      nomeContinuacao: "Acertos em ordem cronológica",
      corPorPessoa: new Map(rel.pessoas.map((p) => [p.pessoaId, corAvatar(p.corIndice)])),
      iniciais: new Map(rel.pessoas.map((p) => [p.pessoaId, p.iniciais])),
    });
  } else {
    for (const p of rel.pessoas) {
      cartaoPessoa(p);
      if (p.linhas.length > 0) tabela(p.linhas, { mostrarPessoa: false, nomeContinuacao: p.nome });
      else y += 1;
    }
  }

  // ---- bloco opcional: rateios
  if (rel.rateios) {
    const rt = rel.rateios;
    garantir(40);
    titulo("Rateios · quanto você realmente gastou (informativo)");
    autoTable(pdf, {
      startY: y,
      head: [["Data", "Lançamento", "Total", "A receber de terceiros", "Você gastou"]],
      body: [
        ...rt.itens.map((i) => [dataBR(i.data), textoPdf(i.descricao + (i.orfao ? " (lançamento removido)" : "")), i.orfao ? "-" : brlCentavos(i.total), brlCentavos(i.aReceber), i.orfao ? "-" : brlCentavos(i.liquido)]),
        [
          { content: "Total (lançamentos existentes)", colSpan: 2, styles: { fontStyle: "bold", halign: "right", fillColor: rgb("#e6f4f1") } },
          { content: brlCentavos(rt.totalRateado), styles: { fontStyle: "bold", fillColor: rgb("#e6f4f1") } },
          { content: brlCentavos(rt.aReceberTerceiros), styles: { fontStyle: "bold", fillColor: rgb("#e6f4f1"), textColor: rgb(COR.verde) } },
          { content: brlCentavos(rt.liquido), styles: { fontStyle: "bold", fillColor: rgb("#e6f4f1"), textColor: rgb(COR.marcaEscura) } },
        ],
      ],
      showHead: "everyPage",
      rowPageBreak: "avoid",
      styles: { fontSize: 8, cellPadding: 1.8, textColor: rgb(COR.tinta), lineColor: rgb(COR.divisor), lineWidth: 0.1 },
      headStyles: { fillColor: rgb(COR.marca), textColor: 255 },
      columnStyles: { 0: { cellWidth: 19 }, 2: { halign: "right", cellWidth: 26 }, 3: { halign: "right", cellWidth: 38 }, 4: { halign: "right", cellWidth: 28 } },
      margin: { top: TOPO_CONT, left: MARGEM, right: MARGEM, bottom: RODAPE },
    });
    y = (pdf as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 4;
    fonte("normal", 7.5);
    cor(COR.cinza);
    t("Informativo: o lançamento e os relatórios continuam com o valor total que saiu da conta.", MARGEM, y);
  }

  // ---- cabeçalho de continuação e rodapé (agora que o total de páginas é conhecido)
  const total = pdf.getNumberOfPages();
  for (let i = 1; i <= total; i++) {
    pdf.setPage(i);
    if (i > 1) {
      fundo(COR.marca);
      pdf.rect(0, 0, L, 3, "F");
      fonte("bold", 10);
      cor(COR.marca);
      t("MeuDinheiro", MARGEM, 10);
      fonte("normal", 8);
      cor(COR.cinza);
      t(`${rel.titulo} · ${rel.periodoTexto}`, MARGEM + 30, 10);
    }
    pdf.setDrawColor(...rgb(COR.divisor));
    pdf.setLineWidth(0.2);
    pdf.line(MARGEM, A - 12, L - MARGEM, A - 12);
    fonte("normal", 8);
    cor(COR.cinza);
    t(`MeuDinheiro · emitido em ${rel.emitidoEm}`, MARGEM, A - 8);
    t(`Página ${i} de ${total}`, L - MARGEM, A - 8, { align: "right" });
  }
  return pdf.output("blob");
}
