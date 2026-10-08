"use client";

import { Download, FlaskConical, LogOut, Trash2, Upload } from "lucide-react";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Compartilhar } from "@/components/config/Compartilhar";
import { PinConfig } from "@/components/config/PinConfig";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Segmentado } from "@/components/ui/Field";
import { Card, CardTitulo, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar } from "@/components/ui/Modal";
import { criarDatasetDemo } from "@/lib/demo";
import { baixarArquivo } from "@/lib/download";
import { sair } from "@/lib/firebase/auth";
import { COLLECTIONS } from "@/lib/finance/types";
import type { Dataset } from "@/lib/finance/types";
import { paraInputData } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { useSession, type TemaPref } from "@/lib/session";
import { exportarBackup, importarBackup } from "@/lib/store/backup";
import { useStore } from "@/lib/store/store";

const ROTULO_COLECAO: Record<string, string> = {
  contas: "contas",
  despesas: "lançamentos",
  despesasFixas: "recorrências",
  categorias: "categorias",
  orcamentos: "orçamentos",
  metas: "metas",
  investimentos: "investimentos",
  cartoes: "cartões",
  transferenciasAgendadas: "transferências agendadas",
  patrimonio: "registros de patrimônio",
  transacoes: "transações (legado)",
};

function resumo(ds: Dataset): string {
  return COLLECTIONS.filter((c) => ds[c].length > 0)
    .map((c) => `${ds[c].length} ${ROTULO_COLECAO[c]}`)
    .join(", ") || "nenhum dado";
}

export default function ConfiguracoesPage() {
  const router = useRouter();
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const modo = useSession((s) => s.modo);
  const user = useSession((s) => s.user);
  const tema = useSession((s) => s.tema);
  const privado = useSession((s) => s.privado);
  const arquivoRef = useRef<HTMLInputElement>(null);
  const [importando, setImportando] = useState<{ ds: Dataset; versao: number; nome: string } | null>(null);
  const [erroImport, setErroImport] = useState<string | null>(null);
  const [demo, setDemo] = useState(false);
  const [apagar, setApagar] = useState(false);
  const [confirmacao, setConfirmacao] = useState("");

  async function lerArquivo(arquivo: File | undefined) {
    setErroImport(null);
    if (!arquivo) return;
    const r = importarBackup(await arquivo.text());
    if (!r.ok) return setErroImport(r.erro);
    setImportando({ ds: r.dataset, versao: r.versao, nome: arquivo.name });
  }

  async function confirmarImport() {
    if (!importando) return;
    await useStore.getState().substituirTudo(importando.ds);
    avisar("sucesso", "Backup importado.");
    setImportando(null);
    if (arquivoRef.current) arquivoRef.current.value = "";
  }

  function exportar() {
    baixarArquivo(`meudinheiro-backup-${paraInputData(Date.now())}.json`, JSON.stringify(exportarBackup(ds), null, 2), "application/json");
  }

  async function trocarModo() {
    if (modo === "firebase") await sair();
    useSession.getState().definirModo(null);
    router.replace("/login/");
  }

  function carregarDemo() {
    void useStore
      .getState()
      .substituirTudo(criarDatasetDemo())
      .then(() => avisar("sucesso", "Dados de exemplo carregados."));
  }

  const temDados = COLLECTIONS.some((c) => ds[c].length > 0);

  return (
    <>
      <PageHeader titulo="Configurações" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card aria-labelledby="t-modo">
          <CardTitulo id="t-modo">Armazenamento</CardTitulo>
          <p className="text-sm">
            {modo === "firebase" ? (
              <>
                <strong>Nuvem (Firebase)</strong>
                {user?.email ? ` · ${user.email}` : ""}. Os dados sincronizam com o app Android.
              </>
            ) : (
              <>
                <strong>Local</strong>. Os dados ficam somente neste navegador; use backup para levar ao app.
              </>
            )}
          </p>
          <div className="mt-3">
            <Button icone={<LogOut size={16} aria-hidden />} onClick={trocarModo}>
              {modo === "firebase" ? "Sair / trocar modo" : "Trocar modo de armazenamento"}
            </Button>
          </div>
        </Card>

        <Card aria-labelledby="t-aparencia">
          <CardTitulo id="t-aparencia">Aparência e privacidade</CardTitulo>
          <Segmentado<TemaPref>
            rotulo="Tema"
            valor={tema}
            onChange={(t) => useSession.getState().definirTema(t)}
            opcoes={[
              { valor: "system", rotulo: "Sistema" },
              { valor: "light", rotulo: "Claro" },
              { valor: "dark", rotulo: "Escuro" },
            ]}
          />
          <div className="mt-4">
            <Checkbox rotulo="Modo privado (ocultar valores)" checked={privado} onChange={() => useSession.getState().alternarPrivado()} />
          </div>
        </Card>

        <Card aria-labelledby="t-backup">
          <CardTitulo id="t-backup">Backup (JSON v2, compatível com o app)</CardTitulo>
          <p className="text-sm text-muted">Exporte todos os dados ou importe um backup do app (v2) ou legado (v1). Ids são preservados; a importação substitui os dados atuais.</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button icone={<Download size={16} aria-hidden />} onClick={exportar}>
              Exportar backup
            </Button>
            <Button icone={<Upload size={16} aria-hidden />} onClick={() => arquivoRef.current?.click()}>
              Importar backup
            </Button>
            <input
              ref={arquivoRef}
              type="file"
              accept="application/json,.json"
              className="sr-only"
              aria-label="Selecionar arquivo de backup"
              onChange={(e) => void lerArquivo(e.target.files?.[0])}
            />
          </div>
          {erroImport && (
            <div className="mt-3">
              <ErroBox>{erroImport}</ErroBox>
            </div>
          )}
        </Card>

        <Card aria-labelledby="t-demo">
          <CardTitulo id="t-demo">Dados de exemplo</CardTitulo>
          <p className="text-sm text-muted">Carrega um conjunto de demonstração (contas, cartões, lançamentos, metas...) para explorar o portal. Disponível apenas no modo Local.</p>
          <div className="mt-3">
            <Button icone={<FlaskConical size={16} aria-hidden />} disabled={modo !== "local"} onClick={() => (temDados ? setDemo(true) : carregarDemo())}>
              Carregar dados de exemplo
            </Button>
          </div>
        </Card>

        <PinConfig />
        {modo === "firebase" && <Compartilhar />}

        <Card className="border-neg/40 lg:col-span-2" aria-labelledby="t-perigo">
          <CardTitulo id="t-perigo">Zona de perigo</CardTitulo>
          <p className="text-sm text-muted">Apaga todas as contas, lançamentos e demais dados {modo === "firebase" ? "da nuvem" : "deste navegador"}. Não pode ser desfeito.</p>
          <div className="mt-3">
            <Button variante="danger" icone={<Trash2 size={16} aria-hidden />} onClick={() => { setConfirmacao(""); setApagar(true); }}>
              Apagar todos os dados
            </Button>
          </div>
        </Card>
      </div>

      <Confirmar
        aberto={importando !== null}
        titulo="Importar backup"
        rotuloConfirmar="Substituir meus dados"
        perigo
        mensagem={importando ? `Arquivo "${importando.nome}" (formato v${importando.versao}): ${resumo(importando.ds)}. Todos os dados atuais serão substituídos.` : ""}
        onConfirmar={() => void confirmarImport()}
        onCancelar={() => setImportando(null)}
      />
      <Confirmar
        aberto={demo}
        titulo="Carregar dados de exemplo"
        rotuloConfirmar="Substituir por exemplo"
        perigo
        mensagem="Os dados atuais deste navegador serão substituídos pelos dados de exemplo."
        onConfirmar={() => {
          setDemo(false);
          carregarDemo();
        }}
        onCancelar={() => setDemo(false)}
      />
      <Confirmar
        aberto={apagar}
        titulo="Apagar todos os dados"
        rotuloConfirmar="Apagar definitivamente"
        perigo
        mensagem="Esta ação é permanente. Para confirmar, digite APAGAR abaixo."
        onConfirmar={() => {
          if (confirmacao !== "APAGAR") return;
          setApagar(false);
          void useStore.getState().limparTudo().then(() => avisar("sucesso", "Todos os dados foram apagados."));
        }}
        onCancelar={() => setApagar(false)}
      >
        <div className="mt-3">
          <Input rotulo="Digite APAGAR para confirmar" value={confirmacao} onChange={(e) => setConfirmacao(e.target.value)} autoComplete="off" />
          {confirmacao !== "APAGAR" && confirmacao.length > 0 && <p className="mt-1 text-xs text-muted">O botão só funciona quando o texto é exatamente APAGAR.</p>}
        </div>
      </Confirmar>
    </>
  );
}
