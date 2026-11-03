import { doc, onSnapshot, setDoc } from "firebase/firestore";
import { definirEnviadorNuvem, useConfigGeral } from "../configGeral";
import type { ConfigGeral } from "../finance/configuracoes";
import { getDb } from "./client";

/**
 * R57 - sincroniza `users/{uid}/configuracoes/geral` com o armazenamento local. Falhas de rede/permissão nunca
 * quebram o app: o local segue valendo. Retorna a função de cancelamento.
 */
export function iniciarConfigNuvem(uid: string): () => void {
  const ref = doc(getDb(), "users", uid, "configuracoes", "geral");
  const enviar = (c: ConfigGeral) => {
    // `undefined` não é aceito pelo Firestore: JSON remove os campos ausentes.
    void setDoc(ref, JSON.parse(JSON.stringify(c))).catch(() => undefined);
  };
  definirEnviadorNuvem(enviar);
  const cancelar = onSnapshot(
    ref,
    (snap) => {
      // escrita local ainda não confirmada: o próprio ecoa o que acabamos de gravar
      if (snap.metadata.hasPendingWrites) return;
      const acao = useConfigGeral.getState().receberDaNuvem(snap.exists() ? snap.data() : null);
      if (acao === "ENVIAR_LOCAL") enviar(useConfigGeral.getState().config);
    },
    () => undefined,
  );
  return () => {
    cancelar();
    definirEnviadorNuvem(null);
  };
}
