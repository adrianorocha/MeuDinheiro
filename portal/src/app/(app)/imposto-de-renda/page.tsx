"use client";

import { AlertTriangle, Download, FileText } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input, InputValor, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, PageHeader } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { useConfigGeral } from "@/lib/configGeral";
import { baixarArquivo, baixarBlob } from "@/lib/download";
import { gerarPdf } from "@/lib/exportar/pdf";
import { chaveCategoria } from "@/lib/finance/painel503020";
import {
  AVISO_IR,
  CLASSES_ESCOLHA,
  CONFIG_IR_PADRAO,
  LIMITE_EDUCACAO_PADRAO_CENTAVOS,
  ROTULO_CLASSE,
  ROTULO_DEDUCAO,
  TIPOS_DEDUCAO,
  anosComDados,
  classificarDeducao,
  classificarRendimento,
  documentoIR,
  normalizarConfigIR,
  relatorioIR,
  type ClasseRendimento,
  type ConfigIR,
  type ItemIR,
  type TipoDeducao,
} from "@/lib/finance/imposto";
import { csvDeDocumento } from "@/lib/finance/relatorios";
import { parseValorBR } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { useStore } from "@/lib/store/store";

const reais = (c: number) => c / 100;
const data = (ms: number) => new Date(ms).toLocaleDateString("pt-BR");

function Cent({ c }: { c: number }) {
  return <Money valor={reais(c)} />;
}

function TabelaItens({ itens, comComprovante }: { itens: ItemIR[]; comComprovante: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-muted">
            <th className="py-1 pr-3 font-medium">Data</th>
            <th className="py-1 pr-3 font-medium">Descrição</th>
            {comComprovante && <th className="py-1 pr-3 font-medium">Comprovante?</th>}
            <th className="py-1 text-right font-medium">Valor</th>
          </tr>
        </thead>
        <tbody>
          {itens.map((i) => (
            <tr key={i.id} className="border-t border-line">
              <td className="py-1 pr-3 tabular whitespace-nowrap">{data(i.data)}</td>
              <td className="py-1 pr-3">
                {i.descricao}
                {i.atencao && <span className="block text-xs text-warn">{i.atencao}</span>}
              </td>
              {comComprovante && <td className="py-1 pr-3">{i.comprovante ? <Badge tom="pos">Sim</Badge> : <Badge tom="warn">Não</Badge>}</td>}
              <td className="py-1 text-right">
                <Cent c={i.centavos} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function ImpostoDeRendaPage() {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const bruta = useConfigGeral((s) => s.config.ir);
  const alterar = useConfigGeral((s) => s.alterar);
  const cfg: ConfigIR = useMemo(() => (bruta ? normalizarConfigIR(bruta) : CONFIG_IR_PADRAO), [bruta]);
  const salvar = (c: ConfigIR) => alterar({ ir: normalizarConfigIR(c) });

  const anos = useMemo(() => anosComDados(ds.despesas, agora), [ds.despesas, agora]);
  const [ano, setAno] = useState<number>(() => new Date(agora).getFullYear() - 1);
  const [limiteTxt, setLimiteTxt] = useState<string | null>(null);

  const r = useMemo(() => relatorioIR(ds, ano, cfg, agora), [ds, ano, cfg, agora]);

  // categorias de receita e de despesa do ano, para a pessoa escolher a classe
  const catsReceita = useMemo(() => {
    const m = new Map<string, string>();
    for (const g of r.rendimentos.grupos) m.set(g.chave, g.categoria);
    return [...m.entries()].sort((a, b) => a[1].localeCompare(b[1], "pt-BR"));
  }, [r]);
  const catsDespesa = useMemo(() => {
    const m = new Map<string, string>();
    for (const d of ds.despesas) {
      if (d.natureza !== "NORMAL" || d.tipo !== "DEBITO" || new Date(d.data).getFullYear() !== ano) continue;
      const nome = d.categoria.trim();
      if (nome) m.set(chaveCategoria(nome), nome);
    }
    return [...m.entries()].sort((a, b) => a[1].localeCompare(b[1], "pt-BR"));
  }, [ds.despesas, ano]);

  const doc = () => documentoIR(r);
  async function exportar(formato: "pdf" | "csv") {
    try {
      const d = doc();
      if (formato === "csv") baixarArquivo(`${d.nomeArquivo}.csv`, csvDeDocumento(d), "text/csv;charset=utf-8");
      else baixarBlob(`${d.nomeArquivo}.pdf`, await gerarPdf(d));
    } catch {
      avisar("erro", "Não foi possível gerar o arquivo.");
    }
  }

  const mudarRend = (chave: string, v: string) => {
    const rend = { ...cfg.rendimentos };
    if (v) rend[chave] = v as ClasseRendimento;
    else delete rend[chave];
    salvar({ ...cfg, rendimentos: rend });
  };
  const mudarDed = (chave: string, v: string) => {
    const ded = { ...cfg.deducoes };
    if (v) ded[chave] = v as TipoDeducao | "NAO";
    else delete ded[chave];
    salvar({ ...cfg, deducoes: ded });
  };

  const limiteAtual = (cfg.limiteEducacaoCentavos / 100).toFixed(2).replace(".", ",");
  const confirmarLimite = () => {
    if (limiteTxt === null) return;
    const v = parseValorBR(limiteTxt);
    if (Number.isFinite(v) && v >= 0) salvar({ ...cfg, limiteEducacaoCentavos: Math.round(v * 100) });
    else avisar("erro", "Informe um valor válido para o limite.");
    setLimiteTxt(null);
  };

  return (
    <>
      <PageHeader
        titulo="Imposto de Renda"
        descricao="Apoio à declaração (IRPF): rendimentos, despesas dedutíveis candidatas, bens e dívidas do ano-calendário"
        acoes={
          <>
            <Select rotulo="Ano-calendário" value={String(ano)} onChange={(e) => setAno(Number(e.target.value))} wrapperClassName="w-40">
              {anos.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </Select>
            <Button icone={<FileText size={16} aria-hidden />} onClick={() => void exportar("pdf")}>
              PDF
            </Button>
            <Button icone={<Download size={16} aria-hidden />} onClick={() => void exportar("csv")}>
              CSV
            </Button>
          </>
        }
      />

      <div role="note" className="mb-4 flex gap-3 rounded-2xl border border-warn/50 bg-warn-soft p-4 text-sm text-warn">
        <AlertTriangle size={20} className="mt-0.5 shrink-0" aria-hidden />
        <div>
          <p className="font-semibold">Triagem, não é a declaração oficial</p>
          <p className="mt-1">{AVISO_IR}</p>
          {r.avisos.map((a) => (
            <p key={a} className="mt-1">
              {a}
            </p>
          ))}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card aria-label="Rendimentos">
          <CardTitulo>1. Rendimentos recebidos em {ano} (realizado)</CardTitulo>
          <ul className="mb-3 flex flex-col gap-1 text-sm">
            {r.rendimentos.porClasse
              .filter((t) => t.totalCentavos !== 0 || t.quantidade > 0)
              .map((t) => (
                <li key={t.classe} className="flex items-center justify-between gap-2">
                  <span>
                    {ROTULO_CLASSE[t.classe]} <span className="text-xs text-muted">({t.quantidade})</span>
                  </span>
                  <Cent c={t.totalCentavos} />
                </li>
              ))}
            <li className="flex items-center justify-between gap-2 border-t border-line pt-1 font-semibold">
              <span>Total recebido</span>
              <Cent c={r.rendimentos.totalRealizadoCentavos} />
            </li>
            <li className="flex items-center justify-between gap-2 text-muted">
              <span>Previsto e ainda não recebido (estimativa, fora dos totais)</span>
              <Cent c={r.rendimentos.previstoCentavos} />
            </li>
          </ul>
          <p className="mb-2 text-xs text-muted">
            Investimentos: informe conforme o informe de rendimentos do banco/corretora. 13º e PLR são classificados como tributação exclusiva na fonte; ajuste abaixo se o seu informe disser outra coisa.
          </p>
          {catsReceita.length === 0 ? (
            <p className="text-sm text-muted">Nenhuma receita recebida neste ano.</p>
          ) : (
            <div className="flex flex-col gap-2">
              {catsReceita.map(([chave, nome]) => {
                const padrao = classificarRendimento(nome);
                return (
                  <Select
                    key={chave}
                    rotulo={nome}
                    value={cfg.rendimentos[chave] ?? ""}
                    onChange={(e) => mudarRend(chave, e.target.value)}
                  >
                    <option value="">Padrão ({ROTULO_CLASSE[padrao]})</option>
                    {CLASSES_ESCOLHA.map((c) => (
                      <option key={c} value={c}>
                        {ROTULO_CLASSE[c]}
                      </option>
                    ))}
                  </Select>
                );
              })}
            </div>
          )}
        </Card>

        <Card aria-label="Deduções">
          <CardTitulo>2. Despesas dedutíveis candidatas (realizado)</CardTitulo>
          <ul className="mb-3 flex flex-col gap-1 text-sm">
            {r.deducoes.porTipo.map((t) => (
              <li key={t.tipo} className="flex items-start justify-between gap-2">
                <span>
                  {ROTULO_DEDUCAO[t.tipo]}
                  {t.limiteCentavos !== null && (
                    <span className="block text-xs text-muted">
                      limite {brl(t.limiteCentavos)}
                      {t.excessoCentavos > 0 ? ` · excesso ${brl(t.excessoCentavos)} (não entra)` : ""}
                    </span>
                  )}
                </span>
                <Cent c={t.deduzivelCentavos} />
              </li>
            ))}
            <li className="flex items-center justify-between gap-2 border-t border-line pt-1 font-semibold">
              <span>Total candidato a dedução</span>
              <Cent c={r.deducoes.totalDeduzivelCentavos} />
            </li>
            <li className="flex items-center justify-between gap-2 text-muted">
              <span>Ainda não pago (estimativa, fora dos totais)</span>
              <Cent c={r.deducoes.pendentesCentavos} />
            </li>
          </ul>
          <div className="mb-3 grid grid-cols-2 gap-3">
            <InputValor
              rotulo="Limite de educação por pessoa (R$)"
              value={limiteTxt ?? limiteAtual}
              onChange={(e) => setLimiteTxt(e.target.value)}
              onBlur={confirmarLimite}
              dica={`Valor de referência (R$ ${(LIMITE_EDUCACAO_PADRAO_CENTAVOS / 100).toFixed(2).replace(".", ",")}): confirme o limite vigente do ano-base na Receita.`}
            />
            <Input
              rotulo="Pessoas com educação"
              type="number"
              min={1}
              max={20}
              value={cfg.pessoasEducacao}
              onChange={(e) => salvar({ ...cfg, pessoasEducacao: Number(e.target.value) })}
              dica="Titular + dependentes (o limite vale por pessoa)."
            />
          </div>
          <p className="mb-2 text-xs text-muted">
            Previdência privada (PGBL): até 12% da renda tributável ({brl(r.deducoes.limitePgblCentavos)} sobre {brl(r.deducoes.baseTributavelCentavos)} tributáveis lançados). Doações: só fundos e projetos incentivados. Remédios e óculos não entram.
          </p>
          <details>
            <summary className="cursor-pointer text-sm font-medium">Escolher quais categorias entram ({catsDespesa.length})</summary>
            <div className="mt-2 flex flex-col gap-2">
              {catsDespesa.map(([chave, nome]) => {
                const padrao = classificarDeducao(nome);
                return (
                  <Select key={chave} rotulo={nome} value={cfg.deducoes[chave] ?? ""} onChange={(e) => mudarDed(chave, e.target.value)}>
                    <option value="">Padrão ({padrao ? ROTULO_DEDUCAO[padrao] : "não entra"})</option>
                    <option value="NAO">Não entra</option>
                    {TIPOS_DEDUCAO.map((t) => (
                      <option key={t} value={t}>
                        {ROTULO_DEDUCAO[t]}
                      </option>
                    ))}
                  </Select>
                );
              })}
            </div>
          </details>
        </Card>
      </div>

      <Card className="mt-4" aria-label="Lançamentos candidatos">
        <CardTitulo>Lançamentos candidatos a dedução ({r.deducoes.quantidade})</CardTitulo>
        {r.deducoes.quantidade > 0 && (
          <p className="mb-3 text-sm text-muted">
            {r.deducoes.semComprovante} sem comprovante anexado. Guarde recibos/notas com CPF/CNPJ do profissional: a Receita pode pedir.
          </p>
        )}
        {r.deducoes.grupos.length === 0 ? (
          <p className="text-sm text-muted">Nenhuma despesa candidata paga neste ano.</p>
        ) : (
          <div className="flex flex-col gap-4">
            {r.deducoes.grupos.map((g) => (
              <section key={`${g.tipo}|${g.chave}`}>
                <h3 className="mb-1 flex items-center justify-between text-sm font-semibold">
                  <span>
                    {g.categoria} <Badge>{ROTULO_DEDUCAO[g.tipo as TipoDeducao]}</Badge>
                  </span>
                  <Cent c={g.totalCentavos} />
                </h3>
                <TabelaItens itens={g.itens} comComprovante />
              </section>
            ))}
          </div>
        )}
        {r.deducoes.naoDedutiveis.length > 0 && (
          <section className="mt-4">
            <h3 className="mb-1 flex items-center justify-between text-sm font-semibold">
              <span>Provavelmente não dedutíveis (fora do total)</span>
              <Cent c={r.deducoes.naoDedutiveisCentavos} />
            </h3>
            <TabelaItens itens={r.deducoes.naoDedutiveis} comComprovante />
          </section>
        )}
      </Card>

      <Card className="mt-4" aria-label="Rendimentos detalhados">
        <CardTitulo>Rendimentos detalhados</CardTitulo>
        {r.rendimentos.grupos.length === 0 ? (
          <p className="text-sm text-muted">Nada recebido neste ano.</p>
        ) : (
          <div className="flex flex-col gap-4">
            {r.rendimentos.grupos.map((g) => (
              <section key={`${g.classe}|${g.chave}`}>
                <h3 className="mb-1 flex items-center justify-between text-sm font-semibold">
                  <span>
                    {g.categoria} <Badge tom={g.classe === "REVISAR" ? "warn" : "neutro"}>{ROTULO_CLASSE[g.classe ?? "REVISAR"]}</Badge>
                  </span>
                  <Cent c={g.totalCentavos} />
                </h3>
                <TabelaItens itens={g.itens} comComprovante={false} />
              </section>
            ))}
          </div>
        )}
      </Card>

      <Card className="mt-4" aria-label="Bens e dívidas">
        <CardTitulo>3. Bens e dívidas</CardTitulo>
        {r.bens.avisoSaldoAtual && (
          <p role="status" className="mb-3 text-sm text-warn">
            {r.bens.avisoSaldoAtual}
          </p>
        )}
        <div className="grid gap-4 md:grid-cols-2">
          <ListaSaldos titulo={`Saldo das contas em 31/12/${ano}`} itens={r.bens.contas} total={r.bens.totalContasCentavos} nota="Calculado a partir dos lançamentos pagos até a data. Contas com saldo muito baixo normalmente não precisam constar: confirme a regra vigente." />
          <ListaSaldos titulo="Investimentos (saldo atual)" itens={r.bens.investimentos} total={r.bens.totalInvestimentosCentavos} />
          <ListaSaldos titulo="Metas / cofrinhos (saldo atual)" itens={r.bens.metas} total={r.bens.totalMetasCentavos} />
          <ListaSaldos titulo="Dívidas ativas (saldo devedor atual)" itens={r.bens.dividas} total={r.bens.totalDividasCentavos} />
        </div>
      </Card>

      <Card className="mt-4" aria-label="Totais e checagem">
        <CardTitulo>4. Totais e checagem</CardTitulo>
        <ul className="flex flex-col gap-1 text-sm">
          <li className="flex justify-between gap-2">
            <span>Rendimentos tributáveis lançados</span>
            <Cent c={r.deducoes.baseTributavelCentavos} />
          </li>
          <li className="flex justify-between gap-2">
            <span>Deduções candidatas</span>
            <Cent c={r.deducoes.totalDeduzivelCentavos} />
          </li>
          <li className="flex justify-between gap-2 border-t border-line pt-1 font-semibold">
            <span>Diferença indicativa (NÃO é a base de cálculo oficial)</span>
            <Cent c={r.indicativoBaseCentavos} />
          </li>
          <li className="mt-2 flex items-center gap-2">
            {r.checagem.confere && r.checagem.deducoesConferem ? <Badge tom="pos">Totais conferem</Badge> : <Badge tom="neg">Totais não conferem</Badge>}
            <span className="text-xs text-muted">
              Receitas recebidas no ano: {brl(r.checagem.receitasRealizadasCentavos)} = soma das classes {brl(r.checagem.somaClassesCentavos)}
            </span>
          </li>
        </ul>
      </Card>
    </>
  );
}

function brl(c: number): string {
  return reais(c).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function ListaSaldos({ titulo, itens, total, nota }: { titulo: string; itens: { nome: string; detalhe: string; centavos: number }[]; total: number; nota?: string }) {
  return (
    <section>
      <h3 className="mb-1 text-sm font-semibold">{titulo}</h3>
      {itens.length === 0 ? (
        <p className="text-sm text-muted">Nada cadastrado.</p>
      ) : (
        <ul className="flex flex-col gap-1 text-sm">
          {itens.map((i, k) => (
            <li key={`${i.nome}-${i.detalhe}-${k}`} className="flex justify-between gap-2">
              <span>
                {i.nome} <span className="text-xs text-muted">{i.detalhe}</span>
              </span>
              <Cent c={i.centavos} />
            </li>
          ))}
          <li className="flex justify-between gap-2 border-t border-line pt-1 font-semibold">
            <span>Total</span>
            <Cent c={total} />
          </li>
        </ul>
      )}
      {nota && <p className="mt-1 text-xs text-muted">{nota}</p>}
    </section>
  );
}
