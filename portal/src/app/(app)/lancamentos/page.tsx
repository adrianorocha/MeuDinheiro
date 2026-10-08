"use client";

import { categoriasDisponiveis } from "@/lib/catalogo";
import { Download, Plus, Receipt, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { LancamentoModal } from "@/components/lancamentos/LancamentoModal";
import { ListaLancamentos } from "@/components/lancamentos/ListaLancamentos";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { Card, EmptyState, PageHeader } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { cartaoIdDe, normalizaNome } from "@/lib/finance/calc";
import { MESES_NOME, mesAnoDe } from "@/lib/finance/dates";
import { toCents, fromCents } from "@/lib/finance/money";
import { lancamentosParaCsv } from "@/lib/csv";
import { baixarArquivo } from "@/lib/download";
import { useAgora, useDataset } from "@/lib/hooks";

const PAGINA = 100;

export default function LancamentosPage() {
  const ds = useDataset();
  const agora = useAgora();
  const atual = mesAnoDe(agora);
  const [mes, setMes] = useState<string>(String(atual.mes));
  const [ano, setAno] = useState<string>(String(atual.ano));
  const [conta, setConta] = useState("");
  const [categoria, setCategoria] = useState("");
  const [tipo, setTipo] = useState("");
  const [status, setStatus] = useState("");
  const [autor, setAutor] = useState("");
  const [conciliacao, setConciliacao] = useState("");
  const [busca, setBusca] = useState("");
  const [limite, setLimite] = useState(PAGINA);
  const [novo, setNovo] = useState(false);

  const anos = useMemo(() => {
    const set = new Set<number>([atual.ano]);
    for (const d of ds.despesas) set.add(d.ano);
    return [...set].sort((a, b) => b - a);
  }, [ds.despesas, atual.ano]);

  const categorias = useMemo(() => {
    const nomes = new Map<string, string>();
    for (const c of categoriasDisponiveis(ds.categorias)) nomes.set(normalizaNome(c.nome), c.nome);
    for (const d of ds.despesas) if (d.categoria) nomes.set(normalizaNome(d.categoria), nomes.get(normalizaNome(d.categoria)) ?? d.categoria);
    return [...nomes.values()].sort((a, b) => a.localeCompare(b, "pt-BR"));
  }, [ds.categorias, ds.despesas]);

  const filtrados = useMemo(() => {
    const termo = normalizaNome(busca);
    return ds.despesas
      .filter((d) => {
        const m = mesAnoDe(d.data);
        if (ano && String(m.ano) !== ano) return false;
        if (mes && String(m.mes) !== mes) return false;
        if (conta) {
          if (conta.startsWith("c:")) {
            if (String(cartaoIdDe(d)) !== conta.slice(2)) return false;
          } else if (d.conta !== conta || cartaoIdDe(d) !== null) return false;
        }
        if (categoria && normalizaNome(d.categoria) !== normalizaNome(categoria)) return false;
        if (tipo && d.tipo !== tipo) return false;
        if (status === "pago" && !d.pago) return false;
        if (status === "pendente" && d.pago) return false;
        if (autor && (d.autor ?? "") !== autor) return false;
        if (conciliacao === "sim" && d.conciliadoEm == null) return false;
        if (conciliacao === "nao" && d.conciliadoEm != null) return false;
        if (termo && !normalizaNome(`${d.descricao} ${d.categoria}`).includes(termo)) return false;
        return true;
      })
      .sort((a, b) => b.data - a.data || b.id - a.id);
  }, [ds.despesas, mes, ano, conta, categoria, tipo, status, autor, conciliacao, busca]);

  const autores = useMemo(() => [...new Set(ds.despesas.map((d) => d.autor).filter((a): a is string => !!a))].sort(), [ds.despesas]);

  const totais = useMemo(() => {
    let entradas = 0;
    let saidas = 0;
    for (const d of filtrados) {
      if (d.tipo === "CREDITO") entradas += toCents(d.valor);
      else saidas += toCents(d.valor);
    }
    return { entradas: fromCents(entradas), saidas: fromCents(saidas), saldo: fromCents(entradas - saidas) };
  }, [filtrados]);

  const visiveis = filtrados.slice(0, limite);
  const nomeCartao = (id: number) => ds.cartoes.find((c) => c.id === id)?.nome ?? String(id);

  return (
    <>
      <PageHeader
        titulo="Lançamentos"
        descricao="Extrato completo com filtros"
        acoes={
          <>
            <Button
              icone={<Download size={16} aria-hidden />}
              disabled={filtrados.length === 0}
              onClick={() => baixarArquivo("lancamentos.csv", lancamentosParaCsv(filtrados, nomeCartao), "text/csv;charset=utf-8")}
            >
              Exportar CSV
            </Button>
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setNovo(true)}>
              Novo lançamento
            </Button>
          </>
        }
      />

      <Card className="mb-4" aria-label="Filtros">
        <form className="grid grid-cols-2 gap-3 lg:grid-cols-4" role="search" onSubmit={(e) => e.preventDefault()}>
          <Select rotulo="Mês" value={mes} onChange={(e) => { setMes(e.target.value); setLimite(PAGINA); }}>
            <option value="">Todos</option>
            {MESES_NOME.map((n, i) => (
              <option key={n} value={i + 1}>
                {n}
              </option>
            ))}
          </Select>
          <Select rotulo="Ano" value={ano} onChange={(e) => { setAno(e.target.value); setLimite(PAGINA); }}>
            <option value="">Todos</option>
            {anos.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </Select>
          <Select rotulo="Conta / cartão" value={conta} onChange={(e) => { setConta(e.target.value); setLimite(PAGINA); }}>
            <option value="">Todas</option>
            {ds.contas.map((c) => (
              <option key={c.id} value={c.conta}>
                {c.banco} · {c.conta}
              </option>
            ))}
            {ds.cartoes.map((c) => (
              <option key={`c${c.id}`} value={`c:${c.id}`}>
                Cartão {c.nome}
              </option>
            ))}
          </Select>
          <Select rotulo="Categoria" value={categoria} onChange={(e) => { setCategoria(e.target.value); setLimite(PAGINA); }}>
            <option value="">Todas</option>
            {categorias.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </Select>
          <Select rotulo="Tipo" value={tipo} onChange={(e) => { setTipo(e.target.value); setLimite(PAGINA); }}>
            <option value="">Receitas e despesas</option>
            <option value="CREDITO">Receitas</option>
            <option value="DEBITO">Despesas</option>
          </Select>
          <Select rotulo="Situação" value={status} onChange={(e) => { setStatus(e.target.value); setLimite(PAGINA); }}>
            <option value="">Pagos e pendentes</option>
            <option value="pago">Pagos</option>
            <option value="pendente">Pendentes</option>
          </Select>
          <Select rotulo="Conciliação" value={conciliacao} onChange={(e) => { setConciliacao(e.target.value); setLimite(PAGINA); }}>
            <option value="">Todos</option>
            <option value="sim">Conciliados</option>
            <option value="nao">Não conciliados</option>
          </Select>
          {autores.length > 0 && (
            <Select rotulo="Quem lançou" value={autor} onChange={(e) => { setAutor(e.target.value); setLimite(PAGINA); }}>
              <option value="">Todos</option>
              {autores.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </Select>
          )}
          <div className="sm:col-span-2">
            <Input
              rotulo="Buscar"
              type="search"
              value={busca}
              placeholder="Descrição ou categoria"
              onChange={(e) => { setBusca(e.target.value); setLimite(PAGINA); }}
              autoComplete="off"
            />
          </div>
        </form>
      </Card>

      <div className="mb-3 grid grid-cols-3 gap-3 text-sm">
        <Card className="!p-3">
          <p className="text-xs text-muted">Entradas</p>
          <Money valor={totais.entradas} className="font-semibold" />
        </Card>
        <Card className="!p-3">
          <p className="text-xs text-muted">Saídas</p>
          <Money valor={totais.saidas} className="font-semibold" />
        </Card>
        <Card className="!p-3">
          <p className="text-xs text-muted">Saldo do filtro</p>
          <Money valor={totais.saldo} tom="auto" className="font-semibold" />
        </Card>
      </div>

      {filtrados.length === 0 ? (
        <EmptyState
          titulo="Nenhum lançamento encontrado"
          descricao="Ajuste os filtros ou crie um novo lançamento."
          icone={<Search size={32} />}
          acao={
            <Button variante="primary" icone={<Receipt size={16} aria-hidden />} onClick={() => setNovo(true)}>
              Novo lançamento
            </Button>
          }
        />
      ) : (
        <Card>
          <p className="mb-1 text-xs text-muted" aria-live="polite">
            {filtrados.length} lançamento(s)
          </p>
          <ListaLancamentos itens={visiveis} />
          {filtrados.length > visiveis.length && (
            <div className="mt-3 text-center">
              <Button onClick={() => setLimite((l) => l + PAGINA)}>Mostrar mais</Button>
            </div>
          )}
        </Card>
      )}

      <LancamentoModal aberto={novo} onFechar={() => setNovo(false)} />
    </>
  );
}
