"use client";

import { useId, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ErroBox } from "@/components/ui/Misc";
import { camposSobrescritos, lerTextoPix, resumoPix, sugerirPessoa, type CamposAtuais, type DirecaoPix, type LeituraPix, type RascunhoPix } from "@/lib/finance/pix";
import type { Pessoa } from "@/lib/finance/types";

interface Props {
  /** Direção assumida quando o texto não diz (a escolha atual do formulário: Despesa = enviado, Receita = recebido). */
  direcaoPadrao: DirecaoPix;
  atual: CamposAtuais;
  pessoas: readonly Pessoa[];
  onAplicar: (r: RascunhoPix) => void;
}

/**
 * R93 - campo "Colar Pix copia e cola ou comprovante". Tudo local: o texto fica só neste estado (não é enviado, gravado nem logado).
 * Lê, mostra o resumo (chave mascarada) e só preenche o formulário quando o usuário confirma.
 */
export function PixLeitor({ direcaoPadrao, atual, pessoas, onAplicar }: Props) {
  const id = useId();
  const [texto, setTexto] = useState("");
  const [leitura, setLeitura] = useState<LeituraPix | null>(null);
  const ok = leitura && leitura.ok ? leitura : null;
  const linhas = useMemo(() => (ok ? resumoPix(ok) : []), [ok]);
  const conflitos = ok ? camposSobrescritos(ok.rascunho, atual) : [];
  const pessoa = ok ? sugerirPessoa(ok.rascunho.contraparte, pessoas) : null;

  function ler() {
    setLeitura(lerTextoPix(texto, direcaoPadrao));
  }

  return (
    <div className="flex flex-col gap-2 rounded-xl border border-line bg-surface-2/40 p-3">
      <label htmlFor={id} className="text-sm font-medium">
        Colar Pix copia e cola ou comprovante
      </label>
      <textarea
        id={id}
        value={texto}
        onChange={(e) => {
          setTexto(e.target.value);
          setLeitura(null);
        }}
        rows={3}
        maxLength={2000}
        autoComplete="off"
        spellCheck={false}
        placeholder="Cole o código do Pix (00020126...) ou o texto do comprovante/notificação do banco"
        className="w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg placeholder:text-muted/70"
      />
      <p className="text-xs text-muted">Lido só neste aparelho: o texto não é enviado nem salvo. A chave completa nunca é gravada.</p>
      <div className="flex gap-2">
        <Button type="button" onClick={ler} disabled={!texto.trim()}>
          Ler
        </Button>
        {texto && (
          <Button
            type="button"
            variante="ghost"
            onClick={() => {
              setTexto("");
              setLeitura(null);
            }}
          >
            Limpar
          </Button>
        )}
      </div>
      {leitura && !leitura.ok && <ErroBox>{leitura.erro}</ErroBox>}
      {ok && (
        <div className="flex flex-col gap-2" role="status">
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-sm">
            {linhas.map((l) => (
              <div key={l.rotulo} className="contents">
                <dt className="text-muted">{l.rotulo}</dt>
                <dd className="break-all">{l.valor}</dd>
              </div>
            ))}
          </dl>
          <p className="text-xs">
            Vai preencher: <strong>{ok.rascunho.descricao}</strong> · {ok.rascunho.tipo === "DEBITO" ? "débito (saída)" : "crédito (entrada)"} · tag #PIX
          </p>
          {conflitos.length > 0 && (
            <p className="text-xs text-warn">Atenção: vai substituir o que você já preencheu em {conflitos.join(", ")}.</p>
          )}
          {pessoa && (
            <p className="text-xs text-muted">
              Parece ser {pessoa.nome} (Pessoa cadastrada). Se quiser, vincule depois pelo botão Dividir do lançamento; nada é vinculado automaticamente.
            </p>
          )}
          <div>
            <Button type="button" variante="primary" onClick={() => onAplicar(ok.rascunho)}>
              {conflitos.length > 0 ? "Substituir e preencher" : "Preencher lançamento"}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
