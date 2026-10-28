/**
 * R66 - escritor de ZIP simples, sem compressão (método STORE) e sem dependências.
 * Escolhido no lugar de gerar .xlsx: não há biblioteca xlsx no projeto e `fflate` só existe como dependência transitiva do jspdf.
 */

export interface ArquivoZip {
  nome: string;
  dados: Uint8Array;
}

let tabelaCrc: Uint32Array | null = null;

export function crc32(dados: Uint8Array): number {
  if (!tabelaCrc) {
    tabelaCrc = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      tabelaCrc[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = 0; i < dados.length; i++) crc = tabelaCrc[(crc ^ dados[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

/** Data/hora no formato MS-DOS (ano mínimo 1980). */
function dosDataHora(d: Date): { data: number; hora: number } {
  const ano = Math.max(1980, d.getFullYear());
  return {
    data: ((ano - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate(),
    hora: (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2),
  };
}

/** Monta o ZIP (nomes em UTF-8, flag 0x0800). */
export function criarZip(arquivos: readonly ArquivoZip[], quando: Date = new Date()): Uint8Array {
  const enc = new TextEncoder();
  const { data: dData, hora: dHora } = dosDataHora(quando);
  const partes: Uint8Array[] = [];
  const central: Uint8Array[] = [];
  let deslocamento = 0;

  for (const a of arquivos) {
    const nome = enc.encode(a.nome);
    const crc = crc32(a.dados);
    const local = new DataView(new ArrayBuffer(30));
    local.setUint32(0, 0x04034b50, true);
    local.setUint16(4, 20, true); // versão necessária
    local.setUint16(6, 0x0800, true); // UTF-8
    local.setUint16(8, 0, true); // STORE
    local.setUint16(10, dHora, true);
    local.setUint16(12, dData, true);
    local.setUint32(14, crc, true);
    local.setUint32(18, a.dados.length, true);
    local.setUint32(22, a.dados.length, true);
    local.setUint16(26, nome.length, true);
    local.setUint16(28, 0, true);
    partes.push(new Uint8Array(local.buffer), nome, a.dados);

    const c = new DataView(new ArrayBuffer(46));
    c.setUint32(0, 0x02014b50, true);
    c.setUint16(4, 20, true);
    c.setUint16(6, 20, true);
    c.setUint16(8, 0x0800, true);
    c.setUint16(10, 0, true);
    c.setUint16(12, dHora, true);
    c.setUint16(14, dData, true);
    c.setUint32(16, crc, true);
    c.setUint32(20, a.dados.length, true);
    c.setUint32(24, a.dados.length, true);
    c.setUint16(28, nome.length, true);
    c.setUint32(42, deslocamento, true);
    central.push(new Uint8Array(c.buffer), nome);

    deslocamento += 30 + nome.length + a.dados.length;
  }

  const tamCentral = central.reduce((s, p) => s + p.length, 0);
  const fim = new DataView(new ArrayBuffer(22));
  fim.setUint32(0, 0x06054b50, true);
  fim.setUint16(8, arquivos.length, true);
  fim.setUint16(10, arquivos.length, true);
  fim.setUint32(12, tamCentral, true);
  fim.setUint32(16, deslocamento, true);

  const todas = [...partes, ...central, new Uint8Array(fim.buffer)];
  const saida = new Uint8Array(todas.reduce((s, p) => s + p.length, 0));
  let pos = 0;
  for (const p of todas) {
    saida.set(p, pos);
    pos += p.length;
  }
  return saida;
}

export interface EntradaZip {
  nome: string;
  tamanho: number;
  crc: number;
  dados: Uint8Array;
}

/** Lê o diretório central de um ZIP STORE (usado nos testes e para validar o que geramos). */
export function lerZip(bytes: Uint8Array): EntradaZip[] {
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let fim = -1;
  for (let i = bytes.length - 22; i >= 0; i--) {
    if (v.getUint32(i, true) === 0x06054b50) {
      fim = i;
      break;
    }
  }
  if (fim < 0) throw new Error("ZIP inválido: diretório central não encontrado.");
  const total = v.getUint16(fim + 10, true);
  let pos = v.getUint32(fim + 16, true);
  const dec = new TextDecoder();
  const r: EntradaZip[] = [];
  for (let n = 0; n < total; n++) {
    if (v.getUint32(pos, true) !== 0x02014b50) throw new Error("ZIP inválido: entrada corrompida.");
    const metodo = v.getUint16(pos + 10, true);
    if (metodo !== 0) throw new Error("ZIP com compressão não suportado aqui.");
    const crc = v.getUint32(pos + 16, true);
    const tamanho = v.getUint32(pos + 24, true);
    const tamNome = v.getUint16(pos + 28, true);
    const tamExtra = v.getUint16(pos + 30, true);
    const tamCom = v.getUint16(pos + 32, true);
    const local = v.getUint32(pos + 42, true);
    const nome = dec.decode(bytes.subarray(pos + 46, pos + 46 + tamNome));
    const ini = local + 30 + v.getUint16(local + 26, true) + v.getUint16(local + 28, true);
    r.push({ nome, tamanho, crc, dados: bytes.subarray(ini, ini + tamanho) });
    pos += 46 + tamNome + tamExtra + tamCom;
  }
  return r;
}
