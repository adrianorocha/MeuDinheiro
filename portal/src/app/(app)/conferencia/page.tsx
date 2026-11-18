"use client";

import { ConferenciaSaldos } from "@/components/conferencia/ConferenciaSaldos";
import { PageHeader } from "@/components/ui/Misc";

export default function ConferenciaPage() {
  return (
    <>
      <PageHeader titulo="Conferência de saldos" descricao="Confira saldos de contas e limites de cartão contra o extrato e recalcule se necessário" />
      <ConferenciaSaldos />
    </>
  );
}
