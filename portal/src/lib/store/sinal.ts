/**
 * R86 - "sinal de sincronização": depois que o portal grava dados, escreve UM documento `users/{uid}/sinais/portal`; uma Cloud Function
 * (functions/src/index.ts > sinalizarSincronizacao) transforma isso em um push silencioso que faz o app (mesmo fechado) sincronizar.
 * Várias gravações em sequência viram um único sinal (debounce), para não gerar uma execução da Function por lançamento.
 */
export class AgendadorSinal {
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly escrever: () => Promise<void>,
    private readonly atrasoMs = 4000,
  ) {}

  /** Pede um sinal; pedidos dentro da janela se fundem no último (o sinal sai [atrasoMs] depois do ÚLTIMO pedido). */
  pedir(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      void this.escrever().catch(() => undefined); // melhor esforço: o app também sincroniza ao abrir
    }, this.atrasoMs);
  }

  /** Cancela o que estiver pendente (logout/troca de conta). */
  cancelar(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }
}
