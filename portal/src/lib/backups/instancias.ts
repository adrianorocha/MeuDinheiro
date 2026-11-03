import { useSession } from "../session";
import { criarHistoricoStore, criarTrilhaStore, kvIndexedDb } from "./armazenamento";

/** Escopo dos dados em uso: o uid do dono (Firebase) ou "local". */
function escopo(): string {
  const s = useSession.getState();
  return s.modo === "firebase" ? (s.raizUid ?? s.user?.uid ?? "firebase") : "local";
}

export const trilhaStore = criarTrilhaStore(kvIndexedDb, escopo);
export const historicoStore = criarHistoricoStore(kvIndexedDb, escopo);
