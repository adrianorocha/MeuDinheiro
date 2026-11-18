import { deleteObject, getDownloadURL, ref, uploadBytesResumable } from "firebase/storage";
import { anexoEhLocal, anexosDoPayload, caminhoStorage, extensaoAnexo, nomeAnexoSeguro, type Anexo } from "../finance/anexos";
import { useSession } from "../session";
import { firebaseConfigurado, getStorageClient } from "./client";

/** R60 - envio/consulta/exclusão dos arquivos de anexos no Firebase Storage (só com a nuvem ligada). */

/** uid cujo caminho recebe os arquivos (dono dos dados em uso, R32); null no modo local/sem login. */
export function uidDosAnexos(): string | null {
  const s = useSession.getState();
  if (s.modo !== "firebase" || !s.user || !firebaseConfigurado()) return null;
  return s.raizUid ?? s.user.uid;
}

function novoUuid(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}

function codigoErro(e: unknown): string {
  return typeof e === "object" && e && "code" in e ? String((e as { code: unknown }).code) : "";
}

export function descreverErroStorage(e: unknown): string {
  const code = codigoErro(e);
  if (code === "storage/unauthorized") return "Sem permissão para enviar o arquivo (verifique as regras do Storage e o login).";
  if (code === "storage/quota-exceeded") return "A cota do Storage foi excedida.";
  if (code === "storage/retry-limit-exceeded" || code === "storage/network-request-failed") return "Falha de conexão ao enviar o arquivo.";
  if (code === "storage/bucket-not-found" || code === "storage/project-not-found") return "O Firebase Storage ainda não foi ativado neste projeto.";
  if (code === "storage/canceled") return "Envio cancelado.";
  return "Não foi possível enviar o arquivo. Tente de novo.";
}

/** Envia um arquivo e devolve o anexo (metadados). Lança Error com mensagem pt-BR em caso de falha. */
export async function enviarAnexo(file: File, tipo: string, despesaId: number, aoProgredir?: (pct: number) => void): Promise<Anexo> {
  const uid = uidDosAnexos();
  if (!uid) throw new Error("Anexos exigem estar conectado à nuvem (Firebase).");
  const id = novoUuid();
  const caminho = caminhoStorage(uid, despesaId, id, tipo);
  if (!caminho || !extensaoAnexo(tipo)) throw new Error("Tipo de arquivo não permitido. Use JPEG, PNG ou PDF.");
  try {
    const tarefa = uploadBytesResumable(ref(getStorageClient(), caminho), file, { contentType: tipo });
    await new Promise<void>((resolve, reject) => {
      tarefa.on(
        "state_changed",
        (s) => aoProgredir?.(s.totalBytes > 0 ? Math.round((s.bytesTransferred / s.totalBytes) * 100) : 0),
        reject,
        () => resolve(),
      );
    });
  } catch (e) {
    throw new Error(descreverErroStorage(e));
  }
  return { id, nome: nomeAnexoSeguro(file.name), tipo, tamanho: file.size, caminho, criadoEm: Date.now() };
}

/** Melhor esforço: apaga o arquivo no Storage (ignora inexistente/sem permissão). Anexos "local:" não têm arquivo na nuvem. */
export async function excluirArquivoAnexo(a: Anexo): Promise<void> {
  if (anexoEhLocal(a.caminho) || !uidDosAnexos()) return;
  try {
    await deleteObject(ref(getStorageClient(), a.caminho));
  } catch {
    /* melhor esforço */
  }
}

/** Apaga os arquivos dos anexos que estavam nos itens da lixeira e não estão mais (esvaziar/purgar). */
export async function limparAnexosDaLixeira(antes: readonly { id: number; payload: string }[], depois: readonly { id: number }[]): Promise<void> {
  const ficam = new Set(depois.map((l) => l.id));
  for (const item of antes) {
    if (ficam.has(item.id)) continue;
    for (const a of anexosDoPayload(item.payload)) await excluirArquivoAnexo(a);
  }
}

/** URL temporária de leitura (visualizar/baixar). */
export async function urlDoAnexo(a: Anexo): Promise<string> {
  if (anexoEhLocal(a.caminho)) throw new Error("Este arquivo está só no aparelho onde foi anexado (não foi enviado para a nuvem).");
  if (!uidDosAnexos()) throw new Error("Entre com sua conta na nuvem para ver os anexos.");
  try {
    return await getDownloadURL(ref(getStorageClient(), a.caminho));
  } catch (e) {
    if (codigoErro(e) === "storage/object-not-found") throw new Error("O arquivo não existe mais no Storage.");
    throw new Error(descreverErroStorage(e));
  }
}
