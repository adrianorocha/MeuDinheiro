"use client";

import { AlertTriangle, CheckCircle2, FileSearch, Info, Search } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { lerArquivo } from "@/components/conciliacao/EnvioArquivo";
import { Button } from "@/components/ui/Button";
import { InputValor } from "@/components/ui/Field";
import { Badge } from "@/components/ui/Misc";
import { Confirmar, Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { interpretarTexto } from "@/lib/conciliacao/leitura";
import type { ArquivoExtrato } from "@/lib/conciliacao/tipos";
import { analisarFatura, type Confianca, type Hipotese } from "@/lib/finance/analiseFatura";
import type { Cartao } from "@/lib/finance/types";
import { formatBRL, parseValorBR } from "@/lib/format";
import { useDataset, useNotificar } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";

interface Props {
  aberto: boolean;
  onFechar: () => void;
  cartao: Cartao;
  mes: number;
  ano: number;
}

const TOM: Record<Confianca, "pos" | "warn" | "neutro"> = { alta: "pos", media: "warn", baixa: "neutro" };
const ROTULO: Record<Confianca, string> = { alta: "Muito provável", media: "Provável", baixa: "Possível" };

/**
 * R107 - "Analisar fatura": informe o valor da fatura no app do banco (e, se quiser, o arquivo do extrato) e o portal aponta o
 * lançamento ou os itens que geram a diferença. Nada é alterado sem o seu clique; o arquivo é lido só no navegador.
 */
export function AnalisarFaturaModal({ aberto, onFechar, cartao, mes, ano }: Props) {
  const ds = useDataset();
  const notificar = useNotificar();
  const [valor, setValor] = useState("");
  const [extrato, setExtrato] = useState<{ nome: string; dados: ArquivoExtrato } | null>(null);
  const [erroArquivo, setErroArquivo] = useState<string | null>(null);
  const [lendo, setLendo] = useState(false);
  const [remover, setRemover] = useState<number | null>(null);

  const totalBanco = useMemo(() => {
    if (valor.trim() === "") return null;
    const v = parseValorBR(valor);
    return Number.isFinite(v) ? v : null;
  }, [valor]);

  const r = useMemo(
    () => (aberto && totalBanco !== null ? analisarFatura({ ds, cartaoId: cartao.id, mes, ano, totalBanco, extrato: extrato?.dados ?? null }) : null),
    [aberto, ds, cartao.id, mes, ano, totalBanco, extrato],
  );

  async function escolherArquivo(files: FileList | null) {
    const f = files?.[0];
    if (!f) return;
    setErroArquivo(null);
    setLendo(true);
    try {
      const lido = await lerArquivo(f);
      const leitura = interpretarTexto(lido.texto);
      if (leitura.estado === "PRONTO") setExtrato({ nome: f.name, dados: leitura.extrato });
      else if (leitura.estado === "REVISAR") setErroArquivo("Não reconheci as colunas deste CSV. Abra Conciliação, mapeie as colunas uma vez e envie de novo aqui.");
      else setErroArquivo(leitura.erro);
    } catch (e) {
      setErroArquivo(e instanceof Error ? e.message : "Não foi possível ler o arquivo.");
    } finally {
      setLendo(false);
    }
  }

  function executar(h: Hipotese) {
    const a = h.acao;
    if (!a) return;
    if (a.tipo === "REMOVER") return setRemover(a.id);
    const alvo = ds.despesas.find((d) => d.id === a.id);
    if (!alvo) return;
    // alternar só quando o estado atual é o oposto do desejado (evita desfazer sem querer)
    if ((a.tipo === "MARCAR_PAGO" && alvo.pago) || (a.tipo === "MARCAR_ABERTO" && !alvo.pago)) return notificar.info("O lançamento já está nesse estado.");
    const res = acoes.alternarPago(a.id);
    if (!res.ok) return notificar.erro(res.erro);
    notificar.sucesso(a.tipo === "MARCAR_PAGO" ? "Marcado como pago." : "Marcado como em aberto.");
  }

  function confirmarRemocao() {
    const id = remover;
    setRemover(null);
    if (id === null) return;
    const res = acoes.removerLancamento(id);
    if (!res.ok) return notificar.erro(res.erro);
    notificar.sucesso("Lançamento enviado para a lixeira (restaurável por 30 dias).");
  }

  const mesTxt = `${String(mes).padStart(2, "0")}/${ano}`;
  const e = r?.extrato ?? null;

  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo={`Analisar fatura ${mesTxt} · ${cartao.nome}`}>
      <div className="space-y-4 text-sm">
        <p className="text-muted">
          Informe o valor da fatura que aparece no <strong>app do banco</strong>. O portal compara com a fatura em aberto daqui e procura a causa da diferença: duplicidade, estorno com sinal trocado, pagamento dentro do
          cartão, item marcado como pago, compra na virada do fechamento, parcela ou IOF não lançados. Para uma análise linha a linha, anexe o extrato (OFX/CSV) da fatura. Nada é alterado sem o seu clique.
        </p>

        <div className="flex flex-wrap items-end gap-3">
          <InputValor rotulo="Valor da fatura no app do banco (R$)" value={valor} onChange={(ev) => setValor(ev.target.value)} wrapperClassName="min-w-[14rem] flex-1" />
          <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-line px-3 text-sm font-medium hover:bg-surface-2 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--ring)]">
            <FileSearch size={16} aria-hidden />
            {lendo ? "Lendo…" : extrato ? "Trocar extrato" : "Anexar extrato (opcional)"}
            <input
              type="file"
              accept=".ofx,.qfx,.csv,.txt"
              className="sr-only"
              aria-label="Anexar extrato da fatura (OFX ou CSV)"
              onChange={(ev) => {
                void escolherArquivo(ev.target.files);
                ev.target.value = "";
              }}
            />
          </label>
          {extrato && (
            <Button tamanho="sm" onClick={() => setExtrato(null)}>
              Remover {extrato.nome}
            </Button>
          )}
        </div>
        {erroArquivo && (
          <p role="alert" className="rounded-xl border border-neg/40 bg-neg/10 p-3">
            {erroArquivo}
          </p>
        )}
        <p className="text-xs text-muted">Privacidade: o arquivo é lido somente no seu navegador e não é gravado.</p>

        {!r && totalBanco === null && (
          <p className="flex items-center gap-2 text-muted">
            <Search size={16} aria-hidden /> Digite o valor da fatura para começar.
          </p>
        )}

        {r && (
          <>
            <div className={`rounded-xl px-3 py-2 ${r.situacao === "BATE" ? "bg-pos-soft" : "bg-warn-soft"}`} role="status">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-1">
                <dt className="text-muted">Fatura no banco</dt>
                <dd className="text-right font-semibold">
                  <Money valor={r.totalBanco} />
                </dd>
                <dt className="text-muted">Em aberto no app</dt>
                <dd className="text-right font-semibold">
                  <Money valor={r.emAberto} />
                </dd>
                {Math.abs(r.totalApp - r.emAberto) >= 0.005 && (
                  <>
                    <dt className="text-muted">Total da fatura no app (com itens já pagos)</dt>
                    <dd className="text-right">
                      <Money valor={r.totalApp} />
                    </dd>
                  </>
                )}
                <dt className="font-medium">Diferença (banco − app)</dt>
                <dd className="text-right font-semibold">{r.situacao === "BATE" ? "Bate" : <Money valor={r.diferenca} tom="auto" />}</dd>
              </dl>
              <p className="mt-1 flex items-start gap-2 text-xs">
                {r.situacao === "BATE" ? <CheckCircle2 size={14} className="mt-0.5 shrink-0 text-pos" aria-hidden /> : <AlertTriangle size={14} className="mt-0.5 shrink-0 text-warn" aria-hidden />}
                {r.resumo}
              </p>
            </div>

            {e && (
              <div className="rounded-xl border border-line p-3">
                <p className="font-medium">Extrato: {extrato?.nome}</p>
                <ul className="mt-1 space-y-0.5 text-xs text-muted">
                  <li>
                    {e.total} linha(s) de compra/estorno; {e.conciliadas} já casam com lançamentos do app.
                  </li>
                  <li>
                    Soma do extrato: <strong>{formatBRL(e.totalExtrato)}</strong>
                    {e.extratoBateBanco ? " (igual ao valor informado do banco)" : ` (${formatBRL(Math.abs(e.extratoMenosBanco))} ${e.extratoMenosBanco > 0 ? "acima" : "abaixo"} do valor informado: o arquivo pode ser parcial ou de outra fatura, ou o banco soma encargos que não estão no arquivo)`}
                  </li>
                  <li>
                    Só no extrato: {e.soNoExtrato.length} · só no app: {e.soNoApp.length} · valor diferente: {e.valorDiferente.length} · em outra fatura no app: {e.outraFatura.length}
                  </li>
                  <li>{e.restanteCentavos === 0 ? "As linhas acima explicam toda a diferença entre extrato e app." : `Sem explicação nas linhas: ${formatBRL(Math.abs(e.restanteCentavos) / 100)}.`}</li>
                </ul>
              </div>
            )}

            {r.situacao !== "BATE" && r.hipoteses.length === 0 && (
              <div className="rounded-xl border border-line p-3 text-xs text-muted">
                <p className="flex items-center gap-2 font-medium text-fg">
                  <Info size={14} aria-hidden /> Nenhum item isolado explica a diferença.
                </p>
                <ul className="mt-1 list-disc space-y-0.5 pl-5">
                  <li>Confira se o valor digitado é o TOTAL da fatura (e não o mínimo ou o valor com juros de parcelamento).</li>
                  <li>Compras recentes podem ainda não ter entrado na fatura do banco.</li>
                  <li>Anexe o extrato da fatura para ver linha a linha o que falta ou sobra.</li>
                  <li>
                    Se o arquivo vier de outro formato, use a <Link href="/conciliacao" className="underline">Conciliação</Link>.
                  </li>
                </ul>
              </div>
            )}

            {r.hipoteses.length > 0 && (
              <ol className="space-y-3" aria-label="Possíveis causas da diferença">
                {r.hipoteses.map((h, i) => (
                  <li key={h.chave} className="rounded-xl border border-line p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-xs text-muted">{i + 1}.</span>
                      <Badge tom={TOM[h.confianca]}>{ROTULO[h.confianca]}</Badge>
                      {h.explicaCentavos !== 0 && (
                        <span className="text-xs text-muted">
                          explica <Money valor={Math.abs(h.explicaCentavos) / 100} />
                        </span>
                      )}
                    </div>
                    <p className="mt-1 font-medium">{h.titulo}</p>
                    <p className="mt-0.5 text-xs text-muted">{h.detalhe}</p>
                    {h.lancamentoIds.length > 0 && <Apontados ids={h.lancamentoIds} />}
                    {h.acao && (
                      <div className="mt-2">
                        <Button tamanho="sm" onClick={() => executar(h)}>
                          {h.acao.rotulo}
                        </Button>
                      </div>
                    )}
                  </li>
                ))}
              </ol>
            )}
          </>
        )}

        <div className="flex justify-end">
          <Button onClick={onFechar}>Fechar</Button>
        </div>
      </div>

      <Confirmar
        aberto={remover !== null}
        titulo="Remover lançamento"
        rotuloConfirmar="Remover"
        perigo
        mensagem="O lançamento vai para a lixeira por 30 dias e pode ser restaurado. Deseja remover?"
        onConfirmar={confirmarRemocao}
        onCancelar={() => setRemover(null)}
      />
    </Modal>
  );
}

/** Aponta os lançamentos envolvidos (até 6). */
function Apontados({ ids }: { ids: number[] }) {
  const ds = useDataset();
  const itens = ids
    .slice(0, 6)
    .map((id) => ds.despesas.find((d) => d.id === id))
    .filter((d): d is NonNullable<typeof d> => !!d);
  return (
    <ul className="mt-2 space-y-0.5 rounded-lg bg-surface-2 p-2 text-xs">
      {itens.map((d) => (
        <li key={d.id} className="flex justify-between gap-2">
          <span className="min-w-0 truncate">
            {new Date(d.data).toLocaleDateString("pt-BR")} · {d.descricao}
            {d.pago ? " (pago)" : ""}
          </span>
          <span className="shrink-0 tabular-nums">
            {d.tipo === "CREDITO" ? "−" : ""}
            {formatBRL(d.valor)}
          </span>
        </li>
      ))}
      {ids.length > itens.length && <li className="text-muted">… e mais {ids.length - itens.length}</li>}
    </ul>
  );
}
