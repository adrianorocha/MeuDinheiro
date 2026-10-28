import type { ClasseMatch } from "@/lib/conciliacao/tipos";

export const COR_CLASSE: Record<ClasseMatch, { rotulo: string; barra: string }> = {
  AUTOMATICO: { rotulo: "Automáticos", barra: "bg-pos" },
  SUGERIDO: { rotulo: "Sugeridos", barra: "bg-warn" },
  SO_NO_EXTRATO: { rotulo: "Só no extrato", barra: "bg-neg" },
  DUPLICADO: { rotulo: "Duplicados", barra: "bg-muted" },
};

const ORDEM: ClasseMatch[] = ["AUTOMATICO", "SUGERIDO", "SO_NO_EXTRATO", "DUPLICADO"];

/** Mini barra empilhada com a distribuição por classe de casamento. */
export function BarraEmpilhada({ classes, rotulo }: { classes: Record<ClasseMatch, number>; rotulo: string }) {
  const total = ORDEM.reduce((s, c) => s + classes[c], 0);
  const descricao = ORDEM.map((c) => `${COR_CLASSE[c].rotulo}: ${classes[c]}`).join(", ");
  return (
    <div role="img" aria-label={`${rotulo}. ${descricao}`} title={descricao} className="flex h-2 w-full overflow-hidden rounded-full bg-surface-2">
      {total > 0 && ORDEM.map((c) => (classes[c] > 0 ? <div key={c} className={COR_CLASSE[c].barra} style={{ width: `${(classes[c] / total) * 100}%` }} /> : null))}
    </div>
  );
}

export function LegendaClasses() {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted" aria-label="Legenda">
      {ORDEM.map((c) => (
        <li key={c} className="inline-flex items-center gap-1.5">
          <span className={`size-2.5 rounded-full ${COR_CLASSE[c].barra}`} aria-hidden />
          {COR_CLASSE[c].rotulo}
        </li>
      ))}
    </ul>
  );
}
