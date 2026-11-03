import { cartaoIdDe } from "../finance/calc";
import { toCents } from "../finance/money";
import type { Dataset } from "../finance/types";

const DIA = 86_400_000;

function ofxData(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}120000[-3:BRT]`;
}

/**
 * Gera um OFX sintético a partir dos lançamentos da conta (para experimentar a conciliação):
 * a maioria bate exatamente; uma linha vem com 1 dia de diferença, uma com IOF a mais (DIFERENCA),
 * uma só existe no extrato (tarifa) e uma é repetida (para ver o DUPLICADO ao reimportar).
 */
export function gerarOfxExemplo(ds: Dataset, numeroConta: string, agora: number): string | null {
  const base = ds.despesas
    .filter((d) => d.conta === numeroConta && cartaoIdDe(d) === null && d.pago && d.natureza === "NORMAL" && d.data <= agora && d.data >= agora - 75 * DIA)
    .sort((a, b) => a.data - b.data)
    .slice(-8);
  if (base.length === 0) return null;
  const linhas: string[] = [];
  let saldo = 0;
  let n = 0;
  const add = (data: number, valor: number, memo: string) => {
    n++;
    saldo += toCents(valor);
    linhas.push(
      `<STMTTRN><TRNTYPE>${valor < 0 ? "DEBIT" : "CREDIT"}<DTPOSTED>${ofxData(data)}<TRNAMT>${valor.toFixed(2)}<FITID>EXEMPLO${String(n).padStart(4, "0")}<MEMO>${memo.replace(/[<>&]/g, " ")}\n</STMTTRN>`,
    );
  };
  base.forEach((d, i) => {
    const sinal = d.tipo === "CREDITO" ? 1 : -1;
    const memo = `${sinal < 0 ? "COMPRA NO DEBITO " : ""}${d.descricao.toUpperCase()}`;
    if (i === 1) add(d.data + DIA, sinal * d.valor, memo); // 1 dia depois
    else if (i === 2 && sinal < 0) add(d.data, sinal * (d.valor + 1.5), memo); // IOF/tarifa
    else add(d.data, sinal * d.valor, memo);
    if (i === 3) add(d.data, -12.5, "CAFE DA ESQUINA"); // só no extrato
  });
  add(agora - 2 * DIA, -29.9, "TARIFA PACOTE DE SERVICOS");
  const saldoFinal = (saldo / 100).toFixed(2);
  return `OFXHEADER:100
DATA:OFXSGML
VERSION:102
CHARSET:1252

<OFX>
<BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL
<BANKACCTFROM><BANKID>0000<ACCTID>${numeroConta}<ACCTTYPE>CHECKING</BANKACCTFROM>
<BANKTRANLIST><DTSTART>${ofxData(base[0].data).slice(0, 8)}<DTEND>${ofxData(agora).slice(0, 8)}
${linhas.join("\n")}
</BANKTRANLIST>
<LEDGERBAL><BALAMT>${saldoFinal}<DTASOF>${ofxData(agora)}</LEDGERBAL>
</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
`;
}
