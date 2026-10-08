"use client";

import { Download, FileImage, FileSpreadsheet, FileText } from "lucide-react";
import { useMemo, useState } from "react";
import { GraficoBarrasSimples, GraficoPatrimonio, GraficoPizza } from "@/components/charts/Graficos";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { Card, CardTitulo, PageHeader } from "@/components/ui/Misc";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { useSession } from "@/lib/session";
import { inicioDoDia, mesAnoDe } from "@/lib/finance/dates";
import { normalizaNome } from "@/lib/finance/calc";
import {
  csvDeDocumento,
  documentoDeResultado,
  documentoFatura,
  documentoPatrimonio,
  filtroPadrao,
  gerarRelatorio,
  MODELOS,
  periodoRapido,
  type Documento,
  type FiltroRelatorio,
  type PeriodoRapido,
  type ResultadoRelatorio,
  type TipoRelatorio,
} from "@/lib/finance/relatorios";
import { MASCARA, deInputData, paraInputData } from "@/lib/format";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";
import { baixarArquivo, baixarBlob } from "@/lib/download";
import { gerarPdf, formatarCelula } from "@/lib/exportar/pdf";
import { gerarPng } from "@/lib/exportar/png";
import { AnaliseMensal } from "./AnaliseMensal";

type Modelo = "personalizado" | "extrato" | "fatura" | "categoria" | "receitasDespesas" | "patrimonio" | "ir";
type PeriodoId = PeriodoRapido | "custom";

const MODELOS_UI: { id: Modelo; rotulo: string }[] = [
  { id: "personalizado", rotulo: "Personalizado" },
  { id: "extrato", rotulo: "Extrato por conta" },
  { id: "fatura", rotulo: "Fatura do cartão" },
  { id: "categoria", rotulo: "Gastos por categoria" },
  { id: "receitasDespesas", rotulo: "Receitas × despesas do mês" },
  { id: "patrimonio", rotulo: "Evolução do patrimônio" },
  { id: "ir", rotulo: "Anual para IR (Saúde e Educação)" },
];

function Chip({ ativo, onClick, children }: { ativo: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={ativo}
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${ativo ? "border-primary bg-primary-soft text-primary" : "border-line text-muted hover:bg-surface-2"}`}
    >
      {children}
    </button>
  );
}

function alterna<T>(lista: T[], v: T): T[] {
  return lista.includes(v) ? lista.filter((x) => x !== v) : [...lista, v];
}

export default function RelatoriosPage() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  const notificar = useNotificar();
  const hoje = mesAnoDe(agora);

  const [modelo, setModelo] = useState<Modelo>("personalizado");
  const [periodo, setPeriodo] = useState<PeriodoId>("mes");
  const [de, setDe] = useState(() => paraInputData(agora - 30 * 86_400_000));
  const [ate, setAte] = useState(() => paraInputData(agora));
  const [contas, setContas] = useState<string[]>([]);
  const [cartoes, setCartoes] = useState<number[]>([]);
  const [categorias, setCategorias] = useState<string[]>([]);
  const [tipo, setTipo] = useState<TipoRelatorio>("DESPESA");
  const [pago, setPago] = useState<"" | "pago" | "pendente">("");
  const [texto, setTexto] = useState("");
  const [contaExtrato, setContaExtrato] = useState(ds.contas[0]?.conta ?? "");
  const [cartaoFatura, setCartaoFatura] = useState(ds.cartoes[0]?.id ?? 0);
  const [mesRef, setMesRef] = useState(hoje);
  const [anoIR, setAnoIR] = useState(hoje.ano);
  const [exportando, setExportando] = useState<string | null>(null);

  const catsDisponiveis = useMemo(() => {
    const m = new Map<string, string>();
    for (const c of ds.categorias) m.set(normalizaNome(c.nome), c.nome);
    for (const d of ds.despesas) if (d.categoria.trim() && !m.has(normalizaNome(d.categoria))) m.set(normalizaNome(d.categoria), d.categoria.trim());
    return [...m.values()].sort((a, b) => a.localeCompare(b, "pt-BR"));
  }, [ds.categorias, ds.despesas]);

  const intervalo = useMemo(() => {
    if (periodo !== "custom") return periodoRapido(periodo, agora);
    const a = deInputData(de);
    const b = deInputData(ate);
    return { inicio: Number.isNaN(a) ? agora : inicioDoDia(a), fim: Number.isNaN(b) ? agora : inicioDoDia(b) + 86_399_999 };
  }, [periodo, agora, de, ate]);

  const { doc, resultado } = useMemo<{ doc: Documento | null; resultado: ResultadoRelatorio | null }>(() => {
    if (modelo === "patrimonio") return { doc: documentoPatrimonio(ds), resultado: null };
    if (modelo === "fatura") {
      const c = ds.cartoes.find((x) => x.id === cartaoFatura);
      return { doc: c ? documentoFatura(ds, c.id, mesRef.mes, mesRef.ano) : null, resultado: null };
    }
    let f: FiltroRelatorio;
    let titulo = "Relatório de lançamentos";
    if (modelo === "extrato") {
      f = MODELOS.extratoConta(contaExtrato, intervalo.inicio, intervalo.fim);
      titulo = `Extrato da conta ${ds.contas.find((c) => c.conta === contaExtrato)?.banco ?? contaExtrato}`;
    } else if (modelo === "receitasDespesas") {
      f = MODELOS.receitasDespesas(mesRef.mes, mesRef.ano);
      titulo = `Receitas x despesas ${String(mesRef.mes).padStart(2, "0")}/${mesRef.ano}`;
    } else if (modelo === "ir") {
      f = MODELOS.anualIR(anoIR);
      titulo = `Despesas dedutíveis (IR) ${anoIR}`;
    } else if (modelo === "categoria") {
      f = { ...MODELOS.gastosCategoria(intervalo.inicio, intervalo.fim), categorias, contas, cartoes };
      titulo = "Gastos por categoria";
    } else {
      f = { ...filtroPadrao(intervalo.inicio, intervalo.fim), contas, cartoes, categorias, tipo, pago: pago === "" ? null : pago === "pago", texto };
    }
    const r = gerarRelatorio(ds, f);
    return { doc: documentoDeResultado(titulo, f, r, ds), resultado: r };
  }, [ds, modelo, cartaoFatura, mesRef, contaExtrato, intervalo, anoIR, categorias, contas, cartoes, tipo, pago, texto]);

  async function exportar(formato: "pdf" | "png" | "csv") {
    if (!doc) return;
    setExportando(formato);
    try {
      if (formato === "csv") baixarArquivo(`${doc.nomeArquivo}.csv`, csvDeDocumento(doc), "text/csv;charset=utf-8");
      else if (formato === "pdf") baixarBlob(`${doc.nomeArquivo}.pdf`, await gerarPdf(doc));
      else baixarBlob(`${doc.nomeArquivo}.png`, await gerarPng(doc));
      notificar.sucesso(`${formato.toUpperCase()} gerado.`);
    } catch (e) {
      notificar.erro(`Não foi possível gerar o arquivo: ${e instanceof Error ? e.message : "erro desconhecido"}`);
    } finally {
      setExportando(null);
    }
  }

  const usaFiltros = modelo === "personalizado" || modelo === "categoria";
  const usaPeriodo = usaFiltros || modelo === "extrato";
  const fmtCelula = (v: string | number, i: number) => (typeof v === "number" && privado ? MASCARA : doc ? formatarCelula(v, i, doc) : String(v));

  return (
    <>
      <PageHeader
        titulo="Relatórios"
        descricao="Monte, visualize e exporte (PDF, PNG, CSV)"
        acoes={
          <>
            <Button icone={<FileText size={16} aria-hidden />} disabled={!doc || exportando !== null} onClick={() => void exportar("pdf")}>
              PDF
            </Button>
            <Button icone={<FileImage size={16} aria-hidden />} disabled={!doc || exportando !== null} onClick={() => void exportar("png")}>
              PNG
            </Button>
            <Button icone={<FileSpreadsheet size={16} aria-hidden />} disabled={!doc || exportando !== null} onClick={() => void exportar("csv")}>
              CSV
            </Button>
          </>
        }
      />

      <Card className="mb-4" aria-labelledby="t-construtor">
        <CardTitulo id="t-construtor">Construtor</CardTitulo>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Select rotulo="Modelo" value={modelo} onChange={(e) => setModelo(e.target.value as Modelo)}>
            {MODELOS_UI.map((m) => (
              <option key={m.id} value={m.id}>
                {m.rotulo}
              </option>
            ))}
          </Select>
          {usaPeriodo && (
            <Select rotulo="Período" value={periodo} onChange={(e) => setPeriodo(e.target.value as PeriodoId)}>
              <option value="mes">Mês atual</option>
              <option value="anterior">Mês anterior</option>
              <option value="ano">Ano atual</option>
              <option value="custom">Personalizado</option>
            </Select>
          )}
          {usaPeriodo && periodo === "custom" && (
            <>
              <Input rotulo="De" type="date" value={de} onChange={(e) => setDe(e.target.value)} />
              <Input rotulo="Até" type="date" value={ate} onChange={(e) => setAte(e.target.value)} />
            </>
          )}
          {modelo === "extrato" && (
            <Select rotulo="Conta" value={contaExtrato} onChange={(e) => setContaExtrato(e.target.value)}>
              {ds.contas.map((c) => (
                <option key={c.id} value={c.conta}>
                  {c.banco} · {c.conta}
                </option>
              ))}
            </Select>
          )}
          {modelo === "fatura" && (
            <Select rotulo="Cartão" value={cartaoFatura} onChange={(e) => setCartaoFatura(Number(e.target.value))}>
              {ds.cartoes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome}
                  {c.cartaoPrincipalId != null ? " (virtual)" : ""}
                </option>
              ))}
            </Select>
          )}
          {modelo === "ir" && <Input rotulo="Ano" type="number" min={2000} max={2100} value={anoIR} onChange={(e) => setAnoIR(Number(e.target.value) || hoje.ano)} />}
          {(modelo === "fatura" || modelo === "receitasDespesas") && (
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{modelo === "fatura" ? "Mês da fatura" : "Mês"}</span>
              <MonthPicker mes={mesRef.mes} ano={mesRef.ano} onChange={setMesRef} />
            </div>
          )}
          {modelo === "personalizado" && (
            <>
              <Select rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoRelatorio)}>
                <option value="DESPESA">Despesas</option>
                <option value="RECEITA">Receitas</option>
                <option value="TODOS">Receitas e despesas</option>
              </Select>
              <Select rotulo="Situação" value={pago} onChange={(e) => setPago(e.target.value as "" | "pago" | "pendente")}>
                <option value="">Pagos e pendentes</option>
                <option value="pago">Pagos</option>
                <option value="pendente">Pendentes</option>
              </Select>
              <Input rotulo="Texto" type="search" value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Descrição ou categoria" />
            </>
          )}
        </div>

        {usaFiltros && (
          <div className="mt-4 space-y-3">
            <fieldset>
              <legend className="mb-1.5 text-sm font-medium">Contas</legend>
              <div className="flex flex-wrap gap-2">
                {ds.contas.length === 0 && <span className="text-xs text-muted">Nenhuma conta.</span>}
                {ds.contas.map((c) => (
                  <Chip key={c.id} ativo={contas.includes(c.conta)} onClick={() => setContas(alterna(contas, c.conta))}>
                    {c.banco} · {c.conta}
                  </Chip>
                ))}
              </div>
            </fieldset>
            <fieldset>
              <legend className="mb-1.5 text-sm font-medium">Cartões (físico inclui seus virtuais)</legend>
              <div className="flex flex-wrap gap-2">
                {ds.cartoes.length === 0 && <span className="text-xs text-muted">Nenhum cartão.</span>}
                {ds.cartoes.map((c) => (
                  <Chip key={c.id} ativo={cartoes.includes(c.id)} onClick={() => setCartoes(alterna(cartoes, c.id))}>
                    {c.nome}
                    {c.cartaoPrincipalId != null ? " (virtual)" : ""}
                  </Chip>
                ))}
              </div>
            </fieldset>
            <fieldset>
              <legend className="mb-1.5 text-sm font-medium">Categorias</legend>
              <div className="flex flex-wrap gap-2">
                {catsDisponiveis.map((c) => (
                  <Chip key={c} ativo={categorias.some((x) => normalizaNome(x) === normalizaNome(c))} onClick={() => setCategorias(alterna(categorias, c))}>
                    {c}
                  </Chip>
                ))}
              </div>
            </fieldset>
          </div>
        )}
      </Card>

      {!doc ? (
        <Card>
          <p className="py-6 text-center text-sm text-muted">Selecione os dados do modelo para ver a prévia.</p>
        </Card>
      ) : (
        <section aria-label="Prévia do relatório" className="space-y-4">
          <Card>
            <CardTitulo>{doc.titulo}</CardTitulo>
            <ul className="mb-3 space-y-0.5 text-xs text-muted">
              {doc.filtros.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {doc.totais.map((t) => (
                <div key={t.rotulo} className="rounded-xl bg-surface-2 px-3 py-2">
                  <dt className="text-xs text-muted">{t.rotulo}</dt>
                  <dd className="tabular text-sm font-semibold">{privado && /R\$/.test(t.valor) ? MASCARA : t.valor}</dd>
                </div>
              ))}
            </dl>
          </Card>

          {doc.grafico && (
            <div className="grid gap-4 lg:grid-cols-2">
              <Card aria-labelledby="t-g1">
                <CardTitulo id="t-g1">{doc.grafico.titulo}</CardTitulo>
                {modelo === "patrimonio" ? (
                  <GraficoPatrimonio dados={doc.grafico.dados} rotulo="Evolução do patrimônio" />
                ) : (
                  <GraficoPizza dados={doc.grafico.dados.map((d) => ({ nome: d.rotulo, valor: d.valor }))} rotulo={doc.grafico.titulo} />
                )}
              </Card>
              {resultado && resultado.porMes.length > 0 && (
                <Card aria-labelledby="t-g2">
                  <CardTitulo id="t-g2">Por mês</CardTitulo>
                  <GraficoBarrasSimples dados={resultado.porMes.map((p) => ({ rotulo: p.mes, valor: Math.abs(p.total) }))} nome="Total" rotulo="Total por mês" />
                </Card>
              )}
            </div>
          )}

          <Card aria-labelledby="t-tab">
            <CardTitulo id="t-tab" acao={<Download size={14} className="text-muted" aria-hidden />}>
              Tabela ({doc.linhas.length})
            </CardTitulo>
            {doc.linhas.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted">Nenhum lançamento para os filtros escolhidos.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[40rem] text-sm">
                  <caption className="sr-only">{doc.titulo}</caption>
                  <thead>
                    <tr className="border-b border-line text-left text-xs text-muted">
                      {doc.colunas.map((c, i) => (
                        <th key={c} scope="col" className={`py-2 pr-3 font-medium ${doc.colunasMoeda.includes(i) ? "text-right" : ""}`}>
                          {c}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {doc.linhas.slice(0, 300).map((l, r) => (
                      <tr key={r} className="border-b border-line last:border-0">
                        {l.map((v, i) => (
                          <td key={i} className={`py-1.5 pr-3 ${doc.colunasMoeda.includes(i) ? "tabular text-right" : ""}`}>
                            {fmtCelula(v, i)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
                {doc.linhas.length > 300 && <p className="mt-2 text-xs text-muted">Mostrando 300 de {doc.linhas.length}; PDF e CSV incluem todas as linhas.</p>}
              </div>
            )}
          </Card>
        </section>
      )}

      <div className="mt-8">
        <AnaliseMensal />
      </div>
    </>
  );
}
