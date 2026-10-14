"use client";

import { BellRing } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Field";
import { Card, CardTitulo } from "@/components/ui/Misc";
import {
  ativarPush,
  desativarPush,
  navegadorSuportaPush,
  permissaoAtual,
  prefsLocais,
  pushLigado,
  salvarPrefsLocais,
  sincronizarPush,
  vapidConfigurada,
} from "@/lib/firebase/push";
import { estadoPush, ROTULO_PREFS, textoDoEstado, type PermissaoNavegador, type PrefsPush } from "@/lib/push";
import { useSession } from "@/lib/session";
import { useStore } from "@/lib/store/store";

/**
 * R65 - web push opcional em Configurações. Oculto sem `NEXT_PUBLIC_FIREBASE_VAPID_KEY`, sem suporte do navegador ou fora do
 * modo nuvem. Estados: sem permissão / bloqueado / inativo / ativo. As preferências por tipo valem só neste navegador e são
 * espelhadas no documento do dispositivo (campo `prefs`) para as Cloud Functions respeitarem.
 */
export function NotificacoesPush() {
  const user = useSession((s) => s.user);
  const raizUid = useSession((s) => s.raizUid);
  const avisar = useStore((s) => s.avisar);
  const [pronto, setPronto] = useState(false);
  const [suportado, setSuportado] = useState(false);
  const [permissao, setPermissao] = useState<PermissaoNavegador>("default");
  const [ligado, setLigado] = useState(false);
  const [prefs, setPrefs] = useState<PrefsPush>(() => prefsLocais());
  const [ocupado, setOcupado] = useState(false);
  const uid = user?.uid ?? null;

  useEffect(() => {
    // lê o estado do navegador só no cliente (evita divergência de hidratação); adiado para fora do corpo síncrono do efeito
    const t = setTimeout(() => {
      setSuportado(navegadorSuportaPush());
      setPermissao(permissaoAtual());
      setLigado(uid ? pushLigado(uid) : false);
      setPrefs(prefsLocais());
      setPronto(true);
    }, 0);
    return () => clearTimeout(t);
  }, [uid]);

  const estado = estadoPush({
    vapidConfigurada: vapidConfigurada(),
    suportado: pronto && suportado,
    nuvemEUsuario: uid !== null,
    permissao,
    ligado,
  });
  if (estado === "oculto" || !uid) return null;

  async function ativar() {
    if (!uid) return;
    setOcupado(true);
    const r = await ativarPush(uid, raizUid, prefs);
    setPermissao(permissaoAtual());
    setLigado(pushLigado(uid));
    setOcupado(false);
    if (r === "ok") avisar("sucesso", "Notificações ativadas neste navegador.");
    else if (r === "negado") avisar("erro", "Permissão de notificações não concedida.");
    else avisar("erro", "Não foi possível ativar as notificações agora (navegador sem suporte a push ou FCM indisponível).");
  }

  async function desativar() {
    if (!uid) return;
    setOcupado(true);
    await desativarPush(uid);
    setLigado(false);
    setOcupado(false);
    avisar("sucesso", "Notificações desativadas neste navegador.");
  }

  async function alterar(chave: keyof PrefsPush, valor: boolean) {
    const novo = { ...prefs, [chave]: valor };
    setPrefs(novo);
    salvarPrefsLocais(novo);
    if (uid && estado === "ativo") await sincronizarPush(uid, raizUid);
  }

  return (
    <Card aria-labelledby="t-push">
      <CardTitulo id="t-push">Notificações push (neste navegador)</CardTitulo>
      <p className="mb-3 text-sm text-muted">{textoDoEstado(estado)}</p>
      {(estado === "sem-permissao" || estado === "inativo") && (
        <Button variante="primary" icone={<BellRing className="mr-2 size-4" aria-hidden />} onClick={ativar} disabled={ocupado}>
          Ativar notificações neste navegador
        </Button>
      )}
      {estado === "ativo" && (
        <div className="space-y-2">
          {ROTULO_PREFS.map((p) => (
            <Checkbox key={p.chave} rotulo={p.rotulo} checked={prefs[p.chave]} onChange={(e) => void alterar(p.chave, e.target.checked)} />
          ))}
          <div className="pt-2">
            <Button onClick={desativar} disabled={ocupado}>
              Desativar neste navegador
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
