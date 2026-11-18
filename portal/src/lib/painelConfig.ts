import { useCallback, useMemo } from "react";
import { CONFIG_PADRAO, normalizarConfig, type ConfigPainel } from "./finance/painel503020";
import { lerConfigLocal, useConfigGeral } from "./configGeral";

/**
 * R54/R57 - configuração do painel 50-30-20 (metas %, meta de meses da reserva e classificação por categoria).
 * Local-first (localStorage) e sincronizada com a nuvem (`configuracoes/geral`) quando o Firebase está ligado.
 */
export function lerConfigPainel(): ConfigPainel {
  return normalizarConfig(lerConfigLocal().painel503020 ?? CONFIG_PADRAO);
}

export function usePainelConfig(): [ConfigPainel, (c: ConfigPainel) => void] {
  const bruta = useConfigGeral((s) => s.config.painel503020);
  const alterar = useConfigGeral((s) => s.alterar);
  const config = useMemo(() => (bruta ? normalizarConfig(bruta) : CONFIG_PADRAO), [bruta]);
  const salvar = useCallback((c: ConfigPainel) => alterar({ painel503020: normalizarConfig(c) }), [alterar]);
  return [config, salvar];
}
