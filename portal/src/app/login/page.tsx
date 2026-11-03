"use client";

import { Cloud, HardDrive, TriangleAlert, Wallet } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Input, Segmentado } from "@/components/ui/Field";
import { ErroBox, Spinner } from "@/components/ui/Misc";
import { cadastrar, entrar, recuperarSenha, traduzirErroAuth } from "@/lib/firebase/auth";
import { useSession } from "@/lib/session";
import type { StorageMode } from "@/lib/store/adapter";

type Aba = "entrar" | "cadastrar" | "recuperar";

export default function LoginPage() {
  const router = useRouter();
  const hidratado = useSession((s) => s.hidratado);
  const firebaseDisponivel = useSession((s) => s.firebaseDisponivel);
  const modoSalvo = useSession((s) => s.modo);
  const user = useSession((s) => s.user);
  const [escolha, setEscolha] = useState<StorageMode | null>(null);
  const [aba, setAba] = useState<Aba>("entrar");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [confirma, setConfirma] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const modo: StorageMode = escolha ?? (firebaseDisponivel ? "firebase" : "local");

  useEffect(() => {
    if (modoSalvo === "firebase" && user) router.replace("/");
  }, [modoSalvo, user, router]);

  if (!hidratado) return <Spinner />;

  function usarLocal() {
    useSession.getState().definirModo("local");
    router.replace("/");
  }

  async function enviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    setInfo(null);
    if (!email.trim()) return setErro("Informe o e-mail.");
    if (aba !== "recuperar" && senha.length < 6) return setErro("A senha deve ter ao menos 6 caracteres.");
    if (aba === "cadastrar" && senha !== confirma) return setErro("As senhas não conferem.");
    setEnviando(true);
    try {
      if (aba === "recuperar") {
        await recuperarSenha(email);
        setInfo("Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha.");
      } else {
        useSession.getState().definirModo("firebase");
        if (aba === "entrar") await entrar(email, senha);
        else await cadastrar(email, senha);
      }
    } catch (err) {
      setErro(traduzirErroAuth(err));
      if (aba !== "recuperar") useSession.getState().definirModo(null);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col justify-center gap-6 px-4 py-10">
      <div className="flex flex-col items-center gap-2 text-center">
        <span className="flex size-12 items-center justify-center rounded-2xl bg-primary text-primary-fg">
          <Wallet size={26} aria-hidden />
        </span>
        <h1 className="text-2xl font-semibold tracking-tight">Meu Dinheiro</h1>
        <p className="text-sm text-muted">Portal web de finanças pessoais</p>
      </div>

      <fieldset className="grid grid-cols-2 gap-2">
        <legend className="mb-2 text-sm font-medium">Onde guardar seus dados?</legend>
        {(
          [
            { v: "firebase", t: "Nuvem", d: "Firebase, sincroniza com o app", icone: Cloud },
            { v: "local", t: "Local", d: "Somente neste navegador", icone: HardDrive },
          ] as const
        ).map((o) => (
          <label
            key={o.v}
            className={`flex cursor-pointer flex-col gap-1 rounded-xl border p-3 text-sm has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--ring)] ${
              modo === o.v ? "border-primary bg-primary-soft" : "border-line bg-surface"
            } ${o.v === "firebase" && !firebaseDisponivel ? "cursor-not-allowed opacity-60" : ""}`}
          >
            <input
              type="radio"
              name="modo"
              className="sr-only"
              checked={modo === o.v}
              disabled={o.v === "firebase" && !firebaseDisponivel}
              onChange={() => setEscolha(o.v)}
            />
            <o.icone size={20} className="text-primary" aria-hidden />
            <span className="font-medium">{o.t}</span>
            <span className="text-xs text-muted">{o.d}</span>
          </label>
        ))}
      </fieldset>

      {!firebaseDisponivel && (
        <div role="note" className="flex gap-2 rounded-xl border border-warn/40 bg-warn-soft px-3 py-2.5 text-sm text-warn">
          <TriangleAlert size={18} className="mt-0.5 shrink-0" aria-hidden />
          <p>
            O Firebase não está configurado (variáveis <code>NEXT_PUBLIC_FIREBASE_*</code> ausentes). Só o modo Local está disponível. Veja o README para configurar a nuvem.
          </p>
        </div>
      )}

      {modo === "local" ? (
        <div className="flex flex-col gap-3 rounded-2xl border border-line bg-surface p-5">
          <p className="text-sm text-muted">
            No modo local os dados ficam apenas no armazenamento deste navegador. Use Configurações para importar ou exportar backups compatíveis com o app.
          </p>
          <Button variante="primary" onClick={usarLocal}>
            Continuar no modo local
          </Button>
        </div>
      ) : (
        <form onSubmit={enviar} className="flex flex-col gap-4 rounded-2xl border border-line bg-surface p-5" noValidate>
          <Segmentado
            rotulo="Ação"
            valor={aba}
            onChange={(v) => {
              setAba(v);
              setErro(null);
              setInfo(null);
            }}
            opcoes={[
              { valor: "entrar", rotulo: "Entrar" },
              { valor: "cadastrar", rotulo: "Criar conta" },
              { valor: "recuperar", rotulo: "Senha" },
            ]}
          />
          <Input rotulo="E-mail" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          {aba !== "recuperar" && (
            <Input
              rotulo="Senha"
              type="password"
              autoComplete={aba === "entrar" ? "current-password" : "new-password"}
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              required
              minLength={6}
            />
          )}
          {aba === "cadastrar" && (
            <Input rotulo="Confirmar senha" type="password" autoComplete="new-password" value={confirma} onChange={(e) => setConfirma(e.target.value)} required />
          )}
          {erro && <ErroBox>{erro}</ErroBox>}
          {info && (
            <p role="status" className="rounded-xl bg-pos-soft px-3 py-2 text-sm text-pos">
              {info}
            </p>
          )}
          <Button type="submit" variante="primary" disabled={enviando}>
            {aba === "entrar" ? "Entrar" : aba === "cadastrar" ? "Criar conta" : "Enviar link de recuperação"}
          </Button>
        </form>
      )}
    </main>
  );
}
