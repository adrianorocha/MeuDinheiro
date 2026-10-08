"use client";

import { Pencil, Plus, Tags, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { Card, EmptyState, ErroBox, PageHeader } from "@/components/ui/Misc";
import { Confirmar, Modal, RodapeForm } from "@/components/ui/Modal";
import { PicBadge, PICS_CATEGORIA } from "@/components/ui/PicIcon";
import { categoriasFaltantes } from "@/lib/catalogo";
import { normalizaNome } from "@/lib/finance/calc";
import type { Categoria } from "@/lib/finance/types";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const NOME_PIC: Record<string, string> = {
  fuel: "Combustível",
  restaurant: "Restaurante",
  transport: "Transporte",
  shopping: "Compras",
  cinema: "Cinema",
  health: "Saúde",
  education: "Educação",
  salary: "Salário",
  repair_car: "Oficina",
  supermarket: "Supermercado",
  gym: "Academia",
  games: "Jogos",
  drink: "Bebidas",
  lunch: "Lanche",
  reserva: "Reserva",
};

function CategoriaForm({ editar, onFechar }: { editar: Categoria | null; onFechar: () => void }) {
  const avisar = useStore((s) => s.avisar);
  const [nome, setNome] = useState(editar?.nome ?? "");
  const [pic, setPic] = useState(editar?.pic || PICS_CATEGORIA[3]);
  const [erro, setErro] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const r = acoes.salvarCategoria({ nome, pic }, editar?.id);
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Categoria salva.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Input rotulo="Nome" value={nome} onChange={(e) => setNome(e.target.value)} maxLength={40} required />
      <Select rotulo="Ícone" value={pic} onChange={(e) => setPic(e.target.value)}>
        {[...new Set<string>([...PICS_CATEGORIA, pic])].map((p) => (
          <option key={p} value={p}>
            {NOME_PIC[p] ?? p}
          </option>
        ))}
      </Select>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

export default function CategoriasPage() {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [form, setForm] = useState<{ editar: Categoria | null } | null>(null);
  const [excluir, setExcluir] = useState<Categoria | null>(null);

  const ordenadas = useMemo(() => [...ds.categorias].sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR")), [ds.categorias]);
  const faltantes = useMemo(() => categoriasFaltantes(ds.categorias), [ds.categorias]);
  const emUso = excluir ? ds.despesas.filter((d) => normalizaNome(d.categoria) === normalizaNome(excluir.nome)).length : 0;

  return (
    <>
      <PageHeader
        titulo="Categorias"
        descricao="Organize seus lançamentos"
        acoes={
          <>
            {faltantes.length > 0 && (
              <Button
                onClick={() => {
                  acoes.adicionarCategoriasPadrao(faltantes);
                  avisar("sucesso", `${faltantes.length} categoria(s) padrão adicionada(s).`);
                }}
              >
                Adicionar padrão ({faltantes.length})
              </Button>
            )}
            <Button variante="primary" icone={<Plus size={16} aria-hidden />} onClick={() => setForm({ editar: null })}>
              Nova categoria
            </Button>
          </>
        }
      />
      {ordenadas.length === 0 ? (
        <EmptyState titulo="Nenhuma categoria" descricao="Adicione as categorias padrão do app ou crie as suas." icone={<Tags size={32} />} />
      ) : (
        <Card>
          <ul className="grid gap-x-6 sm:grid-cols-2">
            {ordenadas.map((c) => (
              <li key={c.id} className="flex items-center gap-3 border-b border-line py-2.5">
                <PicBadge pic={c.pic} />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{c.nome}</span>
                <IconButton rotulo={`Editar ${c.nome}`} onClick={() => setForm({ editar: c })}>
                  <Pencil size={16} aria-hidden />
                </IconButton>
                <IconButton rotulo={`Excluir ${c.nome}`} onClick={() => setExcluir(c)}>
                  <Trash2 size={16} aria-hidden />
                </IconButton>
              </li>
            ))}
          </ul>
        </Card>
      )}
      <Modal aberto={form !== null} onFechar={() => setForm(null)} titulo={form?.editar ? "Editar categoria" : "Nova categoria"}>
        {form && <CategoriaForm editar={form.editar} onFechar={() => setForm(null)} />}
      </Modal>
      <Confirmar
        aberto={excluir !== null}
        perigo
        titulo="Excluir categoria"
        rotuloConfirmar="Excluir"
        mensagem={emUso > 0 ? `${emUso} lançamento(s) usam "${excluir?.nome}". Eles continuarão com esse nome de categoria. Excluir mesmo assim?` : `Excluir a categoria "${excluir?.nome ?? ""}"?`}
        onConfirmar={() => {
          if (excluir) {
            acoes.excluirCategoria(excluir.id);
            avisar("sucesso", "Categoria excluída.");
          }
          setExcluir(null);
        }}
        onCancelar={() => setExcluir(null)}
      />
    </>
  );
}
