"use client";

import { FileUp } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Select } from "@/components/ui/Field";
import { Card, CardTitulo, ErroBox } from "@/components/ui/Misc";
import { analisarCsv, parseCsv, type ConfigCsv, type Delimitador } from "@/lib/conciliacao/csv";
import { lembrarMapeamento, recuperarMapeamento } from "@/lib/conciliacao/mapeamento";
import { parseOfx } from "@/lib/conciliacao/ofx";
import { decodificarTexto } from "@/lib/conciliacao/texto-banco";
import type { ArquivoExtrato } from "@/lib/conciliacao/tipos";
import { pareceOfx } from "@/lib/conciliacao/ofx";

export interface ArquivoLido {
  nome: string;
  texto: string;
}

export const MAX_BYTES = 25 * 1024 * 1024;

export async function lerArquivo(file: File): Promise<ArquivoLido> {
  if (file.size > MAX_BYTES) throw new Error(`${file.name}: arquivo grande demais (máx. 25 MB).`);
  return { nome: file.name, texto: decodificarTexto(new Uint8Array(await file.arrayBuffer())) };
}

/** Área de arrastar/soltar ou escolher arquivos (vários de uma vez). */
export function ZonaDrop({ onArquivos, compacto = false }: { onArquivos: (files: File[]) => void; compacto?: boolean }) {
  const [arrastando, setArrastando] = useState(false);
  return (
    <label
      onDragOver={(e) => {
        e.preventDefault();
        setArrastando(true);
      }}
      onDragLeave={() => setArrastando(false)}
      onDrop={(e) => {
        e.preventDefault();
        setArrastando(false);
        if (e.dataTransfer.files.length) onArquivos([...e.dataTransfer.files]);
      }}
      className={`flex cursor-pointer flex-col items-center gap-2 rounded-2xl border-2 border-dashed text-center transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--ring)] ${compacto ? "px-4 py-4" : "px-6 py-10"} ${arrastando ? "border-primary bg-primary-soft" : "border-line hover:bg-surface-2"}`}
    >
      <FileUp size={compacto ? 22 : 32} className="text-primary" aria-hidden />
      <span className="font-medium">{compacto ? "Adicionar mais arquivos" : "Arraste os arquivos aqui ou clique para escolher"}</span>
      {!compacto && <span className="text-sm text-muted">OFX (v1 ou v2) ou CSV do seu banco. Envie vários de uma vez: eles entram numa fila e podem ser conciliados em lote.</span>}
      <input
        type="file"
        multiple
        accept=".ofx,.qfx,.csv,.txt,text/csv,application/x-ofx"
        className="sr-only"
        aria-label="Escolher arquivos de extrato"
        onChange={(e) => {
          if (e.target.files?.length) onArquivos([...e.target.files]);
          e.target.value = "";
        }}
      />
    </label>
  );
}

/** Passo 1 (modo detalhado): arrastar/soltar ou escolher arquivos (revisados um por vez). */
export function SeletorArquivos({ onArquivos, onExemplo, exemploDisponivel }: { onArquivos: (files: File[]) => void; onExemplo: () => void; exemploDisponivel: boolean }) {
  return (
    <Card aria-labelledby="t-envio">
      <CardTitulo id="t-envio">1. Enviar extrato</CardTitulo>
      <ZonaDrop onArquivos={onArquivos} />
      <p className="mt-3 text-xs text-muted">Privacidade: o arquivo é lido somente no seu navegador. Nada dele é enviado a servidores; só os lançamentos que você confirmar são gravados.</p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button onClick={onExemplo} disabled={!exemploDisponivel}>
          Experimentar com arquivo de exemplo
        </Button>
        <a className="inline-flex h-10 items-center rounded-lg border border-line px-4 text-sm font-medium hover:bg-surface-2" href="/exemplos/extrato-exemplo.ofx" download>
          Baixar OFX de exemplo
        </a>
        <a className="inline-flex h-10 items-center rounded-lg border border-line px-4 text-sm font-medium hover:bg-surface-2" href="/exemplos/fatura-exemplo.csv" download>
          Baixar CSV de fatura
        </a>
      </div>
      {!exemploDisponivel && <p className="mt-2 text-xs text-muted">O exemplo gerado usa os lançamentos de uma conta existente; carregue os dados de exemplo em Configurações (modo local) ou cadastre uma conta com lançamentos.</p>}
    </Card>
  );
}

const ROTULO_DELIM: Record<Delimitador, string> = { ";": "Ponto e vírgula (;)", ",": "Vírgula (,)", "\t": "Tabulação" };

/** Detecta o formato e, para CSV, mostra prévia e mapeamento de colunas. Chama `onPronto` com o extrato lido. */
export function ConfirmarArquivo({ arquivo, restantes, onPronto, onCancelar }: { arquivo: ArquivoLido; restantes: number; onPronto: (a: ArquivoExtrato) => void; onCancelar: () => void }) {
  const ofx = useMemo(() => pareceOfx(arquivo.texto), [arquivo.texto]);
  const analise = useMemo(() => (ofx ? null : analisarCsv(arquivo.texto)), [ofx, arquivo.texto]);
  const inicial = useMemo(() => (analise ? (recuperarMapeamento(analise.assinatura, analise.colunas) ?? analise.config) : null), [analise]);
  const [config, setConfig] = useState<ConfigCsv | null>(inicial);

  const parsed = useMemo(() => (ofx ? parseOfx(arquivo.texto) : config ? parseCsv(arquivo.texto, config) : null), [ofx, arquivo.texto, config]);

  const colunas = analise?.colunas ?? 0;
  const opcoesColuna = (nulo: boolean) => (
    <>
      {nulo && <option value="">— não usar —</option>}
      {Array.from({ length: colunas }, (_x, i) => (
        <option key={i} value={i}>
          {analise?.cabecalho[i] && config?.temCabecalho ? `${i + 1}. ${analise.cabecalho[i]}` : `Coluna ${i + 1}`}
        </option>
      ))}
    </>
  );
  const num = (v: string): number | null => (v === "" ? null : Number(v));
  const set = (patch: Partial<ConfigCsv>) => setConfig((c) => (c ? { ...c, ...patch } : c));
  const valido = parsed !== null && parsed.transacoes.length > 0;

  return (
    <Card aria-labelledby="t-confirma">
      <CardTitulo id="t-confirma">
        Arquivo: {arquivo.nome} {restantes > 0 && <span className="text-xs font-normal">(+{restantes} na fila)</span>}
      </CardTitulo>
      <p className="mb-3 text-sm">
        Formato detectado: <strong>{ofx ? "OFX" : "CSV"}</strong>
        {parsed && ` · ${parsed.transacoes.length} transação(ões)`}
      </p>

      {analise && config && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Select rotulo="Delimitador" value={config.delimitador} onChange={(e) => setConfig(analisarCsv(arquivo.texto, { ...config, delimitador: e.target.value as Delimitador }).config)}>
              {(Object.keys(ROTULO_DELIM) as Delimitador[]).map((d) => (
                <option key={d} value={d}>
                  {ROTULO_DELIM[d]}
                </option>
              ))}
            </Select>
            <Select rotulo="Decimal" value={config.decimal} onChange={(e) => set({ decimal: e.target.value as "," | "." })}>
              <option value=",">1.234,56</option>
              <option value=".">1,234.56</option>
            </Select>
            <Select rotulo="Coluna da data" value={config.colData} onChange={(e) => set({ colData: Number(e.target.value) })}>
              {opcoesColuna(false)}
            </Select>
            <Select rotulo="Coluna da descrição" value={config.colDescricao} onChange={(e) => set({ colDescricao: Number(e.target.value) })}>
              {opcoesColuna(false)}
            </Select>
            <Select rotulo="Coluna do valor (com sinal)" value={config.colValor ?? ""} onChange={(e) => set({ colValor: num(e.target.value) })}>
              {opcoesColuna(true)}
            </Select>
            <Select rotulo="Coluna de débito" value={config.colDebito ?? ""} onChange={(e) => set({ colDebito: num(e.target.value) })} disabled={config.colValor !== null}>
              {opcoesColuna(true)}
            </Select>
            <Select rotulo="Coluna de crédito" value={config.colCredito ?? ""} onChange={(e) => set({ colCredito: num(e.target.value) })} disabled={config.colValor !== null}>
              {opcoesColuna(true)}
            </Select>
            <Select rotulo="Coluna de saldo (opcional)" value={config.colSaldo ?? ""} onChange={(e) => set({ colSaldo: num(e.target.value) })}>
              {opcoesColuna(true)}
            </Select>
          </div>
          <div className="flex flex-wrap gap-x-6 gap-y-2">
            <Checkbox rotulo="A primeira linha é cabeçalho" checked={config.temCabecalho} onChange={(e) => set({ temCabecalho: e.target.checked })} />
            <Checkbox rotulo="Inverter sinal (fatura de cartão: compra positiva)" checked={config.inverter} onChange={(e) => set({ inverter: e.target.checked })} />
          </div>
          <div className="overflow-x-auto rounded-xl border border-line">
            <table className="w-full min-w-[32rem] text-xs">
              <caption className="sr-only">Prévia das primeiras linhas do arquivo</caption>
              <tbody>
                {analise.linhas.slice(0, 7).map((l, i) => (
                  <tr key={i} className={`border-b border-line last:border-0 ${i === 0 && config.temCabecalho ? "bg-surface-2 font-medium" : ""}`}>
                    {Array.from({ length: colunas }, (_x, c) => (
                      <td key={c} className="whitespace-nowrap px-2 py-1">
                        {l[c] ?? ""}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {parsed?.avisos.map((a) => (
        <p key={a} className="mt-2 text-xs text-warn">
          {a}
        </p>
      ))}
      {parsed && !valido && (
        <div className="mt-3">
          <ErroBox>Nenhuma transação encontrada. {ofx ? "Confira se o arquivo é um extrato OFX válido." : "Ajuste o mapeamento das colunas, o decimal ou o cabeçalho."}</ErroBox>
        </div>
      )}
      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Button onClick={onCancelar}>Descartar arquivo</Button>
        <Button
          variante="primary"
          disabled={!valido}
          onClick={() => {
            if (analise && config) lembrarMapeamento(analise.assinatura, config);
            if (parsed) onPronto(parsed);
          }}
        >
          Continuar
        </Button>
      </div>
    </Card>
  );
}
