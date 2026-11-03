"use client";

import { Copy, KeyRound, LogOut, Share2, Trash2, UserPlus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { Confirmar } from "@/components/ui/Modal";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { useCompartilhamento, usePapel } from "@/lib/compartilhamento";
import {
  aceitarConvite,
  alterarPapelMembro,
  consultarConvite,
  convidar,
  convitesPendentes,
  gerarConvite,
  listarConvites,
  listarMembros,
  removerMembro,
  revogarConvite,
  sairDaConta,
  type Convite,
  type ConviteCodigo,
  type Membro,
} from "@/lib/firebase/compartilhar";
import { DESCRICAO_PAPEL, papeisConcediveis, podeExecutar, podeGerenciarMembro, ROTULO_PAPEL, type PapelMembro } from "@/lib/finance/permissoes";
import { formatData } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useStore } from "@/lib/store/store";

/** R67 - membros, papéis e convites por código da conta compartilhada. Só a UI: as regras do Firestore decidem. */
export default function MembrosPage() {
  const modo = useSession((s) => s.modo);
  const user = useSession((s) => s.user);
  const raizUid = useSession((s) => s.raizUid);
  const papel = usePapel();
  const avisar = useStore((s) => s.avisar);

  const meuUid = user?.uid ?? "";
  const meuEmail = user?.email ?? null;
  const donoAtual = raizUid ?? meuUid;
  const concediveis = papeisConcediveis(papel);
  const podeConvidar = podeExecutar(papel, "CONVITE_GERAR");

  const [membros, setMembros] = useState<Membro[]>([]);
  const [recebidos, setRecebidos] = useState<Convite[]>([]);
  const [pendentes, setPendentes] = useState<ConviteCodigo[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);

  const [papelConvite, setPapelConvite] = useState<PapelMembro>("EDITOR");
  const [novoConvite, setNovoConvite] = useState<ConviteCodigo | null>(null);
  const [email, setEmail] = useState("");
  const [codigo, setCodigo] = useState("");
  const [consultado, setConsultado] = useState<ConviteCodigo | null>(null);
  const [removendo, setRemovendo] = useState<Membro | null>(null);
  const [saindo, setSaindo] = useState(false);
  const [ocupado, setOcupado] = useState(false);

  const carregar = useCallback(async () => {
    if (!meuUid) return;
    setCarregando(true);
    try {
      const [m, r] = await Promise.all([listarMembros(donoAtual), meuEmail ? listarConvites(meuEmail) : Promise.resolve([] as Convite[])]);
      setMembros(m);
      setRecebidos(r);
      setErro(null);
    } catch {
      setErro("Não foi possível carregar os membros. Verifique se as regras do Firestore (firestore.rules) foram publicadas.");
    } finally {
      setCarregando(false);
    }
    // A lista de convites pendentes depende da regra de `list` (dono/ADMIN); falha aqui não impede o resto.
    setPendentes(podeConvidar ? await convitesPendentes(donoAtual).catch(() => []) : []);
  }, [meuUid, donoAtual, meuEmail, podeConvidar]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- carga inicial assíncrona
    void carregar();
  }, [carregar]);

  if (modo !== "firebase" || !user) {
    return (
      <>
        <PageHeader titulo="Membros" descricao="Conta compartilhada" />
        <EmptyState titulo="Disponível no modo nuvem" descricao="Entre com sua conta para compartilhar seus dados com outra pessoa (casal/família)." />
      </>
    );
  }

  async function rodar(ok: string | null, fn: () => Promise<void>) {
    setOcupado(true);
    setErro(null);
    try {
      await fn();
      if (ok) avisar("sucesso", ok);
      await useCompartilhamento.getState().carregar();
      await carregar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível concluir.");
    } finally {
      setOcupado(false);
    }
  }

  function gerar() {
    void rodar(null, async () => {
      const exibicao = { donoEmail: user?.email ?? undefined, donoNome: (user?.email ?? "").split("@")[0] };
      setNovoConvite(await gerarConvite(donoAtual, papelConvite, meuUid, papel, exibicao));
    });
  }

  async function copiar(c: ConviteCodigo) {
    try {
      await navigator.clipboard.writeText(c.codigo);
      avisar("sucesso", "Código copiado.");
    } catch {
      avisar("erro", "Não foi possível copiar; selecione o código e copie manualmente.");
    }
  }

  async function compartilhar(c: ConviteCodigo) {
    const texto = `Entre na minha conta no Meu Dinheiro com o código ${c.codigo} (vale 7 dias).`;
    try {
      if (navigator.share) await navigator.share({ text: texto });
      else await navigator.clipboard.writeText(texto);
      if (!navigator.share) avisar("sucesso", "Mensagem copiada.");
    } catch {
      /* compartilhamento cancelado */
    }
  }

  function verificar(e: FormEvent) {
    e.preventDefault();
    setConsultado(null);
    void rodar(null, async () => {
      setConsultado(await consultarConvite(codigo, meuUid));
    });
  }

  function entrar() {
    if (!consultado) return;
    const c = consultado;
    void rodar(`Você entrou na conta de ${c.donoNome || c.donoEmail || "outra pessoa"}.`, async () => {
      await aceitarConvite(c, meuUid, user?.email ?? null, (user?.email ?? "").split("@")[0]);
      setConsultado(null);
      setCodigo("");
      useSession.getState().definirRaiz(c.donoUid);
    });
  }

  function convidarEmail(e: FormEvent) {
    e.preventDefault();
    void rodar("Convite enviado. A pessoa já pode usar a conta.", async () => {
      await convidar(donoAtual, user?.email ?? "", email, papelConvite, papel);
      setEmail("");
    });
  }

  const donoDaConta = recebidos.find((c) => c.donoUid === raizUid);

  return (
    <>
      <PageHeader titulo="Membros" descricao="Quem tem acesso a esta conta e com qual papel" />
      {erro && (
        <div className="mb-4">
          <ErroBox>{erro}</ErroBox>
        </div>
      )}

      <Card className="mb-4" aria-labelledby="t-conta">
        <CardTitulo id="t-conta">Conta em uso</CardTitulo>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge tom={raizUid ? "warn" : "pos"}>{raizUid ? `Conta de ${donoDaConta?.donoEmail ?? "outra pessoa"}` : "Meus dados"}</Badge>
          <Badge>Seu papel: {ROTULO_PAPEL[papel]}</Badge>
          {raizUid && (
            <>
              <Button tamanho="sm" onClick={() => useSession.getState().definirRaiz(null)}>
                Usar meus dados
              </Button>
              <Button tamanho="sm" variante="danger" icone={<LogOut size={14} aria-hidden />} onClick={() => setSaindo(true)}>
                Sair da conta compartilhada
              </Button>
            </>
          )}
        </div>
        {papel === "LEITOR" && <p className="mt-2 text-xs text-muted">Como leitor você só consulta: criar e editar ficam desativados.</p>}
      </Card>

      <Card className="mb-4" aria-labelledby="t-codigo">
        <CardTitulo id="t-codigo">Entrar com um código</CardTitulo>
        <p className="mb-3 text-sm text-muted">Recebeu um código de convite (8 caracteres)? Digite abaixo para ver de quem é a conta antes de confirmar.</p>
        <form onSubmit={verificar} className="flex flex-wrap items-end gap-2" noValidate>
          <div className="min-w-48 flex-1">
            <Input rotulo="Código de convite" value={codigo} onChange={(e) => { setCodigo(e.target.value); setConsultado(null); }} autoComplete="off" autoCapitalize="characters" maxLength={12} />
          </div>
          <Button type="submit" icone={<KeyRound size={16} aria-hidden />} disabled={ocupado || !codigo.trim()}>
            Verificar código
          </Button>
        </form>
        {consultado && (
          <div className="mt-3 rounded-lg border border-line p-3 text-sm">
            <p>
              Este convite dá acesso à conta de <strong>{consultado.donoNome || consultado.donoEmail || "outra pessoa"}</strong> como{" "}
              <strong>{ROTULO_PAPEL[consultado.papel]}</strong>. Vale até {formatData(consultado.expiraEm)}.
            </p>
            <p className="mt-1 text-xs text-muted">{DESCRICAO_PAPEL[consultado.papel]}</p>
            <div className="mt-3 flex gap-2">
              <Button variante="primary" onClick={entrar} disabled={ocupado}>
                Confirmar entrada
              </Button>
              <Button onClick={() => setConsultado(null)}>Cancelar</Button>
            </div>
          </div>
        )}
      </Card>

      {podeConvidar && (
        <Card className="mb-4" aria-labelledby="t-convidar">
          <CardTitulo id="t-convidar">Convidar</CardTitulo>
          <p className="mb-3 text-sm text-muted">O código vale por 7 dias e só pode ser usado uma vez. A pessoa entra com o papel escolhido.</p>
          <div className="flex flex-wrap items-end gap-2">
            <div className="min-w-48">
              <Select rotulo="Papel" value={papelConvite} onChange={(e) => setPapelConvite(e.target.value as PapelMembro)} dica={DESCRICAO_PAPEL[papelConvite]}>
                {concediveis.map((p) => (
                  <option key={p} value={p}>
                    {ROTULO_PAPEL[p]}
                  </option>
                ))}
              </Select>
            </div>
            <Button variante="primary" icone={<KeyRound size={16} aria-hidden />} onClick={gerar} disabled={ocupado || !concediveis.includes(papelConvite)}>
              Gerar código
            </Button>
          </div>
          {novoConvite && (
            <div className="mt-3 rounded-lg border border-line p-3">
              <p className="font-mono text-2xl font-bold tracking-widest" aria-label={`Código ${novoConvite.codigo.split("").join(" ")}`}>
                {novoConvite.codigo}
              </p>
              <p className="text-xs text-muted">
                {ROTULO_PAPEL[novoConvite.papel]} · válido até {formatData(novoConvite.expiraEm)}
              </p>
              <div className="mt-2 flex gap-2">
                <Button tamanho="sm" icone={<Copy size={14} aria-hidden />} onClick={() => void copiar(novoConvite)}>
                  Copiar
                </Button>
                <Button tamanho="sm" icone={<Share2 size={14} aria-hidden />} onClick={() => void compartilhar(novoConvite)}>
                  Compartilhar
                </Button>
              </div>
            </div>
          )}

          {pendentes.length > 0 && (
            <>
              <h3 className="mt-5 text-sm font-semibold">Convites pendentes</h3>
              <ul className="divide-y divide-line">
                {pendentes.map((c) => (
                  <li key={c.codigo} className="flex items-center gap-2 py-2 text-sm">
                    <span className="min-w-0 flex-1 truncate font-mono">
                      {c.codigo} · {ROTULO_PAPEL[c.papel]} · até {formatData(c.expiraEm)}
                    </span>
                    <Button tamanho="sm" variante="danger" disabled={ocupado} onClick={() => void rodar("Convite revogado.", () => revogarConvite(c.codigo))}>
                      Revogar
                    </Button>
                  </li>
                ))}
              </ul>
            </>
          )}

          <h3 className="mt-5 text-sm font-semibold">Convidar por e-mail</h3>
          <p className="mb-2 text-xs text-muted">A pessoa precisa já ter entrado no Meu Dinheiro com esse e-mail. Ela terá o papel escolhido acima.</p>
          <form onSubmit={convidarEmail} className="flex flex-wrap items-end gap-2" noValidate>
            <div className="min-w-56 flex-1">
              <Input rotulo="E-mail do convidado" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
            </div>
            <Button type="submit" icone={<UserPlus size={16} aria-hidden />} disabled={ocupado || !email.trim() || !concediveis.includes(papelConvite)}>
              Convidar
            </Button>
          </form>
        </Card>
      )}

      <Card className="mb-4" aria-labelledby="t-membros">
        <CardTitulo id="t-membros">Pessoas com acesso</CardTitulo>
        {carregando ? (
          <p className="py-2 text-sm text-muted">Carregando...</p>
        ) : membros.length === 0 ? (
          <p className="py-2 text-sm text-muted">Ninguém além do dono tem acesso a esta conta.</p>
        ) : (
          <ul className="divide-y divide-line">
            {membros.map((m) => {
              const titulo = m.nome || m.email || m.uid;
              const gerencia = podeGerenciarMembro(papel, m.papel);
              return (
                <li key={m.uid} className="flex flex-wrap items-center gap-2 py-2 text-sm">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">
                      {titulo}
                      {m.uid === meuUid && " (você)"}
                    </p>
                    <p className="truncate text-xs text-muted">
                      {ROTULO_PAPEL[m.papel]}
                      {m.entrouEm > 0 && ` · desde ${formatData(m.entrouEm)}`}
                      {m.nome && m.email && ` · ${m.email}`}
                    </p>
                  </div>
                  {gerencia ? (
                    <>
                      <Select
                        rotulo={`Papel de ${titulo}`}
                        wrapperClassName="w-40"
                        value={m.papel}
                        disabled={ocupado}
                        onChange={(e) => void rodar(`${titulo} agora é ${ROTULO_PAPEL[e.target.value as PapelMembro]}.`, () => alterarPapelMembro(donoAtual, m, e.target.value as PapelMembro, papel))}
                      >
                        {[...new Set<PapelMembro>([m.papel, ...concediveis])].map((p) => (
                          <option key={p} value={p} disabled={!concediveis.includes(p)}>
                            {ROTULO_PAPEL[p]}
                          </option>
                        ))}
                      </Select>
                      <IconButton rotulo={`Remover ${titulo}`} onClick={() => setRemovendo(m)}>
                        <Trash2 size={16} aria-hidden />
                      </IconButton>
                    </>
                  ) : (
                    <Badge>{ROTULO_PAPEL[m.papel]}</Badge>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      <Card aria-labelledby="t-recebidos">
        <CardTitulo id="t-recebidos">Convites por e-mail recebidos</CardTitulo>
        {recebidos.length === 0 ? (
          <p className="py-2 text-sm text-muted">Nenhum convite recebido.</p>
        ) : (
          <ul className="divide-y divide-line">
            {recebidos.map((c) => (
              <li key={c.donoUid} className="flex items-center gap-2 py-2 text-sm">
                <span className="min-w-0 flex-1 truncate">{c.donoEmail}</span>
                <Button tamanho="sm" disabled={raizUid === c.donoUid} onClick={() => useSession.getState().definirRaiz(c.donoUid)}>
                  Usar dados de {c.donoEmail.split("@")[0]}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Confirmar
        aberto={removendo !== null}
        titulo="Remover membro"
        mensagem={`Remover ${removendo?.nome || removendo?.email || "este membro"} da conta? A pessoa perde o acesso imediatamente.`}
        rotuloConfirmar="Remover"
        perigo
        onCancelar={() => setRemovendo(null)}
        onConfirmar={() => {
          const m = removendo;
          setRemovendo(null);
          if (m) void rodar("Acesso removido.", () => removerMembro(donoAtual, m, papel));
        }}
      />
      <Confirmar
        aberto={saindo}
        titulo="Sair da conta compartilhada"
        mensagem="Você perde o acesso aos dados desta conta e precisará de um novo convite para voltar. Seus próprios dados não são afetados."
        rotuloConfirmar="Sair da conta"
        perigo
        onCancelar={() => setSaindo(false)}
        onConfirmar={() => {
          setSaindo(false);
          void rodar("Você saiu da conta compartilhada.", async () => {
            await sairDaConta(donoAtual, meuUid);
            useSession.getState().definirRaiz(null);
          });
        }}
      />
    </>
  );
}
