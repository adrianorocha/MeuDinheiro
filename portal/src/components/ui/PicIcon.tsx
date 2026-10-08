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

/** `pic` do contrato (nome do drawable do app) -> ícone lucide. */
const ICONES: Record<string, LucideIcon> = {
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
