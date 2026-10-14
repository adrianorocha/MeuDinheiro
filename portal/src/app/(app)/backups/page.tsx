"use client";

import { Download, FileArchive, RotateCcw, Trash2, Upload } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Select, Segmentado } from "@/components/ui/Field";
import { Confirmar } from "@/components/ui/Modal";
import { lembrete } from "@/lib/backups/armazenamento";
import { zipDoDataset } from "@/lib/backups/exportar";
import { compararContagens, contagens, precisaLembrete, type RegistroBackup } from "@/lib/backups/historico";
import { historicoStore, trilhaStore } from "@/lib/backups/instancias";
import { filtrarTrilha, ROTULO_ACAO, type AcaoTrilha, type EntradaTrilha } from "@/lib/backups/trilha";
import { useConfigGeral } from "@/lib/configGeral";
import { baixarArquivo, baixarBlob } from "@/lib/download";
import type { ConfigGeral } from "@/lib/finance/configuracoes";
import type { Dataset } from "@/lib/finance/types";
import { formatBRL, paraInputData } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { exportarBackup, importarBackup } from "@/lib/store/backup";
import { useStore } from "@/lib/store/store";

type Aba = "backups" | "alteracoes";

interface Pendente {
  ds: Dataset;
  nome: string;
  versao: number;
  config?: ConfigGeral;
}

const dataHora = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const kb = (n: number) => `${(n / 1024).toFixed(1).replace(".", ",")} KB`;

function gerarJson(ds: Dataset): string {
  return JSON.stringify(exportarBackup(ds, useConfigGeral.getState().config), null, 2);
}

function nomeArquivo(prefixo: string, quando: number): string {
  const d = new Date(quando);
  const p = (n: number) => String(n).padStart(2, "0");
  return `meudinheiro-${prefixo}-${paraInputData(quando)}-${p(d.getHours())}${p(d.getMinutes())}.json`;
}

export default function BackupsPage() {
  const [aba, setAba] = useState<Aba>("backups");
  return (
    <>
      <PageHeader titulo="Backups" descricao="Cópias de segurança, restauração com prévia e histórico de alterações" />
      <div className="mb-4 max-w-md">
        <Segmentado
          rotulo="Seção"
          valor={aba}
          opcoes={[
            { valor: "backups", rotulo: "Backups" },
            { valor: "alteracoes", rotulo: "Histórico de alterações" },
          ]}
          onChange={setAba}
        />
      </div>
      {aba === "backups" ? <AbaBackups /> : <AbaAlteracoes />}
    </>
  );
}

function AbaBackups() {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const arquivoRef = useRef<HTMLInputElement>(null);
  const [historico, setHistorico] = useState<RegistroBackup[]>([]);
  const [pendente, setPendente] = useState<Pendente | null>(null);
  const [excluir, setExcluir] = useState<RegistroBackup | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [avisoAtivo, setAvisoAtivo] = useState(() => lembrete.ativo());
  const [ultimo, setUltimo] = useState<number | null>(() => lembrete.ultimoBackup());
  const [agora] = useState(() => Date.now());

  const recarregar = useCallback(() => {
    void historicoStore.listar().then(setHistorico);
  }, []);

  useEffect(() => {
    recarregar();
  }, [recarregar]);

  const atual = useMemo(() => contagens(ds), [ds]);
  const comparacao = useMemo(() => (pendente ? compararContagens(atual, contagens(pendente.ds)) : []), [pendente, atual]);

  async function guardar(tipo: RegistroBackup["tipo"], baixar: boolean): Promise<boolean> {
    const quando = Date.now();
    const json = gerarJson(ds);
    if (baixar) baixarArquivo(nomeArquivo(tipo === "SEGURANCA" ? "seguranca" : "backup", quando), json, "application/json");
    const ok = await historicoStore.adicionar({
      id: `${quando}-${Math.random().toString(36).slice(2, 8)}`,
      criadoEm: quando,
      tipo,
      contagens: contagens(ds),
      tamanho: new Blob([json]).size,
      json,
    });
    if (tipo === "MANUAL") {
      lembrete.marcarBackup(quando);
      setUltimo(quando);
    }
    recarregar();
    return ok;
  }

  async function backupAgora() {
    const ok = await guardar("MANUAL", true);
    avisar(ok ? "sucesso" : "info", ok ? "Backup baixado e guardado no histórico deste navegador." : "Backup baixado (não foi possível guardar no histórico do navegador).");
  }

  async function lerArquivo(arquivo: File | undefined) {
    setErro(null);
    if (!arquivo) return;
    const r = importarBackup(await arquivo.text());
    if (!r.ok) return setErro(r.erro);
    setPendente({ ds: r.dataset, nome: arquivo.name, versao: r.versao, config: r.configuracoes });
  }

  function restaurarDoHistorico(reg: RegistroBackup) {
    setErro(null);
    const r = importarBackup(reg.json);
    if (!r.ok) return setErro(r.erro);
    setPendente({ ds: r.dataset, nome: `backup de ${dataHora.format(reg.criadoEm)}`, versao: r.versao, config: r.configuracoes });
  }

  async function confirmarRestauracao() {
    if (!pendente) return;
    const alvo = pendente;
    setPendente(null);
    // Antes de substituir: backup de segurança do estado atual (baixado e guardado no histórico).
    await guardar("SEGURANCA", true);
    await useStore.getState().substituirTudo(alvo.ds);
    if (alvo.config) useConfigGeral.getState().importar(alvo.config);
    void trilhaStore.registrar([{ quando: Date.now(), acao: "RESTAURAR_BACKUP", id: null, descricao: `Backup restaurado: ${alvo.nome}`, valor: null, alteracoes: [] }]);
    avisar("sucesso", "Backup restaurado. Uma cópia de segurança do estado anterior foi baixada.");
    if (arquivoRef.current) arquivoRef.current.value = "";
  }

  function exportarZip() {
    const bytes = zipDoDataset(ds);
    baixarBlob(`meudinheiro-tudo-${paraInputData(Date.now())}.zip`, new Blob([new Uint8Array(bytes)], { type: "application/zip" }));
  }

  const mostrarLembrete = avisoAtivo && precisaLembrete(ultimo, agora);

  return (
    <div className="grid gap-4">
      {mostrarLembrete && (
        <div role="status" className="rounded-2xl border border-line bg-warn-soft p-4 text-sm text-warn">
          {ultimo === null ? "Você ainda não fez backup neste navegador." : `Seu último backup foi em ${dataHora.format(ultimo)} (há mais de 7 dias).`} Faça um agora.
        </div>
      )}

      <Card aria-labelledby="t-agora">
        <CardTitulo id="t-agora">Fazer backup</CardTitulo>
        <p className="mb-3 text-sm text-muted">
          Gera o arquivo JSON (formato v2, o mesmo do app) e guarda uma cópia no histórico deste navegador (últimos 7). O navegador não agenda tarefas em segundo plano:
          o backup só acontece quando você pede aqui. Para backups automáticos diários, use o app Android.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button variante="primary" icone={<Download size={16} aria-hidden />} onClick={() => void backupAgora()}>
            Baixar backup agora
          </Button>
          <Button icone={<FileArchive size={16} aria-hidden />} onClick={exportarZip}>
            Exportar tudo (ZIP de CSVs)
          </Button>
        </div>
        <div className="mt-3">
          <Checkbox
            rotulo="Lembrar de fazer backup se passarem mais de 7 dias"
            checked={avisoAtivo}
            onChange={(e) => {
              lembrete.definirAtivo(e.target.checked);
              setAvisoAtivo(e.target.checked);
            }}
          />
        </div>
      </Card>

      <Card aria-labelledby="t-restaurar">
        <CardTitulo id="t-restaurar">Restaurar de um arquivo</CardTitulo>
        <p className="mb-3 text-sm text-muted">Você verá uma comparação das quantidades (atual × arquivo) antes de confirmar. Um backup de segurança do estado atual é baixado automaticamente.</p>
        <Button icone={<Upload size={16} aria-hidden />} onClick={() => arquivoRef.current?.click()}>
          Escolher arquivo de backup
        </Button>
        <input ref={arquivoRef} type="file" accept=".json,application/json" className="sr-only" aria-label="Selecionar arquivo de backup" onChange={(e) => void lerArquivo(e.target.files?.[0])} />
        {erro && (
          <div className="mt-3">
            <ErroBox>{erro}</ErroBox>
          </div>
        )}
      </Card>

      <Card aria-labelledby="t-hist">
        <CardTitulo id="t-hist">Histórico neste navegador (últimos 7)</CardTitulo>
        {historico.length === 0 ? (
          <EmptyState titulo="Nenhum backup guardado ainda" descricao="Os backups gerados nesta página aparecem aqui." />
        ) : (
          <ul className="divide-y divide-line">
            {historico.map((h) => (
              <li key={h.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium">
                    {dataHora.format(h.criadoEm)} <Badge tom={h.tipo === "SEGURANCA" ? "warn" : "primary"}>{h.tipo === "SEGURANCA" ? "Segurança" : "Manual"}</Badge>
                  </p>
                  <p className="text-xs text-muted">
                    {kb(h.tamanho)} · {h.contagens.lancamentos ?? 0} lançamentos · {h.contagens.contas ?? 0} contas · {h.contagens.cartoes ?? 0} cartões · {h.contagens.categorias ?? 0} categorias
                  </p>
                </div>
                <div className="flex gap-1">
                  <Button tamanho="sm" icone={<Download size={14} aria-hidden />} onClick={() => baixarArquivo(nomeArquivo("backup", h.criadoEm), h.json, "application/json")}>
                    Baixar
                  </Button>
                  <Button tamanho="sm" icone={<RotateCcw size={14} aria-hidden />} onClick={() => restaurarDoHistorico(h)}>
                    Restaurar
                  </Button>
                  <Button tamanho="sm" variante="ghost" icone={<Trash2 size={14} aria-hidden />} onClick={() => setExcluir(h)} aria-label={`Excluir backup de ${dataHora.format(h.criadoEm)}`}>
                    Excluir
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Confirmar
        aberto={pendente !== null}
        titulo="Restaurar backup"
        rotuloConfirmar="Substituir meus dados"
        perigo
        mensagem={pendente ? `${pendente.nome} (formato v${pendente.versao}). Todos os dados atuais serão substituídos. Antes, um backup de segurança do estado atual será baixado.` : ""}
        onConfirmar={() => void confirmarRestauracao()}
        onCancelar={() => setPendente(null)}
      >
        <table className="mt-3 w-full text-sm">
          <caption className="sr-only">Comparação de quantidades por coleção</caption>
          <thead>
            <tr className="text-left text-xs text-muted">
              <th scope="col" className="py-1">Coleção</th>
              <th scope="col" className="py-1 text-right">Atual</th>
              <th scope="col" className="py-1 text-right">Arquivo</th>
              <th scope="col" className="py-1 text-right">Diferença</th>
            </tr>
          </thead>
          <tbody>
            {comparacao.map((l) => (
              <tr key={l.chave} className="border-t border-line">
                <td className="py-1">{l.rotulo}</td>
                <td className="py-1 text-right tabular-nums">{l.atual}</td>
                <td className="py-1 text-right tabular-nums">{l.arquivo}</td>
                <td className={`py-1 text-right tabular-nums ${l.diferenca < 0 ? "text-neg" : l.diferenca > 0 ? "text-pos" : "text-muted"}`}>
                  {l.diferenca > 0 ? `+${l.diferenca}` : l.diferenca}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Confirmar>
      <Confirmar
        aberto={excluir !== null}
        titulo="Excluir backup do histórico"
        rotuloConfirmar="Excluir"
        perigo
        mensagem={excluir ? `O backup de ${dataHora.format(excluir.criadoEm)} será removido deste navegador. Não dá para desfazer.` : ""}
        onConfirmar={() => {
          const alvo = excluir;
          setExcluir(null);
          if (alvo) void historicoStore.excluir(alvo.id).then(recarregar);
        }}
        onCancelar={() => setExcluir(null)}
      />
    </div>
  );
}

const PERIODOS = [
  { valor: "", rotulo: "Todo o período" },
  { valor: "7", rotulo: "Últimos 7 dias" },
  { valor: "30", rotulo: "Últimos 30 dias" },
  { valor: "90", rotulo: "Últimos 90 dias" },
];

function AbaAlteracoes() {
  const [todas, setTodas] = useState<EntradaTrilha[]>([]);
  const [busca, setBusca] = useState("");
  const [acao, setAcao] = useState("");
  const [dias, setDias] = useState("");
  const [agora] = useState(() => Date.now());

  useEffect(() => {
    void trilhaStore.ler().then(setTodas);
  }, []);

  const lista = useMemo(
    () => filtrarTrilha(todas, { de: dias ? agora - Number(dias) * 86_400_000 : null, acao: (acao || null) as AcaoTrilha | null, busca }),
    [todas, busca, acao, dias, agora],
  );

  return (
    <div className="grid gap-4">
      <Card aria-labelledby="t-filtros">
        <CardTitulo id="t-filtros">Filtros</CardTitulo>
        <p className="mb-3 text-sm text-muted">Registro local (somente neste navegador) das últimas 2.000 alterações de lançamentos: criar, editar, excluir, restaurar da lixeira e pagar.</p>
        <div className="grid gap-3 sm:grid-cols-3">
          <Input rotulo="Buscar" value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Descrição, campo ou valor" />
          <Select rotulo="Ação" value={acao} onChange={(e) => setAcao(e.target.value)}>
            <option value="">Todas</option>
            {(Object.keys(ROTULO_ACAO) as AcaoTrilha[]).map((a) => (
              <option key={a} value={a}>
                {ROTULO_ACAO[a]}
              </option>
            ))}
          </Select>
          <Select rotulo="Período" value={dias} onChange={(e) => setDias(e.target.value)}>
            {PERIODOS.map((p) => (
              <option key={p.valor} value={p.valor}>
                {p.rotulo}
              </option>
            ))}
          </Select>
        </div>
      </Card>
      {lista.length === 0 ? (
        <EmptyState titulo="Nada registrado" descricao="As alterações feitas a partir de agora aparecem aqui." />
      ) : (
        <ul className="grid gap-2">
          {lista.map((e, i) => (
            <li key={`${e.quando}-${e.id ?? "x"}-${i}`} className="rounded-2xl border border-line bg-surface p-3 text-sm">
              <p className="flex flex-wrap items-center gap-2">
                <Badge tom={e.acao === "EXCLUIR" ? "neg" : e.acao === "CRIAR" || e.acao === "PAGAR" ? "pos" : "primary"}>{ROTULO_ACAO[e.acao]}</Badge>
                <span className="text-xs text-muted">{dataHora.format(e.quando)}</span>
              </p>
              <p className="mt-1 font-medium">
                {e.descricao}
                {e.valor !== null ? ` · ${formatBRL(e.valor)}` : ""}
              </p>
              {e.alteracoes.map((m) => (
                <p key={m.campo} className="text-xs text-muted">
                  {m.campo}: {m.antigo || "—"} → {m.novo || "—"}
                </p>
              ))}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
