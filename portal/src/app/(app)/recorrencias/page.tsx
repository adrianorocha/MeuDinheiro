"use client";

import { FastForward, Pause, Pencil, Play, Plus, Repeat, Trash2 } from "lucide-react";
import { SeletorCategoria } from "@/components/ui/SeletorCategoria";
import { picDaCategoria } from "@/lib/catalogo";
import { useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { Badge, Card, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { PicBadge } from "@/components/ui/PicIcon";
import { recorrenciaPausada } from "@/lib/finance/operations";
import type { DespesaFixa, Tipo } from "@/lib/finance/types";
import { deInputData, formatBRL, formatData, formatMesAno, parseValorBR, valorParaCampo } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

function FixaForm({ editar, onFechar }: { editar: DespesaFixa | null; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [descricao, setDescricao] = useState(editar?.descricao ?? "");
  const [valorTxt, setValorTxt] = useState(editar ? valorParaCampo(editar.valor) : "");
  const [tipo, setTipo] = useState<Tipo>(editar?.tipo ?? "DEBITO");
  const [dia, setDia] = useState(String(editar?.diaVencimento ?? 5));
  const [conta, setConta] = useState(editar?.conta ?? ds.contas[0]?.conta ?? "");
  const [forma, setForma] = useState<"conta" | "cartao">(editar?.cartaoId ? "cartao" : "conta");
  const [cartaoId, setCartaoId] = useState(String(editar?.cartaoId ?? ds.cartoes[0]?.id ?? ""));
  const [categoria, setCategoria] = useState(editar?.categoria ?? "");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const valor = parseValorBR(valorTxt);
    if (!categoria.trim()) return setErro("Informe a categoria.");
    const pic = picDaCategoria(categoria, ds.categorias) ?? editar?.pic ?? "";
    if (forma === "cartao" && !cartaoId) return setErro("Selecione o cartão.");
    const dados = { descricao, valor, tipo, diaVencimento: Number(dia), conta, cartaoId: forma === "cartao" ? Number(cartaoId) : null, categoria: categoria.trim(), pic };
    const r = editar ? acoes.editarDespesaFixa(editar.id, dados) : acoes.criarDespesaFixa(dados);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", editar ? "Recorrência atualizada." : "Recorrência criada.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Descrição" value={descricao} onChange={(e) => setDescricao(e.target.value)} required />
      <div className="grid grid-cols-2 gap-3">
        <Select rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as Tipo)}>
          <option value="DEBITO">Despesa</option>
          <option value="CREDITO">Receita</option>
        </Select>
        <InputValor rotulo="Valor (R$)" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
      </div>
      <Input rotulo="Dia do vencimento" type="number" min={1} max={31} value={dia} onChange={(e) => setDia(e.target.value)} dica="29 a 31 caem no último dia dos meses curtos." />
      <Segmentado
        rotulo="Forma de pagamento"
        valor={forma}
        onChange={setForma}
        opcoes={[
          { valor: "conta", rotulo: "Conta" },
          { valor: "cartao", rotulo: "Cartão" },
        ]}
      />
      {forma === "conta" ? (
        <Select rotulo="Conta" value={conta} onChange={(e) => setConta(e.target.value)}>
          {ds.contas.map((c) => (
            <option key={c.id} value={c.conta}>
              {c.banco} · {c.conta}
            </option>
          ))}
        </Select>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <Select rotulo="Cartão" value={cartaoId} onChange={(e) => setCartaoId(e.target.value)}>
            {ds.cartoes.length === 0 && <option value="">Nenhum cartão cadastrado</option>}
            {ds.cartoes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nome} (final {c.finalCartao || "—"}){c.cartaoPrincipalId != null ? " · virtual" : ""}
              </option>
            ))}
          </Select>
          <Input
            rotulo="Conta (do cartão)"
            readOnly
            value={(() => {
              const cc = ds.cartoes.find((c) => c.id === Number(cartaoId));
              const ct = cc ? ds.contas.find((x) => x.id === cc.contaId) : undefined;
              return ct ? `${ct.banco} · ${ct.conta}` : "—";
            })()}
          />
        </div>
      )}
      <SeletorCategoria valor={categoria} onChange={setCategoria} personalizadas={ds.categorias} />
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

/** R40 — paga agora um mês futuro da recorrência (com ou sem desconto). */
function AdiantarForm({ fixa, onFechar }: { fixa: DespesaFixa; onFechar: () => void }) {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const meses = Array.from({ length: 12 }, (_, i) => {
    const d = new Date(new Date(agora).getFullYear(), new Date(agora).getMonth() + 1 + i, 1);
    return { mes: d.getMonth() + 1, ano: d.getFullYear() };
  }).filter((m) => !ds.despesas.some((d) => d.pago && d.grupoId === `fixa:${fixa.id}:${m.ano}-${String(m.mes).padStart(2, "0")}`));
  const [alvo, setAlvo] = useState(meses[0] ? `${meses[0].ano}-${meses[0].mes}` : "");
  const [valorTxt, setValorTxt] = useState(valorParaCampo(fixa.valor));
  const [erro, setErro] = useState<string | null>(null);
  const valor = parseValorBR(valorTxt);
  const desconto = Number.isFinite(valor) ? Math.round((fixa.valor - valor) * 100) / 100 : 0;

  function enviar(e: FormEvent) {
    e.preventDefault();
    const [ano, mes] = alvo.split("-").map(Number);
    if (!ano || !mes) return setErro("Selecione o mês.");
    const r = acoes.adiantarOcorrenciaFixa({ fixaId: fixa.id, mes, ano, valorPago: valor });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", `${formatMesAno(mes, ano)} pago adiantado (${formatBRL(valor)}).`);
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Select rotulo="Mês a pagar agora" value={alvo} onChange={(e) => setAlvo(e.target.value)}>
        {meses.map((m) => (
          <option key={`${m.ano}-${m.mes}`} value={`${m.ano}-${m.mes}`}>
            {formatMesAno(m.mes, m.ano)}
          </option>
        ))}
      </Select>
      <InputValor rotulo="Valor pago (R$)" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} dica={`Valor da regra: ${formatBRL(fixa.valor)}. Pagando menos, a diferença é desconto.`} />
      {desconto > 0 && <p className="text-sm text-pos">Desconto de {formatBRL(desconto)} neste mês.</p>}
      <p className="text-xs text-muted">O mês fica pago hoje e não será lançado de novo quando chegar.</p>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Pagar agora" />
    </form>
  );
}

/** R47 — pausa a recorrência, com data de retomada opcional. */
function PausarForm({ fixa, onFechar }: { fixa: DespesaFixa; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [ate, setAte] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const limite = ate ? deInputData(ate) : null;
    if (limite !== null && Number.isNaN(limite)) return setErro("Data inválida.");
    const r = acoes.pausarRecorrencia(fixa.id, limite);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", limite ? `Recorrência pausada até ${formatData(limite)}.` : "Recorrência pausada.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Pausar até (opcional)" type="date" value={ate} onChange={(e) => setAte(e.target.value)} dica="Sem data, a recorrência fica pausada até você retomá-la." />
      <p className="text-xs text-muted">Os meses em que ficar pausada não serão lançados nem recuperados depois. Lançamentos já criados permanecem como estão.</p>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Pausar" />
    </form>
  );
}

export default function RecorrenciasPage() {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [form, setForm] = useState<{ editar: DespesaFixa | null } | null>(null);
  const [excluir, setExcluir] = useState<DespesaFixa | null>(null);
  const [adiantar, setAdiantar] = useState<DespesaFixa | null>(null);
  const [pausar, setPausar] = useState<DespesaFixa | null>(null);
  const agora = useAgora();

  function retomar(f: DespesaFixa) {
    const r = acoes.retomarRecorrencia(f.id);
    if (!r.ok) return avisar("erro", r.erro);
    avisar("sucesso", "Recorrência retomada. Os meses pausados não são recuperados.");
  }

  function processar() {
    const r = acoes.processarFixas();
    if (!r.ok) return avisar("erro", r.erro);
    avisar(r.criados.length ? "sucesso" : "info", r.criados.length ? `${r.criados.length} lançamento(s) criado(s).` : "Nada a lançar agora.");
  }

  return (
    <>
      <PageHeader
        titulo="Recorrências"
        descricao="Despesas e receitas fixas lançadas todo mês"
        acoes={
          <>
            <Button icone={<Play size={16} aria-hidden />} onClick={processar} disabled={ds.despesasFixas.length === 0}>
              Processar agora
            </Button>
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })} disabled={ds.contas.length === 0}>
              Nova recorrência
            </Button>
          </>
        }
      />
      <p className="mb-4 text-sm text-muted">Ao abrir o portal, os meses pendentes (até 12) são lançados automaticamente como pendentes.</p>
      {ds.despesasFixas.length === 0 ? (
        <EmptyState titulo="Nenhuma recorrência" descricao="Cadastre aluguel, assinaturas ou salário para lançar automaticamente." icone={<Repeat size={32} />} />
      ) : (
        <Card>
          <ul className="divide-y divide-line">
            {ds.despesasFixas.map((f) => {
              const pausada = recorrenciaPausada(f, agora);
              return (
              <li key={f.id} className={`flex items-center gap-3 py-3 ${pausada ? "opacity-60" : ""}`}>
                <PicBadge pic={ds.categorias.find((c) => c.nome.trim().toLowerCase() === f.categoria.trim().toLowerCase())?.pic || f.pic} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{f.descricao}</p>
                  <p className="truncate text-xs text-muted">
                    Todo dia {f.diaVencimento} · {f.categoria} · {(() => {
                      const cartao = f.cartaoId ? ds.cartoes.find((c) => c.id === f.cartaoId) : undefined;
                      return cartao ? `${cartao.nome} ••${cartao.finalCartao || "----"}${cartao.cartaoPrincipalId != null ? " (virtual)" : ""}` : (ds.contas.find((c) => c.conta === f.conta)?.banco ?? f.conta);
                    })()}
                  </p>
                  <p className="text-xs text-muted">{f.ultimaDataLancamento ? `Último lançamento: ${formatData(f.ultimaDataLancamento)}` : "Ainda não lançada"}</p>
                </div>
                {pausada && <Badge tom="warn">{f.pausadaAte ? `Pausada até ${formatData(f.pausadaAte)}` : "Pausada"}</Badge>}
                <Badge tom={f.tipo === "CREDITO" ? "pos" : "neutro"}>{f.tipo === "CREDITO" ? "Receita" : "Despesa"}</Badge>
                <Money valor={f.valor} className="text-sm font-semibold" />
                {pausada ? (
                  <IconButton rotulo={`Retomar ${f.descricao}`} onClick={() => retomar(f)}>
                    <Play size={16} aria-hidden />
                  </IconButton>
                ) : (
                  <IconButton rotulo={`Pausar ${f.descricao}`} onClick={() => setPausar(f)}>
                    <Pause size={16} aria-hidden />
                  </IconButton>
                )}
                {f.tipo === "DEBITO" && !f.cartaoId && !pausada && (
                  <IconButton rotulo={`Adiantar um mês de ${f.descricao}`} onClick={() => setAdiantar(f)}>
                    <FastForward size={16} aria-hidden />
                  </IconButton>
                )}
                <IconButton rotulo={`Editar ${f.descricao}`} onClick={() => setForm({ editar: f })}>
                  <Pencil size={16} aria-hidden />
                </IconButton>
                <IconButton rotulo={`Excluir ${f.descricao}`} onClick={() => setExcluir(f)}>
                  <Trash2 size={16} aria-hidden />
                </IconButton>
              </li>
              );
            })}
          </ul>
        </Card>
      )}
      <Modal aberto={form !== null} onFechar={() => setForm(null)} titulo={form?.editar ? "Editar recorrência" : "Nova recorrência"}>
        {form && <FixaForm editar={form.editar} onFechar={() => setForm(null)} />}
      </Modal>
      <Modal aberto={adiantar !== null} onFechar={() => setAdiantar(null)} titulo={`Adiantar: ${adiantar?.descricao ?? ""}`}>
        {adiantar && <AdiantarForm key={adiantar.id} fixa={adiantar} onFechar={() => setAdiantar(null)} />}
      </Modal>
      <Modal aberto={pausar !== null} onFechar={() => setPausar(null)} titulo={`Pausar: ${pausar?.descricao ?? ""}`}>
        {pausar && <PausarForm key={pausar.id} fixa={pausar} onFechar={() => setPausar(null)} />}
      </Modal>
      <Confirmar
        aberto={excluir !== null}
        perigo
        titulo="Excluir recorrência"
        rotuloConfirmar="Excluir"
        mensagem={`Excluir "${excluir?.descricao ?? ""}"? Os lançamentos já criados são mantidos.`}
        onConfirmar={() => {
          if (excluir) {
            acoes.excluirDespesaFixa(excluir.id);
            avisar("sucesso", "Recorrência excluída.");
          }
          setExcluir(null);
        }}
        onCancelar={() => setExcluir(null)}
      />
    </>
  );
}
