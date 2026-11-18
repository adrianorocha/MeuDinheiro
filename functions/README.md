# Cloud Functions do MeuDinheiro (R65 - notificações push)

Envia notificações push (FCM) para o app Android e para o portal web:

| Função | Quando | O que faz |
|---|---|---|
| `avisarVencimentos` | todo dia 08:00 (America/Sao_Paulo) | 1 push agrupado por aparelho com as contas a pagar de hoje, amanhã ou em atraso |
| `resumoSemanal` | domingo 18:00 | totais da semana (entradas/saídas, em centavos) e maior categoria |
| `avisarConvite` | novo doc em `perfis/{email}/convites/{dono}` | avisa o convidado (só se `perfis/{email}` já existir) |
| `avisarOrcamento` | escrita em `users/{uid}/despesas/{id}` | avisa quando uma categoria chega a 80% ou 100% do orçamento do mês (uma vez por categoria/mês/limiar) |

As mensagens são **só de dados** e não levam valores em reais (ver `docs/CONTRATO_DADOS.md`, R65).

## RESSALVA: exige o plano Blaze

Cloud Functions (2ª geração), Cloud Scheduler e Eventarc **só funcionam no plano Blaze** (pago por uso, exige cartão no projeto).
**Sem Blaze nada daqui é implantado e o app continua com as notificações locais que já existem** (ronda diária, limite diário,
extratos, sugestões do banco): elas não dependem de servidor. O push é um complemento opcional.

**Custo esperado (uso pessoal):** poucos centavos por mês, geralmente zero. As 2 execuções agendadas por dia/semana, os gatilhos
por lançamento e as leituras do Firestore ficam dentro da cota gratuita (2 milhões de invocações, 50 mil leituras/dia, 3 jobs do
Scheduler grátis por conta de faturamento; o FCM é gratuito). Pode haver uma cobrança mínima de armazenamento do container
(Artifact Registry) de alguns centavos. Configure um **alerta de orçamento** no Google Cloud (Faturamento > Orçamentos) por segurança.

## O que você precisa fazer (nada foi implantado)

1. **Plano Blaze**: Console Firebase > Uso e faturamento > Modificar plano.
2. **Região**: confira a região do Firestore (`firebase firestore:databases:get "(default)"`) e ajuste `REGIAO` em `src/config.ts`
   se não for `southamerica-east1` (os gatilhos precisam ficar na mesma região do banco).
3. **Web push (opcional)**: Console > Configurações do projeto > Cloud Messaging > "Web Push certificates" > gerar par de chaves;
   coloque a chave pública em `portal/.env.local` como `NEXT_PUBLIC_FIREBASE_VAPID_KEY=...` e refaça o `npm run build` do portal.
   Sem ela o botão de notificações do portal fica oculto (o app Android não depende dela).
4. **Regras do Firestore**: publique `portal/firestore.rules` (valida os campos de `users/{uid}/dispositivos/{id}`).
5. **Deploy** (a partir de `portal/`, onde está o `firebase.json`; ele aponta `functions.source` para `../functions`):

   ```bash
   cd functions && npm install && npm test && cd ..
   cd portal
   firebase deploy --only functions            # só as funções
   firebase deploy --only firestore:rules      # as regras
   ```

   O `predeploy` compila o TypeScript (`npm --prefix ../functions run build`). Requer Firebase CLI atual e Node 20+ local.
6. No app: Configurações > "Notificações push" > ligar (exige a nuvem ligada e login). No portal: Configurações > "Ativar
   notificações neste navegador".

## Como desativar

- **Só o envio**: `firebase functions:delete avisarVencimentos resumoSemanal avisarConvite avisarOrcamento` (ou apague no Console >
  Functions). Nada mais é enviado; o app volta a depender só dos avisos locais.
- **Por aparelho**: desligue o interruptor em Configurações (app/portal): o documento do dispositivo é apagado.
- **Por tipo**: interruptores por tipo (vencimentos, orçamento, resumo semanal, convites).
- Voltar ao plano Spark: delete as funções antes; o app/portal seguem normais.

## Desenvolvimento

```bash
cd functions
npm install
npm test          # vitest: lógica pura em src/dominio (sem firebase-admin)
npm run typecheck
npm run build     # gera lib/
```

Estrutura: `src/dominio/*` = decisões puras e testadas (datas em São Paulo, vencimentos, resumo em centavos, orçamento 80%/100%
com idempotência, prefs, vínculo dispositivo×conta, limpeza de tokens, mensagem FCM); `src/acesso.ts` = Firestore/FCM; `src/index.ts` = funções.
Nada foi executado contra o Firebase real (sem emulador): teste com o Emulator Suite ou em um projeto de teste antes de confiar.

## Segurança e privacidade

- Tokens ficam em `users/<uid>/dispositivos/<id>`, legíveis só pelo próprio uid (regras). As funções usam o Admin SDK.
- Um aparelho só recebe avisos de uma conta alheia se o uid dele for **membro** dela (`users/{dono}/membros/{uid}`).
- Sem valores em reais no título/corpo; no app a tela bloqueada mostra texto genérico.
- Tokens recusados pelo FCM e aparelhos sem renovação há 90 dias são apagados; o app apaga o seu no logout.
