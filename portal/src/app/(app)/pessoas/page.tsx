"use client";

import { Copy, HandCoins, MessageCircle, Pencil, Plus, Trash2, Users } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Checkbox, Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { brlCentavos } from "@/lib/finance/centavos";
import { CATEGORIA_ACERTOS, CATEGORIA_EMPRESTIMOS, CATEGORIA_REEMBOLSO, resumoRateios, saldosPorPessoa, textoCobranca } from "@/lib/finance/rateios";
import { fromCents } from "@/lib/finance/money";
import type { Acerto, Pessoa, TipoAcerto } from "@/lib/finance/types";
import { deInputData, formatData, paraInputData, parseValorBR } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

function PessoaForm({ editar, onFechar }: { editar: Pessoa | null; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [contato, setContato] = useState(editar?.contato ?? "");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const r = acoes.salvarPessoa({ nome, contato: contato || null }, editar?.id);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Pessoa salva.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Nome" value={nome} onChange={(e) => setNome(e.target.value)} maxLength={60} required />
      <Input rotulo="Contato (opcional)" value={contato} onChange={(e) => setContato(e.target.value)} maxLength={80} dica="Telefone, e-mail… só para você lembrar" />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

/** "Emprestei" (A_RECEBER) ou "Devo" (A_PAGAR): acerto avulso, com lançamento na conta opcional. */
function AvulsoForm({ tipo, pessoaId, onFechar }: { tipo: TipoAcerto; pessoaId?: number; onFechar: () => void }) {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const [pessoa, setPessoa] = useState(String(pessoaId ?? ds.pessoas[0]?.id ?? ""));
  const [valor, setValor] = useState("");
  const [descricao, setDescricao] = useState("");
  const [data, setData] = useState(paraInputData(agora));
  const [lancar, setLancar] = useState(false);
  const [conta, setConta] = useState(ds.contas[0]?.conta ?? "");
  const [erro, setErro] = useState<string | null>(null);
  const recebe = tipo === "A_RECEBER";

  function enviar(e: FormEvent) {
    e.preventDefault();
    const v = parseValorBR(valor);
    if (!Number.isFinite(v)) return setErro("Informe o valor.");
    const d = deInputData(data);
    if (Number.isNaN(d)) return setErro("Informe uma data válida.");
    const r = acoes.criarAcertoAvulso({
      pessoaId: Number(pessoa),
      tipo,
      valor: v,
      descricao,
      data: d,
      lancamento: lancar ? { conta, categoria: CATEGORIA_EMPRESTIMOS } : null,
    });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", recebe ? "Empréstimo registrado." : "Dívida registrada.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Select rotulo="Pessoa" value={pessoa} onChange={(e) => setPessoa(e.target.value)} required>
        {ds.pessoas.map((p) => (
          <option key={p.id} value={p.id}>
            {p.nome}
          </option>
        ))}
      </Select>
      <div className="grid grid-cols-2 gap-3">
        <InputValor rotulo="Valor" value={valor} onChange={(e) => setValor(e.target.value)} required />
        <Input rotulo="Data" type="date" value={data} onChange={(e) => setData(e.target.value)} />
      </div>
      <Input rotulo="Descrição (opcional)" value={descricao} onChange={(e) => setDescricao(e.target.value)} maxLength={80} />
      <Checkbox rotulo={recebe ? "Gerar a saída na conta (o dinheiro saiu da minha conta)" : "Gerar a entrada na conta (o dinheiro entrou na minha conta)"} checked={lancar} onChange={(e) => setLancar(e.target.checked)} />
      {lancar && (
        <Select rotulo="Conta" value={conta} onChange={(e) => setConta(e.target.value)}>
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
      )}
      {lancar && <p className="text-xs text-muted">Cria um lançamento “{CATEGORIA_EMPRESTIMOS}” já {recebe ? "pago (saída)" : "efetivado (entrada)"}; ele entra nos relatórios como qualquer lançamento comum.</p>}
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Registrar" />
    </form>
  );
}

/** Receber/pagar o acerto: pede confirmação e, se marcado, cria o lançamento já pago para o saldo bater com o banco. */
function QuitarModal({ acerto, onFechar }: { acerto: Acerto | null; onFechar: () => void }) {
  return (
    <Modal aberto={acerto !== null} onFechar={onFechar} titulo={acerto?.tipo === "A_RECEBER" ? "Receber acerto" : "Pagar acerto"}>
      {acerto && <QuitarConteudo acerto={acerto} onFechar={onFechar} />}
    </Modal>
  );
}

function QuitarConteudo({ acerto, onFechar }: { acerto: Acerto; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const recebe = acerto.tipo === "A_RECEBER";
  const [lancar, setLancar] = useState(ds.contas.length > 0);
  const [conta, setConta] = useState(ds.contas[0]?.conta ?? "");
  const [categoria, setCategoria] = useState(recebe ? CATEGORIA_REEMBOLSO : CATEGORIA_ACERTOS);
  const [erro, setErro] = useState<string | null>(null);
  const nome = ds.pessoas.find((p) => p.id === acerto.pessoaId)?.nome ?? "pessoa";

  function confirmar() {
    const r = acoes.quitarAcerto({ acertoId: acerto.id, lancamento: lancar ? { conta, categoria } : null });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", recebe ? "Acerto recebido." : "Acerto pago.");
    onFechar();
  }

  return (
    <div className="flex flex-col gap-4 text-sm">
      <p>
        {recebe ? `Confirmar que ${nome} pagou` : `Confirmar que você pagou a ${nome}`} <strong>{brlCentavos(Math.round(acerto.valor * 100))}</strong> ({acerto.descricao})?
      </p>
      <Checkbox rotulo={recebe ? "Criar a receita na conta (para o saldo bater com o banco)" : "Criar a despesa na conta (para o saldo bater com o banco)"} checked={lancar} onChange={(e) => setLancar(e.target.checked)} />
      {lancar && (
        <div className="grid grid-cols-2 gap-3">
          <Select rotulo="Conta" value={conta} onChange={(e) => setConta(e.target.value)}>
            {ds.contas.map((c) => (
              <option key={c.id} value={c.conta}>
                {c.banco} · {c.conta}
              </option>
            ))}
          </Select>
          <Input rotulo="Categoria" value={categoria} onChange={(e) => setCategoria(e.target.value)} />
        </div>
      )}
      {lancar && <p className="text-xs text-muted">O lançamento entra já {recebe ? "recebido (receita)" : "pago (despesa)"} e aparece nos relatórios.</p>}
      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar}>Cancelar</Button>
        <Button variante="primary" onClick={confirmar} disabled={lancar && !conta}>
          {recebe ? "Confirmar recebimento" : "Confirmar pagamento"}
        </Button>
      </div>
    </div>
  );
}

/** Texto pronto para WhatsApp/copiar; a chave PIX só entra se você digitar (nunca é inventada). */
function CobrarModal({ pessoa, acertos, onFechar }: { pessoa: Pessoa | null; acertos: Acerto[]; onFechar: () => void }) {
  return (
    <Modal aberto={pessoa !== null} onFechar={onFechar} titulo={`Cobrar ${pessoa?.nome ?? ""}`}>
      {pessoa && <CobrarConteudo pessoa={pessoa} acertos={acertos} onFechar={onFechar} />}
    </Modal>
  );
}

function CobrarConteudo({ pessoa, acertos, onFechar }: { pessoa: Pessoa; acertos: Acerto[]; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [pix, setPix] = useState("");
  const texto = textoCobranca(pessoa.nome, acertos, pix);

  async function copiar() {
    if (!texto) return;
    try {
      await navigator.clipboard.writeText(texto);
      avisar("sucesso", "Texto copiado.");
    } catch {
      avisar("erro", "Não foi possível copiar. Selecione o texto e copie manualmente.");
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <Input rotulo="Chave PIX (opcional)" value={pix} onChange={(e) => setPix(e.target.value)} dica="Digite a sua chave; ela só entra no texto se você preencher." autoComplete="off" />
      <label className="flex flex-col gap-1.5 text-sm font-medium">
        Mensagem
        <textarea readOnly value={texto ?? "Nada pendente para cobrar."} rows={9} className="w-full rounded-lg border border-line bg-surface p-3 font-mono text-sm font-normal" />
      </label>
      <div className="flex flex-wrap justify-end gap-2">
        <Button onClick={onFechar}>Fechar</Button>
        <Button icone={<Copy size={16} aria-hidden />} onClick={copiar} disabled={!texto}>
          Copiar
        </Button>
        {texto && (
          <a
            href={`https://wa.me/?text=${encodeURIComponent(texto)}`}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-medium text-primary-fg hover:bg-primary-hover"
          >
            <MessageCircle size={16} aria-hidden /> WhatsApp
          </a>
        )}
      </div>
    </div>
  );
}

export default function PessoasPage() {
  const ds = useDataset();
  const agora = useAgora();
  const [formPessoa, setFormPessoa] = useState<{ editar: Pessoa | null } | null>(null);
  const [avulso, setAvulso] = useState<{ tipo: TipoAcerto; pessoaId?: number } | null>(null);
  const [quitando, setQuitando] = useState<Acerto | null>(null);
  const [cobrando, setCobrando] = useState<Pessoa | null>(null);
  const [excluirPessoa, setExcluirPessoa] = useState<Pessoa | null>(null);
  const [excluirAcerto, setExcluirAcerto] = useState<Acerto | null>(null);
  const [periodo, setPeriodo] = useState<"MES" | "TUDO">("MES");
  const [quitados, setQuitados] = useState<Set<number>>(new Set());
  const avisar = useStore((s) => s.avisar);

  const saldos = useMemo(
    () => saldosPorPessoa(ds.pessoas, ds.acertos).sort((a, b) => Math.abs(b.liquido) - Math.abs(a.liquido) || a.pessoa.nome.localeCompare(b.pessoa.nome, "pt-BR")),
    [ds.pessoas, ds.acertos],
  );
  const resumo = useMemo(() => {
    const d = new Date(agora);
    const intervalo = periodo === "MES" ? { inicio: new Date(d.getFullYear(), d.getMonth(), 1).getTime(), fim: new Date(d.getFullYear(), d.getMonth() + 1, 0, 23, 59, 59, 999).getTime() } : undefined;
    return resumoRateios(ds.despesas, ds.acertos, intervalo);
  }, [ds.despesas, ds.acertos, periodo, agora]);
  const despesaExiste = (id: number | null | undefined) => id != null && ds.despesas.some((d) => d.id === id);
  const totalReceber = saldos.reduce((s, x) => s + x.aReceber, 0);
  const totalPagar = saldos.reduce((s, x) => s + x.aPagar, 0);
  const semPessoas = ds.pessoas.length === 0;

  function linha(a: Acerto) {
    const orfao = a.despesaId != null && !despesaExiste(a.despesaId);
    return (
      <li key={a.id} className="flex items-center gap-3 py-2 text-sm">
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{a.descricao}</p>
          <p className="text-xs text-muted">
            {formatData(a.data)}
            {a.quitado && a.quitadoEm != null ? ` · quitado em ${formatData(a.quitadoEm)}` : ""}
          </p>
          <div className="mt-0.5 flex flex-wrap gap-1">
            <Badge tom={a.tipo === "A_RECEBER" ? "pos" : "warn"}>{a.tipo === "A_RECEBER" ? "a receber" : "a pagar"}</Badge>
            {a.grupoRateio && <Badge tom="primary">rateio</Badge>}
            {orfao && <Badge tom="warn">lançamento removido</Badge>}
          </div>
        </div>
        <Money valor={a.valor} className="font-semibold" />
        {!a.quitado && (
          <Button tamanho="sm" onClick={() => setQuitando(a)}>
            {a.tipo === "A_RECEBER" ? "Receber" : "Pagar"}
          </Button>
        )}
        <IconButton rotulo={`Excluir acerto ${a.descricao}`} onClick={() => setExcluirAcerto(a)}>
          <Trash2 size={16} aria-hidden />
        </IconButton>
      </li>
    );
  }

  return (
    <>
      <PageHeader
        titulo="Pessoas"
        descricao="Rateios, empréstimos e acertos: quem te deve e quem você deve"
        acoes={
          <span className="flex flex-wrap gap-2">
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setFormPessoa({ editar: null })}>
              Nova pessoa
            </Button>
            <Button icone={<HandCoins size={16} aria-hidden />} disabled={semPessoas} onClick={() => setAvulso({ tipo: "A_RECEBER" })}>
              Emprestei
            </Button>
            <Button disabled={semPessoas} onClick={() => setAvulso({ tipo: "A_PAGAR" })}>
              Devo
            </Button>
          </span>
        }
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <Card>
          <p className="text-xs uppercase text-muted">A receber (pendente)</p>
          <p className="text-xl font-semibold"><Money valor={fromCents(totalReceber)} tom="auto" /></p>
        </Card>
        <Card>
          <p className="text-xs uppercase text-muted">A pagar (pendente)</p>
          <p className="text-xl font-semibold"><Money valor={fromCents(-totalPagar)} tom="auto" /></p>
        </Card>
      </div>

      <Card aria-labelledby="t-liq" className="mb-4">
        <CardTitulo
          id="t-liq"
          acao={
            <Segmentado
              rotulo="Período"
              valor={periodo}
              opcoes={[{ valor: "MES", rotulo: "Este mês" }, { valor: "TUDO", rotulo: "Tudo" }]}
              onChange={setPeriodo}
            />
          }
        >
          Quanto você realmente gastou
        </CardTitulo>
        <p className="mb-3 text-xs text-muted">
          Só nos lançamentos divididos. O lançamento e os relatórios continuam com o valor total que saiu da conta; aqui o líquido é o total menos o que as pessoas devem a você.
        </p>
        {resumo.itens.length === 0 ? (
          <p className="text-sm text-muted">Nenhuma despesa dividida {periodo === "MES" ? "neste mês" : "ainda"}. Use o botão “Dividir” na lista de lançamentos.</p>
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-3">
              <div>
                <p className="text-xs text-muted">Total das despesas divididas</p>
                <p className="text-lg font-semibold"><Money valor={fromCents(resumo.totalRateado)} /></p>
              </div>
              <div>
                <p className="text-xs text-muted">A receber de terceiros</p>
                <p className="text-lg font-semibold"><Money valor={fromCents(resumo.aReceberTerceiros)} /></p>
              </div>
              <div>
                <p className="text-xs text-muted">Você realmente gastou</p>
                <p className="text-lg font-semibold"><Money valor={fromCents(resumo.liquido)} /></p>
              </div>
            </div>
            <ul className="mt-3 divide-y divide-line text-sm">
              {resumo.itens.map((i) => (
                <li key={i.despesaId} className="flex items-center justify-between gap-2 py-1.5">
                  <span className="min-w-0 truncate">
                    {formatData(i.data)} · {i.descricao} {i.orfao && <Badge tom="warn">lançamento removido</Badge>}
                  </span>
                  <span className="shrink-0 tabular-nums">
                    {i.orfao ? <>a receber <Money valor={fromCents(i.aReceber)} /></> : <>{brlCentavos(i.total)} → <strong>{brlCentavos(i.liquido)}</strong></>}
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

      {semPessoas ? (
        <EmptyState
          titulo="Nenhuma pessoa cadastrada"
          descricao="Cadastre quem divide gastos ou empréstimos com você. Depois, use “Dividir” em qualquer despesa dos lançamentos."
          icone={<Users size={32} />}
        />
      ) : (
        <div className="space-y-4">
          {saldos.map((s) => (
            <Card key={s.pessoa.id} aria-label={`Pessoa ${s.pessoa.nome}`}>
              <CardTitulo
                acao={
                  <span className="flex items-center gap-1">
                    <IconButton rotulo={`Editar ${s.pessoa.nome}`} onClick={() => setFormPessoa({ editar: s.pessoa })}>
                      <Pencil size={16} aria-hidden />
                    </IconButton>
                    <IconButton rotulo={`Excluir ${s.pessoa.nome}`} onClick={() => setExcluirPessoa(s.pessoa)}>
                      <Trash2 size={16} aria-hidden />
                    </IconButton>
                  </span>
                }
              >
                {s.pessoa.nome}
                {s.pessoa.contato && <span className="ml-2 text-xs font-normal text-muted">{s.pessoa.contato}</span>}
              </CardTitulo>
              <p className="text-2xl font-semibold tracking-tight">
                <Money valor={fromCents(s.liquido)} tom="auto" />
              </p>
              <p className="text-xs text-muted">
                {s.liquido > 0 ? "te deve (a receber − a pagar)" : s.liquido < 0 ? "você deve (a pagar − a receber)" : "sem saldo pendente"}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button tamanho="sm" variante="primary" disabled={s.aReceber === 0} onClick={() => setCobrando(s.pessoa)}>
                  Cobrar
                </Button>
                <Button tamanho="sm" onClick={() => setAvulso({ tipo: "A_RECEBER", pessoaId: s.pessoa.id })}>
                  Emprestei
                </Button>
                <Button tamanho="sm" onClick={() => setAvulso({ tipo: "A_PAGAR", pessoaId: s.pessoa.id })}>
                  Devo
                </Button>
              </div>

              {s.pendentes.length > 0 && (
                <div className="mt-3">
                  <h3 className="text-xs font-semibold uppercase text-muted">Pendentes</h3>
                  <ul className="divide-y divide-line">{s.pendentes.slice().sort((a, b) => b.data - a.data).map(linha)}</ul>
                </div>
              )}
              {s.quitados.length > 0 && (
                <div className="mt-3">
                  <Button
                    tamanho="sm"
                    aria-expanded={quitados.has(s.pessoa.id)}
                    onClick={() => setQuitados((q) => { const n = new Set(q); if (n.has(s.pessoa.id)) n.delete(s.pessoa.id); else n.add(s.pessoa.id); return n; })}
                  >
                    {quitados.has(s.pessoa.id) ? "Ocultar quitados" : `Ver quitados (${s.quitados.length})`}
                  </Button>
                  {quitados.has(s.pessoa.id) && <ul className="mt-1 divide-y divide-line">{s.quitados.slice().sort((a, b) => b.data - a.data).map(linha)}</ul>}
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      <Modal aberto={formPessoa !== null} onFechar={() => setFormPessoa(null)} titulo={formPessoa?.editar ? "Editar pessoa" : "Nova pessoa"}>
        {formPessoa && <PessoaForm editar={formPessoa.editar} onFechar={() => setFormPessoa(null)} />}
      </Modal>
      <Modal aberto={avulso !== null} onFechar={() => setAvulso(null)} titulo={avulso?.tipo === "A_RECEBER" ? "Emprestei" : "Devo"}>
        {avulso && <AvulsoForm tipo={avulso.tipo} pessoaId={avulso.pessoaId} onFechar={() => setAvulso(null)} />}
      </Modal>
      <QuitarModal acerto={quitando} onFechar={() => setQuitando(null)} />
      <CobrarModal pessoa={cobrando} acertos={cobrando ? ds.acertos.filter((a) => a.pessoaId === cobrando.id) : []} onFechar={() => setCobrando(null)} />
      <Confirmar
        aberto={excluirPessoa !== null}
        titulo="Excluir pessoa"
        mensagem={excluirPessoa ? `Excluir “${excluirPessoa.nome}”? Só é possível se não houver acertos.` : ""}
        rotuloConfirmar="Excluir"
        perigo
        onCancelar={() => setExcluirPessoa(null)}
        onConfirmar={() => {
          if (excluirPessoa) {
            const r = acoes.excluirPessoa(excluirPessoa.id);
            if (!r.ok) avisar("erro", r.erro);
          }
          setExcluirPessoa(null);
        }}
      />
      <Confirmar
        aberto={excluirAcerto !== null}
        titulo="Excluir acerto"
        mensagem={excluirAcerto ? `Excluir o acerto “${excluirAcerto.descricao}” (${brlCentavos(Math.round(excluirAcerto.valor * 100))})? O lançamento de origem, se houver, não é alterado.` : ""}
        rotuloConfirmar="Excluir"
        perigo
        onCancelar={() => setExcluirAcerto(null)}
        onConfirmar={() => {
          if (excluirAcerto) acoes.excluirAcerto(excluirAcerto.id);
          setExcluirAcerto(null);
        }}
      />
    </>
  );
}
