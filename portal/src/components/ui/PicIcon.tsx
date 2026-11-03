import {
  ArrowLeftRight,
  Banknote,
  Bus,
  Clapperboard,
  CreditCard,
  Dumbbell,
  Fuel,
  Gamepad2,
  GraduationCap,
  HeartPulse,
  Landmark,
  PiggyBank,
  Sandwich,
  ShoppingBag,
  ShoppingCart,
  Tag,
  Utensils,
  Wine,
  Wrench,
  type LucideIcon,
} from "lucide-react";
import { GRUPOS_PICS, ICONES_CATALOGO } from "./picsCatalogo";

export { GRUPOS_PICS } from "./picsCatalogo";
export type { GrupoPics } from "./picsCatalogo";

/** `pic` do contrato (nome do drawable do app) -> ícone lucide. */
const ICONES: Record<string, LucideIcon> = {
  ...ICONES_CATALOGO,
  // Originais por último: nunca podem ser sobrescritos pelo catálogo (R50).
  fuel: Fuel,
  restaurant: Utensils,
  transport: Bus,
  shopping: ShoppingBag,
  cinema: Clapperboard,
  health: HeartPulse,
  education: GraduationCap,
  salary: Banknote,
  repair_car: Wrench,
  supermarket: ShoppingCart,
  gym: Dumbbell,
  games: Gamepad2,
  game: Gamepad2,
  drink: Wine,
  lunch: Sandwich,
  reserva: PiggyBank,
  ic_savings: PiggyBank,
  transferencia: ArrowLeftRight,
  credit_card: CreditCard,
  bank: Landmark,
};

export const PICS_CATEGORIA = [
  "fuel",
  "restaurant",
  "transport",
  "shopping",
  "cinema",
  "health",
  "education",
  "salary",
  "repair_car",
  "supermarket",
  "gym",
  "games",
  "drink",
  "lunch",
  "reserva",
] as const;

/** Rótulo pt-BR de cada chave de categoria (originais + catálogo R50). */
export const NOME_PIC: Record<string, string> = {
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
  ...Object.fromEntries(GRUPOS_PICS.flatMap((g) => g.itens.map((i) => [i.chave, i.rotulo]))),
};

/** Grupo extra com os 15 ícones originais (mesmas chaves do app), exibido primeiro no seletor. */
export const GRUPO_ORIGINAIS: { id: string; rotulo: string; itens: { chave: string; rotulo: string }[] } = {
  id: "originais",
  rotulo: "Padrão",
  itens: PICS_CATEGORIA.map((chave) => ({ chave, rotulo: NOME_PIC[chave] })),
};

export function PicIcon({ pic, size = 18, className = "" }: { pic: string; size?: number; className?: string }) {
  const Icone = ICONES[pic] ?? (pic ? Landmark : Tag);
  return <Icone size={size} className={className} aria-hidden />;
}

/** Ícone arredondado usado em listas. */
export function PicBadge({ pic, size = 18 }: { pic: string; size?: number }) {
  return (
    <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
      <PicIcon pic={pic} size={size} />
    </span>
  );
}
