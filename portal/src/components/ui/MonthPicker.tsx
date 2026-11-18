import { ChevronLeft, ChevronRight } from "lucide-react";
import { normalizaMes } from "@/lib/finance/dates";
import { formatMesAno } from "@/lib/format";
import { IconButton } from "./Button";

interface Props {
  mes: number;
  ano: number;
  onChange: (v: { mes: number; ano: number }) => void;
  rotulo?: string;
}

export function MonthPicker({ mes, ano, onChange, rotulo = "Mês" }: Props) {
  return (
    <div role="group" aria-label={rotulo} className="inline-flex items-center gap-1 rounded-lg border border-line bg-surface px-1">
      <IconButton rotulo="Mês anterior" onClick={() => onChange(normalizaMes(mes - 1, ano))}>
        <ChevronLeft size={18} aria-hidden />
      </IconButton>
      <span className="min-w-36 text-center text-sm font-medium" aria-live="polite">
        {formatMesAno(mes, ano)}
      </span>
      <IconButton rotulo="Próximo mês" onClick={() => onChange(normalizaMes(mes + 1, ano))}>
        <ChevronRight size={18} aria-hidden />
      </IconButton>
    </div>
  );
}
