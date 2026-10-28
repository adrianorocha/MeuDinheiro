"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Segmentado, Select } from "@/components/ui/Field";
import { Badge, ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { brlCentavos } from "@/lib/finance/centavos";
import { fromCents, toCents } from "@/lib/finance/money";
import { comprometidoAReceber } from "@/lib/finance/rateios";
import type { Despesa, TipoAcerto } from "@/lib/finance/types";
import { deInputData, formatData, parseValorBR, valorParaCampo } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const PAGINA = 50;
const DIA_MS = 24 * 60 * 60 * 1000;
const normaliza = (t: string) => t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

/**
 * R68 - "Vincular lançamentos": liga lançamentos JÁ EXISTENTES a uma pessoa. Só cria acertos (controle de quanto a pessoa te deve /
 * você deve); não altera lançamentos, saldo, cartão nem relatórios.
 */
export function VincularModal({ aberto, pessoaId, onFechar }: { aberto: boolean; pessoaId?: number; onFechar: () => void }) {
  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo="Vincular lançamentos">
      {aberto && <Conteudo pessoaId={pessoaId} onFechar={onFechar} />}
    </Modal>
  );
}

function Conteudo({ pessoaId, onFechar }: { pessoaId?: number; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [pessoa, setPessoa] = useState(String(pessoaId ?? ds.pessoas[0]?.id ?? ""));
  const [tipo, setTipo] = useState<TipoAcerto>("A_RECEBER");
  const [texto, setTexto] = useState("");
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [conta, setConta] = useState("");
  const [categoria, setCategoria] = useState("");
  const [tag, setTag] = useState("");
  const [semVinculo, setSemVinculo] = useState(true);
  const [limite, setLimite] = useState(PAGINA);
  const [marcados, setMarcados] = useState<Set<number>>(new Set());
  const [valores, setValores] = useState<Record<number, string>>({});
  const [erro, setErro] = useState<string | null>(null);

  const recebe = tipo === "A_RECEBER";
  const pid = Number(pessoa);
  const nomePessoa = ds.pessoas.find((p) => p.id === pid)?.nome ?? "a pessoa";

  /** Já vinculado (centavos) no tipo escolhido, qualquer pessoa. */
  const vinculado = useMemo(() => {
    const m = new Map<number, number>();
    for (const a of ds.acertos) {
      if (a.despesaId == null || a.tipo !== tipo) continue;
      m.set(a.despesaId, (m.get(a.despesaId) ?? 0) + toCents(a.valor));
    }
    return m;
  }, [ds.acertos, tipo]);
  const jaDestaPessoa = useMemo(() => new Set(ds.acertos.filter((a) => a.pessoaId === pid && a.tipo === tipo && a.despesaId != null).map((a) => a.despesaId as number)), [ds.acertos, pid, tipo]);
  const temAcerto = useMemo(() => new Set(ds.acertos.filter((a) => a.despesaId != null).map((a) => a.despesaId as number)), [ds.acertos]);

  const categorias = useMemo(() => [...new Set(ds.despesas.map((d) => d.categoria))].sort((a, b) => a.localeCompare(b, "pt-BR")), [ds.despesas]);
  const tags = useMemo(() => [...new Set(ds.despesas.flatMap((d) => d.tags ?? []))].sort((a, b) => a.localeCompare(b, "pt-BR")), [ds.despesas]);

  const filtrados = useMemo(() => {
    const q = normaliza(texto.trim());
    const ini = de ? deInputData(de) : NaN;
    const fim = ate ? deInputData(ate) + DIA_MS - 1 : NaN;
    return ds.despesas
      .filter((d) => d.natureza === "NORMAL" && !(recebe && d.tipo === "CREDITO"))
      .filter((d) => (q ? normaliza(`${d.descricao} ${d.categoria}`).includes(q) : true))
      .filter((d) => (Number.isNaN(ini) ? true : d.data >= ini) && (Number.isNaN(fim) ? true : d.data <= fim))
      .filter((d) => (conta ? d.conta === conta : true))
      .filter((d) => (categoria ? d.categoria === categoria : true))
      .filter((d) => (tag ? (d.tags ?? []).includes(tag) : true))
      .filter((d) => (semVinculo ? !temAcerto.has(d.id) : true))
      .sort((a, b) => b.data - a.data || b.id - a.id);
  }, [ds.despesas, texto, de, ate, conta, categoria, tag, semVinculo, recebe, temAcerto]);

  /** O que ainda pode ser vinculado a este lançamento (centavos): A_RECEBER respeita o teto; A_PAGAR vai até o valor. */
  const restante = (d: Despesa) => (recebe ? toCents(d.valor) - comprometidoAReceber(ds.acertos, d.id) : toCents(d.valor));
  const centavosDoItem = (d: Despesa): number => {
    const t = valores[d.id];
    if (t === undefined || t.trim() === "") return Math.max(restante(d), 0);
    const v = parseValorBR(t);
    return Number.isFinite(v) ? toCents(v) : NaN;
  };
  const bloqueado = (d: Despesa) => jaDestaPessoa.has(d.id) || restante(d) <= 0;

  const selecionados = ds.despesas.filter((d) => marcados.has(d.id));
  const total = selecionados.reduce((s, d) => s + (centavosDoItem(d) || 0), 0);

  function alternar(id: number) {
    setErro(null);
    setMarcados((m) => {
      const n = new Set(m);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  function trocarTipo(t: TipoAcerto) {
    setTipo(t);
    setMarcados(new Set());
    setValores({});
    setErro(null);
  }

  function confirmar() {
    if (!Number.isInteger(pid) || !ds.pessoas.some((p) => p.id === pid)) return setErro("Escolha a pessoa.");
    if (selecionados.length === 0) return setErro("Marque ao menos um lançamento.");
    const itens = [];
    for (const d of selecionados) {
      const c = centavosDoItem(d);
      if (!Number.isFinite(c) || c <= 0) return setErro(`“${d.descricao}”: informe um valor maior que zero.`);
      itens.push({ despesaId: d.id, valor: fromCents(c) });
    }
    const r = acoes.vincularLancamentos({ pessoaId: pid, tipo, itens });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", `${itens.length === 1 ? "1 lançamento vinculado" : `${itens.length} lançamentos vinculados`} a ${nomePessoa} (${brlCentavos(total)}). Saldo e lançamentos não mudaram.`);
    onFechar();
  }

  if (ds.pessoas.length === 0) return <p className="text-sm text-muted">Cadastre uma pessoa antes de vincular lançamentos.</p>;

  return (
    <div className="flex flex-col gap-3 text-sm">
      <p role="note" className="rounded-lg border border-line bg-surface-2 p-2.5 text-xs">
        Isto não altera saldo nem os lançamentos — só cria o controle de quanto a pessoa te deve/você deve.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Select rotulo="Pessoa" value={pessoa} onChange={(e) => { setPessoa(e.target.value); setErro(null); }}>
          {ds.pessoas.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nome}
            </option>
          ))}
        </Select>
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Tipo</span>
          <Segmentado
            rotulo="Tipo de vínculo"
            valor={tipo}
            opcoes={[{ valor: "A_RECEBER", rotulo: "Ela me deve" }, { valor: "A_PAGAR", rotulo: "Eu devo a ela" }]}
            onChange={trocarTipo}
          />
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Input rotulo="Buscar" value={texto} onChange={(e) => { setTexto(e.target.value); setLimite(PAGINA); }} placeholder="descrição ou categoria" wrapperClassName="sm:col-span-3" />
        <Input rotulo="De" type="date" value={de} onChange={(e) => { setDe(e.target.value); setLimite(PAGINA); }} />
        <Input rotulo="Até" type="date" value={ate} onChange={(e) => { setAte(e.target.value); setLimite(PAGINA); }} />
        <Select rotulo="Conta" value={conta} onChange={(e) => { setConta(e.target.value); setLimite(PAGINA); }}>
          <option value="">Todas</option>
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
        <Select rotulo="Categoria" value={categoria} onChange={(e) => { setCategoria(e.target.value); setLimite(PAGINA); }}>
          <option value="">Todas</option>
          {categorias.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </Select>
        {tags.length > 0 && (
          <Select rotulo="Tag" value={tag} onChange={(e) => { setTag(e.target.value); setLimite(PAGINA); }}>
            <option value="">Todas</option>
            {tags.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
        )}
      </div>
      <Checkbox rotulo="Apenas lançamentos ainda sem vínculo" checked={semVinculo} onChange={(e) => { setSemVinculo(e.target.checked); setLimite(PAGINA); }} />

      <p className="text-xs text-muted" aria-live="polite">
        {filtrados.length} lançamento(s) {filtrados.length > limite ? `· mostrando ${limite}` : ""}
      </p>
      <ul className="divide-y divide-line rounded-lg border border-line" aria-label="Lançamentos existentes">
        {filtrados.slice(0, limite).map((d) => {
          const marcado = marcados.has(d.id);
          const trava = bloqueado(d);
          const ja = vinculado.get(d.id) ?? 0;
          const rId = `vinc-${d.id}`;
          return (
            <li key={d.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
              <input
                id={rId}
                type="checkbox"
                className="size-4 accent-[var(--primary)]"
                checked={marcado}
                disabled={trava}
                onChange={() => alternar(d.id)}
                aria-label={`Vincular ${d.descricao}`}
              />
              <label htmlFor={rId} className="min-w-0 flex-1 basis-40">
                <span className="block truncate font-medium">{d.descricao}</span>
                <span className="block text-xs text-muted">
                  {formatData(d.data)} · {d.categoria} · {d.tipo === "CREDITO" ? "receita" : "despesa"} {brlCentavos(toCents(d.valor))}
                </span>
              </label>
              <span className="text-xs text-muted tabular-nums" title="Já vinculado a pessoas neste tipo">
                já vinculado {brlCentavos(ja)}
              </span>
              {jaDestaPessoa.has(d.id) ? (
                <Badge tom="warn">já vinculado a {nomePessoa}</Badge>
              ) : restante(d) <= 0 ? (
                <Badge tom="warn">sem saldo a vincular</Badge>
              ) : (
                marcado && (
                  <input
                    aria-label={`Valor a vincular de ${d.descricao}`}
                    inputMode="decimal"
                    autoComplete="off"
                    className="h-9 w-28 rounded-lg border border-line bg-surface px-2 text-right text-sm tabular-nums"
                    value={valores[d.id] ?? valorParaCampo(fromCents(Math.max(restante(d), 0)))}
                    onChange={(e) => { setErro(null); setValores((v) => ({ ...v, [d.id]: e.target.value })); }}
                  />
                )
              )}
            </li>
          );
        })}
        {filtrados.length === 0 && <li className="px-3 py-4 text-center text-muted">Nenhum lançamento encontrado.</li>}
      </ul>
      {filtrados.length > limite && (
        <Button tamanho="sm" onClick={() => setLimite((l) => l + PAGINA)}>
          Mostrar mais {PAGINA}
        </Button>
      )}

      <p className="rounded-lg bg-surface-2 p-2.5">
        {selecionados.length === 0 ? (
          "Nenhum lançamento selecionado."
        ) : (
          <>
            <strong>{selecionados.length}</strong> selecionado(s) · total <strong>{brlCentavos(total)}</strong> —{" "}
            {recebe ? `${nomePessoa} passa a te dever` : `você passa a dever a ${nomePessoa}`} esse valor.
          </>
        )}
      </p>
      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar}>Cancelar</Button>
        <Button variante="primary" onClick={confirmar} disabled={selecionados.length === 0}>
          {selecionados.length === 0 ? "Vincular" : `Vincular ${selecionados.length} (${brlCentavos(total)})`}
        </Button>
      </div>
    </div>
  );
}
