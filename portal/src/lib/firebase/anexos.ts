import { validarNovoAnexo } from "../finance/anexos";
import { acoes } from "../store/actions";
import { useStore } from "../store/store";
import { enviarAnexo, excluirArquivoAnexo } from "./anexosArquivos";

/** R60 - formulário de anexos: validação dos arquivos escolhidos e sincronização com o lançamento. */

/** O navegador às vezes não informa o MIME: completa pela extensão. */
export function tipoDoArquivo(f: File): string {
  const t = f.type.toLowerCase();
  if (t) return t === "image/jpg" ? "image/jpeg" : t;
  const ext = f.name.split(".").pop()?.toLowerCase();
  return ext === "jpg" || ext === "jpeg" ? "image/jpeg" : ext === "png" ? "image/png" : ext === "pdf" ? "application/pdf" : "";
}

export interface Pendente {
  chave: string;
  file: File;
  tipo: string;
  status: "aguardando" | "enviando" | "erro";
  pct: number;
  erro?: string;
}

let seqPendente = 0;

/** Valida os arquivos escolhidos e devolve os aceitos (como pendentes) + as mensagens de recusa (pt-BR). */
export function prepararPendentes(arquivos: File[], quantidadeAtual: number): { aceitos: Pendente[]; erros: string[] } {
  const aceitos: Pendente[] = [];
  const erros: string[] = [];
  for (const file of arquivos) {
    const tipo = tipoDoArquivo(file);
    const msg = validarNovoAnexo(tipo, file.size, quantidadeAtual + aceitos.length);
    if (msg) erros.push(`${file.name}: ${msg}`);
    else aceitos.push({ chave: `p${Date.now()}-${seqPendente++}`, file, tipo, status: "aguardando", pct: 0 });
  }
  return { aceitos, erros };
}

/**
 * Aplica as mudanças de anexos ao lançamento [despesaId]: envia os pendentes (um a um, com progresso), remove os
 * [removidos] e grava a lista final no lançamento. O lançamento nunca é desfeito: o que falhar continua pendente (`erro`).
 */
export async function sincronizarAnexos(
  despesaId: number,
  pendentes: Pendente[],
  removidos: string[],
  aoMudar: (chave: string, parcial: Partial<Pendente>) => void,
): Promise<{ falhas: number; enviados: string[] }> {
  const novos = [];
  const enviados: string[] = [];
  let falhas = 0;
  for (const p of pendentes) {
    if (p.status === "enviando") continue;
    aoMudar(p.chave, { status: "enviando", pct: 0, erro: undefined });
    try {
      const a = await enviarAnexo(p.file, p.tipo, despesaId, (pct) => aoMudar(p.chave, { pct }));
      novos.push(a);
      enviados.push(p.chave);
    } catch (e) {
      falhas++;
      aoMudar(p.chave, { status: "erro", pct: 0, erro: e instanceof Error ? e.message : "Falha no envio." });
    }
  }
  // lista final lida do estado ATUAL (outro aparelho pode ter mudado enquanto enviava)
  const atual = useStore.getState().ds.despesas.find((d) => d.id === despesaId);
  const existentes = atual?.anexos ?? [];
  const aApagar = existentes.filter((a) => removidos.includes(a.id));
  if (novos.length > 0 || aApagar.length > 0) {
    const r = acoes.editLancamento(despesaId, { anexos: [...existentes.filter((a) => !removidos.includes(a.id)), ...novos] });
    if (!r.ok) {
      // lançamento sumiu/bloqueado: não deixa arquivo órfão
      for (const a of novos) await excluirArquivoAnexo(a);
      return { falhas: falhas + novos.length, enviados: [] };
    }
    for (const a of aApagar) await excluirArquivoAnexo(a);
  }
  return { falhas, enviados };
}
