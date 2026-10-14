"use client";

import { FileDown } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Segmentado, Select } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { baixarBlob } from "@/lib/download";
import { brlCentavos } from "@/lib/finance/centavos";
import { fimDoDia, inicioDoDia } from "@/lib/finance/dates";
import {
  MSG_SEM_ACERTOS,
  montarRelatorioPessoas,
  nomeArquivoPessoas,
  periodoPorAtalho,
  type AgruparRelatorio,
  type AtalhoPeriodo,
  type SituacaoRelatorio,
} from "@/lib/finance/relatorioPessoas";
import type { Acerto, Despesa, Pessoa } from "@/lib/finance/types";
import { deInputData, paraInputData } from "@/lib/format";
import { useAgora } from "@/lib/hooks";

type Formato = "pdf" | "png" | "jpeg";
type AtalhoOuPersonalizado = AtalhoPeriodo | "PERSONALIZADO";

interface Props {
  aberto: boolean;
  onFechar: () => void;
  pessoas: Pessoa[];
  acertos: Acerto[];
  despesas: Despesa[];
  /** abre já filtrado nesta pessoa ("Relatório desta pessoa") */
  pessoaIdInicial?: number;
}

/** R69 - relatório detalhado de Pessoas (PDF, PNG ou JPEG), com pré-visualização dos totais antes de gerar. */
export function RelatorioPessoasModal({ aberto, onFechar, ...resto }: Props) {
  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo="Relatório de pessoas">
      {aberto && <Conteudo onFechar={onFechar} {...resto} />}
    </Modal>
  );
}

function Conteudo({ onFechar, pessoas, acertos, despesas, pessoaIdInicial }: Omit<Props, "aberto">) {
  const agora = useAgora();
  const [escolher, setEscolher] = useState(pessoaIdInicial !== undefined);
  const [ids, setIds] = useState<Set<number>>(new Set(pessoaIdInicial !== undefined ? [pessoaIdInicial] : []));
  const [atalho, setAtalho] = useState<AtalhoOuPersonalizado>("TUDO");
  const [de, setDe] = useState(paraInputData(agora));
  const [ate, setAte] = useState(paraInputData(agora));
  const [situacao, setSituacao] = useState<SituacaoRelatorio>("TODOS");
  const [detalhes, setDetalhes] = useState(true);
  const [agrupar, setAgrupar] = useState<AgruparRelatorio>("POR_PESSOA");
  const [rateios, setRateios] = useState(false);
  const [formato, setFormato] = useState<Formato>("pdf");
  const [gerando, setGerando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const periodo = useMemo(() => {
    if (atalho !== "PERSONALIZADO") return { ok: true as const, valor: periodoPorAtalho(atalho, agora) };
    const a = deInputData(de);
    const b = deInputData(ate);
    if (Number.isNaN(a) || Number.isNaN(b)) return { ok: false as const, erro: "Informe as duas datas do período." };
    if (a > b) return { ok: false as const, erro: "A data inicial deve ser anterior à final." };
    return { ok: true as const, valor: { inicio: inicioDoDia(a), fim: fimDoDia(b) } };
  }, [atalho, de, ate, agora]);

  const relatorio = useMemo(() => {
    if (!periodo.ok) return null;
    return montarRelatorioPessoas(pessoas, acertos, despesas, {
      pessoaIds: escolher ? [...ids] : [],
      periodo: periodo.valor,
      situacao,
      incluirDetalhes: detalhes,
      agrupar,
      incluirRateios: rateios,
      agora,
    });
  }, [pessoas, acertos, despesas, escolher, ids, periodo, situacao, detalhes, agrupar, rateios, agora]);

  const semEscolha = escolher && ids.size === 0;
  const podeGerar = !gerando && relatorio !== null && !relatorio.vazio && !semEscolha;

  function alternar(id: number) {
    setIds((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  async function gerar() {
    if (!relatorio || relatorio.vazio) return setErro(MSG_SEM_ACERTOS);
    setErro(null);
    setGerando(true);
    try {
      if (formato === "pdf") {
        const { gerarPdfPessoas } = await import("@/lib/exportar/pessoasPdf");
        baixarBlob(nomeArquivoPessoas(agora, "pdf"), await gerarPdfPessoas(relatorio));
      } else {
        const { gerarImagensPessoas } = await import("@/lib/exportar/pessoasImagem");
        const partes = await gerarImagensPessoas(relatorio, formato);
        const ext = formato === "png" ? "png" : "jpg";
        for (let i = 0; i < partes.length; i++) {
          baixarBlob(nomeArquivoPessoas(agora, ext, partes.length > 1 ? i + 1 : undefined), partes[i]);
          if (i < partes.length - 1) await new Promise((r) => setTimeout(r, 400));
        }
      }
      onFechar();
    } catch (e) {
      setErro(e instanceof Error && e.message ? `Não foi possível gerar o relatório: ${e.message}` : "Não foi possível gerar o relatório. Tente de novo.");
    } finally {
      setGerando(false);
    }
  }

  const r = relatorio?.resumo;
  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex flex-col gap-2">
        <Segmentado
          rotulo="Pessoas"
          valor={escolher ? "ESCOLHER" : "TODAS"}
          opcoes={[{ valor: "TODAS", rotulo: "Todas as pessoas" }, { valor: "ESCOLHER", rotulo: "Escolher" }]}
          onChange={(v) => setEscolher(v === "ESCOLHER")}
        />
        {escolher && (
          <fieldset className="flex max-h-40 flex-col gap-1.5 overflow-y-auto rounded-lg border border-line p-3">
            <legend className="px-1 text-xs text-muted">Marque uma ou mais pessoas</legend>
            {pessoas.map((p) => (
              <Checkbox key={p.id} rotulo={p.nome} checked={ids.has(p.id)} onChange={() => alternar(p.id)} />
            ))}
          </fieldset>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Select rotulo="Período (data do acerto)" value={atalho} onChange={(e) => setAtalho(e.target.value as AtalhoOuPersonalizado)}>
          <option value="ESTE_MES">Este mês</option>
          <option value="MES_PASSADO">Mês passado</option>
          <option value="TRES_MESES">Últimos 3 meses</option>
          <option value="ANO">Este ano</option>
          <option value="TUDO">Tudo</option>
          <option value="PERSONALIZADO">Personalizado</option>
        </Select>
        <Select rotulo="Situação" value={situacao} onChange={(e) => setSituacao(e.target.value as SituacaoRelatorio)}>
          <option value="TODOS">Todos</option>
          <option value="PENDENTES">Somente pendentes</option>
          <option value="QUITADOS">Somente quitados</option>
        </Select>
      </div>
      {atalho === "PERSONALIZADO" && (
        <div className="grid grid-cols-2 gap-3">
          <Input rotulo="De" type="date" value={de} onChange={(e) => setDe(e.target.value)} />
          <Input rotulo="Até" type="date" value={ate} onChange={(e) => setAte(e.target.value)} />
        </div>
      )}
      {!periodo.ok && <ErroBox>{periodo.erro}</ErroBox>}

      <div className="flex flex-col gap-2">
        <Checkbox rotulo="Incluir detalhes (uma linha por acerto)" checked={detalhes} onChange={(e) => setDetalhes(e.target.checked)} />
        <Checkbox rotulo="Incluir bloco “Rateios” (quanto você realmente gastou, informativo)" checked={rateios} onChange={(e) => setRateios(e.target.checked)} />
        {detalhes && (
          <Segmentado
            rotulo="Agrupar"
            valor={agrupar}
            opcoes={[{ valor: "POR_PESSOA", rotulo: "Por pessoa" }, { valor: "CRONOLOGICO", rotulo: "Cronológico" }]}
            onChange={setAgrupar}
          />
        )}
        <Segmentado
          rotulo="Formato"
          valor={formato}
          opcoes={[{ valor: "pdf", rotulo: "PDF" }, { valor: "png", rotulo: "PNG" }, { valor: "jpeg", rotulo: "JPEG" }]}
          onChange={setFormato}
        />
      </div>

      <section aria-label="Pré-visualização do resumo" className="rounded-lg border border-line bg-surface-2 p-3">
        <h3 className="mb-2 text-xs font-semibold uppercase text-muted">Pré-visualização do resumo</h3>
        {semEscolha ? (
          <p className="text-muted">Escolha ao menos uma pessoa.</p>
        ) : !relatorio ? (
          <p className="text-muted">Corrija o período para ver o resumo.</p>
        ) : relatorio.vazio || !r ? (
          <p>{MSG_SEM_ACERTOS}</p>
        ) : (
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 tabular-nums">
            <dt className="text-muted">A receber (pendente)</dt>
            <dd className="text-right font-semibold text-pos">{brlCentavos(r.aReceberPendente)}</dd>
            <dt className="text-muted">A pagar (pendente)</dt>
            <dd className="text-right font-semibold text-warn">{brlCentavos(r.aPagarPendente)}</dd>
            <dt className="text-muted">Saldo líquido</dt>
            <dd className="text-right font-semibold">
              {brlCentavos(Math.abs(r.saldoLiquido))} {r.saldoLiquido > 0 ? "(a seu favor)" : r.saldoLiquido < 0 ? "(contra você)" : "(quites)"}
            </dd>
            <dt className="text-muted">Quitado (a receber / a pagar)</dt>
            <dd className="text-right">{brlCentavos(r.quitadoAReceber)} / {brlCentavos(r.quitadoAPagar)}</dd>
            <dt className="text-muted">Acertos / pessoas</dt>
            <dd className="text-right">{r.nAcertos} / {r.nPessoas} ({r.nPessoasComSaldo} com saldo)</dd>
          </dl>
        )}
      </section>

      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar} disabled={gerando}>Cancelar</Button>
        <Button variante="primary" icone={<FileDown size={16} aria-hidden />} onClick={gerar} disabled={!podeGerar}>
          {gerando ? "Gerando…" : "Gerar"}
        </Button>
      </div>
    </div>
  );
}
