import { aplicarRegras } from "./regras";
import { normalizar } from "./texto";
import type { RegraCategoria, Tipo } from "./types";

/**
 * R62 - Lançar por texto/voz. `interpretarLancamento(texto, ctx)` é PURA e espelha `domain/LancamentoTexto.kt`
 * (mesmos casos em `lancamentoTexto.test.ts` e `LancamentoTextoTest`). NUNCA salva nada: devolve os campos
 * entendidos, a confiança de cada um e as pendências (o que faltou ou ficou ambíguo); quem chama abre o formulário
 * já preenchido para o usuário revisar e confirmar.
 */

export type Confianca = "ALTA" | "MEDIA" | "BAIXA";

export interface Candidato {
  tipo: "CONTA" | "CARTAO";
  /** número da conta (`Conta.conta`) ou id do cartão em texto */
  id: string;
  nome: string;
}

export interface ContextoTexto {
  /** `id` = número da conta (`Conta.conta`), `nome` = banco */
  contas: readonly { id: string; nome: string }[];
  cartoes: readonly { id: number; nome: string }[];
  /** nomes das categorias existentes (padrão + personalizadas) */
  categorias: readonly string[];
  regras: readonly RegraCategoria[];
  /** "agora" injetado (epoch ms, fuso local) */
  agora: number;
}

export interface DataInterpretada {
  ano: number;
  mes: number;
  dia: number;
}

export interface Interpretacao {
  tipo: Tipo;
  /** valor TOTAL em centavos; null = não entendi */
  valorCentavos: number | null;
  descricao: string;
  data: DataInterpretada;
  /** conta/cartão escolhido (só quando inequívoco) */
  destino: Candidato | null;
  /** opções quando ambíguo (não escolhe sozinho); vazio quando há destino ou nada foi citado */
  candidatos: Candidato[];
  parcelas: number | null;
  categoria: string | null;
  confianca: { tipo: Confianca; valor: Confianca; data: Confianca; descricao: Confianca; destino: Confianca; categoria: Confianca; parcelas: Confianca };
  pendencias: string[];
}

export function dataParaMs(d: DataInterpretada): number {
  return new Date(d.ano, d.mes - 1, d.dia, 12, 0, 0, 0).getTime();
}

/** 2..120, o mesmo teto do formulário (`MAX_PARCELAS`). */
const PARCELAS_MAX = 120;

// ---------------------------------------------------------------- vocabulário (idêntico ao Kotlin)

const VERBOS_DEBITO = ["gastei", "gasto", "gastamos", "paguei", "pagamos", "comprei", "compramos", "debitou", "saiu", "torrei", "parcelei"];
const VERBOS_CREDITO = ["recebi", "recebemos", "ganhei", "ganhamos", "entrou", "caiu", "vendi", "depositaram", "depositou"];
const SUBST_CREDITO = ["salario", "deposito", "rendimento", "rendimentos", "reembolso", "cashback", "dividendos", "freela", "freelance", "bonus", "comissao", "mesada"];
const PARCELAMENTO = ["parcelei", "parcelado", "parcelada", "parcelar", "parcelamos"];
const PALAVRAS_CARTAO = ["cartao", "credito"];
const PALAVRAS_CONTA = ["debito", "conta"];
const MOEDA_SUFIXO = ["reais", "real", "conto", "contos", "pila", "pilas"];
const STOP = ["no", "na", "nos", "nas", "em", "de", "do", "da", "dos", "das", "pro", "pra", "para", "com", "um", "uma", "o", "a", "os", "as", "pelo", "pela", "e", "ao", "por", "foi", "deu", "eu"];
const STOP_NOME = ["banco", "cartao", "conta", "credito", "debito", "de", "do", "da", "dos", "das", "sa", "card", "bank"];
const DIAS_SEMANA: Record<string, number> = { domingo: 0, segunda: 1, terca: 2, quarta: 3, quinta: 4, sexta: 5, sabado: 6 };
const PASSADA = ["passada", "passado", "passadas", "passados"];

/** palavra-chave -> categorias preferidas (a 1ª que existir; senão a 1ª que for categoria padrão). */
const CATEGORIAS_PADRAO_NOMES = ["Combustível", "Alimentação", "Transporte", "Compras", "Cinema", "Saúde", "Educação", "Salário", "Oficina", "Supermercado", "Academia", "Jogos", "Bebidas", "Lanche", "Reserva"];

const MAPA_DEBITO: [string[], string[]][] = [
  [["mercado", "supermercado", "atacadao", "sacolao", "hortifruti", "acougue"], ["Supermercado"]],
  [["almoco", "janta", "jantar", "cafe", "restaurante", "pizza", "ifood", "delivery", "marmita", "comida", "churrasco", "padaria", "sorvete", "pastel", "rodizio"], ["Alimentação"]],
  [["lanche", "lanchonete", "hamburguer", "sanduiche", "salgado"], ["Lanche", "Alimentação"]],
  [["bebida", "bebidas", "cerveja", "vinho", "chopp", "bar", "drink"], ["Bebidas", "Lazer"]],
  [["gasolina", "combustivel", "etanol", "diesel", "alcool", "posto"], ["Combustível", "Transporte"]],
  [["uber", "taxi", "onibus", "metro", "passagem", "pedagio", "estacionamento", "cabify", "trem", "brt"], ["Transporte"]],
  [["farmacia", "drogaria", "remedio", "remedios", "medico", "dentista", "consulta", "exame", "hospital", "psicologo", "saude"], ["Saúde"]],
  [["escola", "faculdade", "curso", "livro", "apostila", "matricula"], ["Educação"]],
  [["academia"], ["Academia"]],
  [["cinema", "filme", "ingresso"], ["Cinema", "Lazer"]],
  [["jogo", "jogos", "steam", "playstation", "xbox"], ["Jogos"]],
  [["oficina", "mecanico", "pneu", "revisao"], ["Oficina", "Transporte"]],
  [["roupa", "roupas", "sapato", "tenis", "camisa", "shopping", "loja"], ["Compras"]],
  [["luz", "energia", "eletricidade"], ["Luz", "Energia", "Energia elétrica", "Contas", "Casa", "Moradia"]],
  [["agua", "esgoto"], ["Água", "Contas", "Casa", "Moradia"]],
  [["internet", "wifi", "telefone", "celular"], ["Internet", "Telefone", "Contas", "Casa"]],
  [["aluguel", "condominio"], ["Aluguel", "Moradia", "Casa"]],
  [["gas"], ["Gás", "Contas", "Casa"]],
  [["netflix", "spotify", "assinatura", "streaming"], ["Assinaturas", "Lazer"]],
];

const MAPA_CREDITO: [string[], string[]][] = [
  [["salario", "ordenado"], ["Salário"]],
  [["freela", "freelance", "bonus", "comissao"], ["Freelance", "Extra", "Outras receitas"]],
  [["reembolso"], ["Reembolso"]],
  [["rendimento", "rendimentos", "dividendos"], ["Rendimentos", "Investimentos"]],
];

// ---------------------------------------------------------------- tokens

interface Tok {
  orig: string;
  n: string;
  usado: boolean;
}

function tokenizar(texto: string): Tok[] {
  const out: Tok[] = [];
  for (const raw of texto.trim().split(/\s+/)) {
    const limpo = raw.replace(/^[,;:!?()"'“”]+/, "").replace(/[,;:!?()"'“”.]+$/, "");
    const n = normalizar(limpo);
    if (n) out.push({ orig: limpo, n, usado: false });
  }
  return out;
}

const ehNumero = (s: string) => /^\d+([.,]\d+)*$/.test(s);

/** "1.234,56" / "3.500" / "120,50" / "18.90" / "45" -> centavos; inválido -> null. */
export function numeroEmCentavos(t: string): number | null {
  if (!ehNumero(t)) return null;
  const ultV = t.lastIndexOf(",");
  const ultP = t.lastIndexOf(".");
  let inteiro = t;
  let dec = "";
  if (ultV >= 0 && ultP >= 0) {
    const sep = Math.max(ultV, ultP);
    dec = t.slice(sep + 1);
    inteiro = t.slice(0, sep).replace(/[.,]/g, "");
  } else if (ultV >= 0 || ultP >= 0) {
    const sep = Math.max(ultV, ultP);
    const ch = t[sep];
    const qtd = t.split(ch).length - 1;
    const depois = t.slice(sep + 1);
    if (qtd > 1) {
      // 1.234.567 (milhar repetido); vírgula repetida é inválida
      if (ch === ",") return null;
      inteiro = t.replace(/\./g, "");
    } else if (ch === "." && depois.length === 3 && t.slice(0, sep) !== "0" && t.slice(0, sep).length <= 3) {
      inteiro = t.replace(".", "");
    } else {
      inteiro = t.slice(0, sep);
      dec = depois;
    }
  }
  if (dec.length > 2 || inteiro === "" || inteiro.length > 12) return null;
  const c = Number(inteiro) * 100 + (dec ? Number(dec.padEnd(2, "0")) : 0);
  return Number.isFinite(c) ? c : null;
}

// ---------------------------------------------------------------- datas

function diasNoMes(mes: number, ano: number): number {
  return new Date(ano, mes, 0).getDate();
}

function dataValida(ano: number, mes: number, dia: number): boolean {
  if (mes < 1 || mes > 12 || dia < 1) return false;
  return dia <= diasNoMes(mes, ano);
}

function deslocar(hoje: DataInterpretada, dias: number): DataInterpretada {
  const d = new Date(hoje.ano, hoje.mes - 1, hoje.dia - dias, 12, 0, 0, 0);
  return { ano: d.getFullYear(), mes: d.getMonth() + 1, dia: d.getDate() };
}

const cmp = (a: DataInterpretada, b: DataInterpretada) => a.ano * 10000 + a.mes * 100 + a.dia - (b.ano * 10000 + b.mes * 100 + b.dia);

/** "dia N": o dia N mais recente que não esteja no futuro (mês corrente se N <= hoje; senão meses anteriores que tenham o dia N). */
function ultimoDiaN(hoje: DataInterpretada, n: number): { data: DataInterpretada; voltou: boolean } {
  for (let k = 0; k < 24; k++) {
    const idx = hoje.ano * 12 + (hoje.mes - 1) - k;
    const ano = Math.floor(idx / 12);
    const mes = (idx % 12) + 1;
    if (k === 0) {
      if (n <= hoje.dia) return { data: { ano, mes, dia: n }, voltou: false };
    } else if (n <= diasNoMes(mes, ano)) return { data: { ano, mes, dia: n }, voltou: true };
  }
  return { data: hoje, voltou: false };
}

// ---------------------------------------------------------------- destino (conta/cartão)

interface Pontuado {
  c: Candidato;
  pontos: number;
  usados: number[];
}

function tokensDoNome(nome: string): string[] {
  return normalizar(nome)
    .split(/[^a-z0-9]+/)
    .filter((t) => t.length >= 3 && !STOP_NOME.includes(t));
}

function pontuar(c: Candidato, toks: Tok[]): Pontuado {
  const nomes = tokensDoNome(c.nome);
  const juntoNome = normalizar(c.nome).replace(/[^a-z0-9]/g, "");
  let pontos = 0;
  const usados: number[] = [];
  for (const nt of nomes) {
    let melhor = 0;
    let idx = -1;
    toks.forEach((t, i) => {
      if (t.usado || ehNumero(t.n)) return;
      let p = 0;
      if (t.n === nt) p = 3;
      else if (t.n.length >= 4 && nt.startsWith(t.n)) p = 2;
      if (p > melhor) {
        melhor = p;
        idx = i;
      }
    });
    if (melhor > 0) {
      pontos += melhor;
      usados.push(idx);
    }
  }
  // "mercado pago" escrito em duas palavras para um nome "MercadoPago"
  if (pontos === 0 && juntoNome.length >= 5) {
    for (let i = 0; i + 1 < toks.length; i++) {
      if (!toks[i].usado && !toks[i + 1].usado && toks[i].n + toks[i + 1].n === juntoNome) {
        pontos = 3;
        usados.push(i, i + 1);
        break;
      }
    }
  }
  return { c, pontos, usados };
}

function nomeCandidato(c: Candidato): string {
  return `${c.nome} (${c.tipo === "CONTA" ? "conta" : "cartão"})`;
}

// ---------------------------------------------------------------- categoria

function categoriaPorPalavra(descTokens: string[], tipo: Tipo, existentes: readonly string[]): string | null {
  const mapa = tipo === "CREDITO" ? MAPA_CREDITO : MAPA_DEBITO;
  const existe = (nome: string) => existentes.find((e) => normalizar(e) === normalizar(nome));
  for (const t of descTokens) {
    for (const [chaves, prefs] of mapa) {
      if (!chaves.includes(t)) continue;
      // a 1ª preferida que exista; as categorias padrão do app contam como existentes
      for (const p of prefs) {
        const e = existe(p) ?? CATEGORIAS_PADRAO_NOMES.find((x) => normalizar(x) === normalizar(p));
        if (e) return e;
      }
      return null;
    }
  }
  return null;
}

// ---------------------------------------------------------------- principal

const fmtBRL = (c: number) => {
  const inteiro = String(Math.floor(c / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `R$ ${inteiro},${String(c % 100).padStart(2, "0")}`;
};

export function interpretarLancamento(texto: string, ctx: ContextoTexto): Interpretacao {
  const toks = tokenizar(texto);
  const pend: string[] = [];
  const conf: Interpretacao["confianca"] = { tipo: "BAIXA", valor: "BAIXA", data: "BAIXA", descricao: "BAIXA", destino: "BAIXA", categoria: "BAIXA", parcelas: "BAIXA" };
  const ag = new Date(ctx.agora);
  const hoje: DataInterpretada = { ano: ag.getFullYear(), mes: ag.getMonth() + 1, dia: ag.getDate() };
  const usar = (...idx: number[]) => idx.forEach((i) => (toks[i].usado = true));
  const livre = (i: number) => i >= 0 && i < toks.length && !toks[i].usado;
  const n = (i: number) => (i >= 0 && i < toks.length ? toks[i].n : "");

  // ---- 1) data
  let data = hoje;
  for (let i = 0; i < toks.length; i++) {
    if (!livre(i)) continue;
    const t = toks[i].n;
    if (t === "hoje") {
      data = hoje;
      conf.data = "ALTA";
      usar(i);
    } else if (t === "ontem") {
      data = deslocar(hoje, 1);
      conf.data = "ALTA";
      usar(i);
    } else if (t === "anteontem") {
      data = deslocar(hoje, 2);
      conf.data = "ALTA";
      usar(i);
    } else if (t === "antes" && n(i + 1) === "de" && n(i + 2) === "ontem" && livre(i + 1) && livre(i + 2)) {
      data = deslocar(hoje, 2);
      conf.data = "ALTA";
      usar(i, i + 1, i + 2);
    } else if (t === "dia" && livre(i + 1) && /^\d{1,2}$/.test(n(i + 1))) {
      const dia = Number(n(i + 1));
      usar(i, i + 1);
      if (dia < 1 || dia > 31) {
        pend.push(`Dia ${dia} inválido: usei hoje.`);
      } else {
        const r = ultimoDiaN(hoje, dia);
        data = r.data;
        conf.data = r.voltou ? "MEDIA" : "ALTA";
      }
    } else if (/^\d{1,2}\/\d{1,2}(\/(\d{2}|\d{4}))?$/.test(t) || (t === "dia" && livre(i + 1) && /^\d{1,2}\/\d{1,2}(\/(\d{2}|\d{4}))?$/.test(n(i + 1)))) {
      const alvo = t === "dia" ? n(i + 1) : t;
      if (t === "dia") usar(i, i + 1);
      else usar(i);
      const p = alvo.split("/");
      const dia = Number(p[0]);
      const mes = Number(p[1]);
      if (p.length === 3) {
        const ano = p[2].length === 2 ? 2000 + Number(p[2]) : Number(p[2]);
        if (dataValida(ano, mes, dia)) {
          data = { ano, mes, dia };
          conf.data = "ALTA";
        } else pend.push(`Data inválida (${alvo}): usei hoje.`);
      } else if (dataValida(hoje.ano, mes, dia)) {
        const nesteAno = { ano: hoje.ano, mes, dia };
        if (cmp(nesteAno, hoje) <= 0) {
          data = nesteAno;
          conf.data = "ALTA";
        } else if (dataValida(hoje.ano - 1, mes, dia)) {
          data = { ano: hoje.ano - 1, mes, dia };
          conf.data = "MEDIA";
        } else pend.push(`Data inválida (${alvo}): usei hoje.`);
      } else pend.push(`Data inválida (${alvo}): usei hoje.`);
    } else {
      const base = t.replace(/-feira$/, "");
      if (base in DIAS_SEMANA) {
        const alvo = DIAS_SEMANA[base];
        usar(i);
        let estrita = false;
        let j = i + 1;
        if (livre(j) && n(j) === "feira") {
          usar(j);
          j++;
        }
        if (livre(j) && PASSADA.includes(n(j))) {
          usar(j);
          estrita = true;
        }
        let delta = (new Date(hoje.ano, hoje.mes - 1, hoje.dia, 12).getDay() - alvo + 7) % 7;
        if (estrita && delta === 0) delta = 7;
        data = deslocar(hoje, delta);
        conf.data = estrita ? "ALTA" : "MEDIA";
      }
    }
  }

  // ---- 2) parcelas
  let parcelas: number | null = null;
  let valorAposParcela = -1;
  let pediuParcelamento = false;
  for (let i = 0; i < toks.length; i++) {
    if (!livre(i)) continue;
    const t = toks[i].n;
    const mx = /^(\d{1,3})x$/.exec(t);
    if (mx) {
      usar(i);
      const k = Number(mx[1]);
      if (k >= 2 && k <= PARCELAS_MAX) {
        parcelas = k;
        conf.parcelas = "ALTA";
        if (n(i + 1) === "de" && livre(i + 1) && ehNumero(n(i + 2)) && livre(i + 2)) {
          usar(i + 1);
          valorAposParcela = i + 2;
        }
      } else if (k > PARCELAS_MAX) pend.push(`Parcelas demais (${k}): o máximo é ${PARCELAS_MAX}.`);
    } else if (/^\d{1,3}$/.test(t) && ["vezes", "parcelas", "parcela", "vez"].includes(n(i + 1)) && livre(i + 1)) {
      usar(i, i + 1);
      const k = Number(t);
      if (k >= 2 && k <= PARCELAS_MAX) {
        parcelas = k;
        conf.parcelas = "ALTA";
      } else if (k > PARCELAS_MAX) pend.push(`Parcelas demais (${k}): o máximo é ${PARCELAS_MAX}.`);
    }
  }
  for (let i = 0; i < toks.length; i++) {
    if (livre(i) && PARCELAMENTO.includes(toks[i].n)) {
      pediuParcelamento = true;
      if (toks[i].n !== "parcelei") usar(i);
    }
  }

  // ---- 3) valor
  interface Cand {
    ini: number;
    fim: number;
    centavos: number;
    marcado: boolean;
  }
  const cands: Cand[] = [];
  for (let i = 0; i < toks.length; i++) {
    if (!livre(i)) continue;
    let marcado = false;
    const ini = i;
    let numTxt = "";
    let fim = i;
    const t = toks[i].n;
    if (t === "r$" && livre(i + 1) && ehNumero(n(i + 1))) {
      numTxt = n(i + 1);
      fim = i + 1;
      marcado = true;
    } else if (t.startsWith("r$") && ehNumero(t.slice(2))) {
      numTxt = t.slice(2);
      marcado = true;
    } else if (ehNumero(t)) {
      numTxt = t;
    } else {
      const mk = /^(\d+([.,]\d+)?)k$/.exec(t);
      if (mk) {
        const c = numeroEmCentavos(mk[1]);
        if (c !== null) cands.push({ ini: i, fim: i, centavos: c * 1000, marcado: false });
      } else {
        const mm = /^(\d+([.,]\d+)?)mil$/.exec(t);
        if (mm) {
          const c = numeroEmCentavos(mm[1]);
          if (c !== null) cands.push({ ini: i, fim: i, centavos: c * 1000, marcado: false });
        }
      }
      continue;
    }
    let c = numeroEmCentavos(numTxt);
    if (c === null) continue;
    if (livre(fim + 1) && n(fim + 1) === "mil") {
      c *= 1000;
      fim += 1;
    }
    if (livre(fim + 1) && MOEDA_SUFIXO.includes(n(fim + 1))) {
      marcado = true;
      fim += 1;
    }
    if (c <= 0) continue;
    cands.push({ ini, fim, centavos: c, marcado });
    i = fim;
  }
  let valor: number | null = null;
  const porParcela = valorAposParcela >= 0 ? cands.find((c) => c.ini === valorAposParcela) : undefined;
  if (porParcela && parcelas) {
    valor = porParcela.centavos * parcelas;
    for (let k = porParcela.ini; k <= porParcela.fim; k++) toks[k].usado = true;
    conf.valor = "MEDIA";
  } else {
    const pool = cands.some((c) => c.marcado) ? cands.filter((c) => c.marcado) : cands;
    if (pool.length > 0) {
      const c = pool[0];
      valor = c.centavos;
      for (let k = c.ini; k <= c.fim; k++) toks[k].usado = true;
      conf.valor = pool.length > 1 ? "MEDIA" : "ALTA";
      if (pool.length > 1) pend.push(`Encontrei mais de um valor (${fmtBRL(pool[0].centavos)} e ${fmtBRL(pool[1].centavos)}): usei o primeiro.`);
    } else {
      const i = toks.findIndex((t) => !t.usado && t.n === "mil");
      if (i >= 0) {
        valor = 100_000;
        usar(i);
        if (livre(i + 1) && MOEDA_SUFIXO.includes(n(i + 1))) usar(i + 1);
        conf.valor = "MEDIA";
      }
    }
  }
  if (valor === null) pend.push('Não entendi o valor. Diga, por exemplo, "45 reais" ou "R$ 45,90".');

  // ---- 4) tipo
  let deb = 0;
  let cred = 0;
  for (let i = 0; i < toks.length; i++) {
    if (!livre(i)) continue;
    if (VERBOS_DEBITO.includes(toks[i].n)) {
      deb++;
      usar(i);
    } else if (VERBOS_CREDITO.includes(toks[i].n)) {
      cred++;
      usar(i);
    }
  }
  if (pediuParcelamento && deb === 0 && cred === 0) deb = 1;
  let tipo: Tipo = "DEBITO";
  if (deb > 0 && cred > 0) {
    // conflito: vale o primeiro verbo citado
    const primeiro = toks.findIndex((t) => VERBOS_DEBITO.includes(t.n) || VERBOS_CREDITO.includes(t.n));
    tipo = primeiro >= 0 && VERBOS_CREDITO.includes(toks[primeiro].n) ? "CREDITO" : "DEBITO";
    conf.tipo = "BAIXA";
    pend.push("Não ficou claro se é despesa ou receita: confira o tipo.");
  } else if (cred > 0) {
    tipo = "CREDITO";
    conf.tipo = "ALTA";
  } else if (deb > 0) {
    conf.tipo = "ALTA";
  } else if (toks.some((t) => SUBST_CREDITO.includes(t.n))) {
    tipo = "CREDITO";
    conf.tipo = "MEDIA";
  } else {
    conf.tipo = "BAIXA";
  }

  // ---- 5) conta / cartão
  let flagCartao = pediuParcelamento || parcelas !== null;
  let explicitoCartao = false;
  let flagConta = false;
  for (let i = 0; i < toks.length; i++) {
    if (!livre(i)) continue;
    if (PALAVRAS_CARTAO.includes(toks[i].n)) {
      explicitoCartao = true;
      flagCartao = true;
      usar(i);
    } else if (PALAVRAS_CONTA.includes(toks[i].n)) {
      flagConta = true;
      usar(i);
    }
  }
  const todos: Candidato[] = [
    ...ctx.contas.map((c) => ({ tipo: "CONTA" as const, id: c.id, nome: c.nome })),
    ...ctx.cartoes.map((c) => ({ tipo: "CARTAO" as const, id: String(c.id), nome: c.nome })),
  ];
  let pontuados = todos.map((c) => pontuar(c, toks)).filter((p) => p.pontos > 0);
  let destino: Candidato | null = null;
  let candidatos: Candidato[] = [];
  const dica = explicitoCartao ? "CARTAO" : flagConta ? "CONTA" : null;
  let tipoSemNome = false;
  if (dica) {
    const filtrados = pontuados.filter((p) => p.c.tipo === dica);
    // citou um nome que só existe no outro tipo (ex.: "cartão nubank" sem cartão Nubank): não adivinha
    tipoSemNome = filtrados.length === 0 && pontuados.length > 0;
    pontuados = filtrados;
  } else if (flagCartao) {
    const soCartao = pontuados.filter((p) => p.c.tipo === "CARTAO");
    if (soCartao.length > 0) pontuados = soCartao;
  }
  if (pontuados.length > 0) {
    const topo = Math.max(...pontuados.map((p) => p.pontos));
    const melhores = pontuados.filter((p) => p.pontos === topo);
    for (const p of melhores) p.usados.forEach((i) => (toks[i].usado = true));
    if (melhores.length === 1) {
      destino = melhores[0].c;
      conf.destino = "ALTA";
    } else {
      candidatos = melhores.map((p) => p.c);
      pend.push(`Qual conta ou cartão? Encontrei: ${candidatos.map(nomeCandidato).join(", ")}.`);
    }
  } else if (tipoSemNome) {
    const doTipo = todos.filter((c) => c.tipo === dica);
    candidatos = doTipo;
    pend.push(`Não achei ${dica === "CARTAO" ? "um cartão" : "uma conta"} com esse nome. Opções: ${doTipo.length ? doTipo.map((c) => c.nome).join(", ") : "nenhuma cadastrada"}.`);
  } else if (explicitoCartao || (flagCartao && pediuParcelamento)) {
    // "no cartão" sem nome: se só há um cartão, é ele; senão devolve as opções
    if (ctx.cartoes.length === 1) {
      destino = { tipo: "CARTAO", id: String(ctx.cartoes[0].id), nome: ctx.cartoes[0].nome };
      conf.destino = "MEDIA";
    } else if (ctx.cartoes.length > 1) {
      candidatos = ctx.cartoes.map((c) => ({ tipo: "CARTAO" as const, id: String(c.id), nome: c.nome }));
      pend.push(`Qual cartão? Encontrei: ${candidatos.map((c) => c.nome).join(", ")}.`);
    } else {
      pend.push("Você falou em cartão, mas não há cartões cadastrados.");
    }
  }

  // ---- 6) descrição (o que sobrou, sem as preposições soltas nas pontas)
  const segmentos: string[] = [];
  let atual: Tok[] = [];
  const fecha = () => {
    let a = 0;
    let b = atual.length;
    while (a < b && STOP.includes(atual[a].n)) a++;
    while (b > a && STOP.includes(atual[b - 1].n)) b--;
    if (b > a) segmentos.push(atual.slice(a, b).map((t) => t.orig).join(" "));
    atual = [];
  };
  for (const t of toks) {
    if (t.usado) fecha();
    else atual.push(t);
  }
  fecha();
  let descricao = segmentos.join(" ").trim().slice(0, 120);
  if (descricao) {
    descricao = descricao.charAt(0).toUpperCase() + descricao.slice(1);
    conf.descricao = "ALTA";
  } else {
    pend.push('Não entendi o que foi. Diga, por exemplo, "mercado" ou "almoço".');
  }
  if (pediuParcelamento && parcelas === null) pend.push('Em quantas parcelas? Diga, por exemplo, "em 3x".');

  // ---- 7) categoria (regras R55 -> palavras-chave)
  let categoria: string | null = null;
  if (descricao) {
    const contaRegra = destino?.tipo === "CONTA" ? destino.id : undefined;
    const r = aplicarRegras(ctx.regras, { descricao, conta: contaRegra, tipo });
    if (r) {
      categoria = r.categoria.trim();
      conf.categoria = "ALTA";
    } else {
      const palavras = normalizar(descricao).split(/[^a-z0-9]+/).filter(Boolean);
      categoria = categoriaPorPalavra(palavras, tipo, ctx.categorias);
      if (categoria) conf.categoria = "MEDIA";
    }
  }

  return { tipo, valorCentavos: valor, descricao, data, destino, candidatos, parcelas, categoria, confianca: conf, pendencias: pend };
}

/** Texto curto, pt-BR, do que foi entendido (para a prévia). */
export function resumoInterpretacao(i: Interpretacao): string[] {
  const l: string[] = [];
  l.push(i.tipo === "CREDITO" ? "Receita" : "Despesa");
  if (i.valorCentavos !== null) l.push(fmtBRL(i.valorCentavos) + (i.parcelas ? ` em ${i.parcelas}x` : ""));
  if (i.descricao) l.push(i.descricao);
  l.push(`${String(i.data.dia).padStart(2, "0")}/${String(i.data.mes).padStart(2, "0")}/${i.data.ano}`);
  if (i.destino) l.push(nomeCandidato(i.destino));
  if (i.categoria) l.push(i.categoria);
  return l;
}
