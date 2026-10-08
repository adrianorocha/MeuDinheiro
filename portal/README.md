# Meu Dinheiro – Portal Web

Portal web de finanças pessoais (Next.js + React + TypeScript) com paridade funcional com o app Android.
Segue o contrato de dados e as regras financeiras R1–R17 de [`../docs/CONTRATO_DADOS.md`](../docs/CONTRATO_DADOS.md).

O portal é **cliente puro** (build estático, `output: "export"`): não há backend próprio. Os dados ficam no
Firestore (modo nuvem, sincroniza com o app) ou no navegador (modo local).

## Requisitos

- Node.js 20+ e npm.

## Scripts

| Comando | O que faz |
|---|---|
| `npm install` | instala dependências |
| `npm run dev` | servidor de desenvolvimento (http://localhost:3000) |
| `npm run lint` | ESLint (`eslint-config-next` + TypeScript) |
| `npm run test` | testes unitários (vitest) da biblioteca de finanças e do backup |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run build` | build de produção estático em `out/` |

## Configurar o Firebase (modo nuvem)

1. No [console do Firebase](https://console.firebase.google.com/), abra o projeto **`meudinheiro-1b054`**.
2. Em *Configurações do projeto > Seus apps*, **crie um app Web** (`</>`) e copie o objeto `firebaseConfig`.
3. Em *Authentication > Sign-in method*, habilite **E-mail/senha**.
4. Em *Firestore Database*, crie o banco (se ainda não existir).
5. Publique as regras e índices deste diretório:
   ```bash
   npm i -g firebase-tools
   firebase login
   firebase use meudinheiro-1b054
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   `firestore.rules` permite que cada usuário leia/escreva somente `users/{uid}/**` (`request.auth.uid == uid`) e nega o resto.
6. Copie `.env.example` para `.env.local` e preencha:

   | Variável | Origem no `firebaseConfig` |
   |---|---|
   | `NEXT_PUBLIC_FIREBASE_API_KEY` | `apiKey` |
   | `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | `authDomain` |
   | `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | `projectId` (`meudinheiro-1b054`) |
   | `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET` | `storageBucket` |
   | `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID` | `messagingSenderId` |
   | `NEXT_PUBLIC_FIREBASE_APP_ID` | `appId` |

   As variáveis `NEXT_PUBLIC_*` são embutidas no build; reinicie o `npm run dev` ou refaça o `npm run build` ao alterá-las.
7. (Opcional) Hospedar: `npm run build && firebase deploy --only hosting` (`firebase.json` publica `out/`).

Sem essas variáveis o portal mostra um aviso na tela de login e oferece **apenas o modo Local**.

## Modos de armazenamento

A escolha é feita na tela de login (e pode ser trocada em Configurações). O padrão é Firebase quando as variáveis existem; senão, Local.
Ambos implementam a mesma interface (`src/lib/store/adapter.ts`):

- **Local** (`LocalAdapter`): o dataset inteiro é serializado no `localStorage` (chave `meudinheiro:local:dataset:v1`,
  no formato do backup v2). Os dados **não saem do navegador** e não sincronizam com o app; para levar dados de/para o
  celular use importar/exportar backup. O limite prático é a cota do `localStorage` (~5 MB, dezenas de milhares de lançamentos).
- **Firebase** (`FirebaseAdapter`): Firestore em `users/{uid}/<colecao>/{id}`, com `onSnapshot` por coleção, persistência offline
  (IndexedDB, multi-aba), escritas em *batch* de até 500 operações e campo `atualizadoEm` (epoch ms) em cada documento.
  Ao carregar e a cada mudança remota o portal recalcula e regrava os caches `contas.saldo` e `cartoes.limiteDisponivel` (R3/R4).

Toda mudança de domínio passa pelas ações em `src/lib/store/actions.ts`, que aplicam as funções puras de
`src/lib/finance/operations.ts`, recalculam os caches (`recalcularTudo`) e persistem apenas o diff (por identidade de objeto).
Ao abrir, o portal também processa as despesas fixas (R16), executa transferências agendadas vencidas (R9) e atualiza o snapshot de patrimônio do mês (R14).

## Backup JSON (compatível com o app)

Em **Configurações**:

- **Exportar backup**: gera `meudinheiro-backup-AAAA-MM-DD.json` no formato v2 (`versaoBackup: 2`, `transacao` para o legado), datas em epoch ms.
- **Importar backup**: aceita v2 e o legado v1 (campo `data` em texto; usa `Date.parse` e, se falhar, a data atual; `pago` ausente vira `true`).
  O arquivo é validado com zod, **os ids são preservados** (ids ausentes/duplicados recebem ids novos), os caches são recalculados
  e os dados atuais são **substituídos** (com confirmação).
- **Dados de exemplo** (somente modo Local) e **apagar todos os dados** (exige digitar `APAGAR`).
- **Modo privado** oculta valores (`R$ ••••••`) e há tema claro/escuro/sistema.

Para levar dados do app ao portal em modo Local: exporte o backup no app e importe aqui. No modo nuvem, basta entrar com a mesma conta.

## Estrutura

```
portal/
├─ firebase.json, firestore.rules, firestore.indexes.json   # Hosting + Firestore
├─ .env.example
└─ src/
   ├─ app/
   │  ├─ login/                  # login/cadastro/recuperar senha + escolha do modo
   │  └─ (app)/                  # rotas autenticadas (guard + shell): dashboard, contas, lancamentos,
   │                             # cartoes, orcamentos, metas, investimentos, recorrencias, pendencias,
   │                             # categorias, relatorios, configuracoes
   ├─ components/                # ui/ (Button, Field, Modal, ...), layout/ (Shell, SessionProvider),
   │                             # charts/ (recharts), lancamentos/ (formulário e lista reutilizáveis)
   └─ lib/
      ├─ finance/                # funções PURAS e testadas (R1–R17)
      │  ├─ money.ts dates.ts ids.ts types.ts
      │  ├─ calc.ts              # saldo, limite, KPIs, fatura, orçamento, patrimônio, previsão, saúde, recalcularTudo
      │  └─ operations.ts        # lançamento/parcelamento, transferência, pagar fatura, metas, fixas, contas, cartões
      ├─ store/                  # zustand, adaptadores (local/firebase), diff, ações, backup (zod)
      ├─ firebase/               # client.ts (init + persistência offline) e auth.ts
      ├─ selectors.ts demo.ts csv.ts format.ts session.ts
```

## Regras financeiras (resumo de decisões)

- Todo valor é arredondado a 2 casas (meio para cima) e somado em **centavos inteiros**.
- `contas.saldo` e `cartoes.limiteDisponivel` são **caches derivados** do extrato; nunca editados manualmente.
- Receitas dos KPIs (R5) **não incluem estornos de cartão** (`CREDITO` com `cartaoId`): eles apenas abatem as despesas, evitando dupla contagem.
- Em R15 uma fatura pendente só reduz o saldo livre previsto se o total em aberto for positivo.
- Despesas fixas ganham `grupoId = fixa:<id>:<AAAA-MM>`, o que torna o processamento idempotente (vários dispositivos/abas).
- Pagamentos de fatura, aportes e resgates de meta não podem ser excluídos diretamente; transferências excluem o par.
- Excluir conta/cartão com lançamentos/compras exige confirmação e remove também os itens dependentes.
- **Cartões virtuais (R18):** `cartoes.cartaoPrincipalId` liga um virtual ao físico. O grupo compartilha limite, conta, dias e fatura; o limite é gravado igual em todos os cartões do grupo, a fatura é única (ciclo do físico) e pagar por qualquer cartão do grupo quita tudo (`grupoId = fatura:<id do principal>:...`).

## Pacote "dia a dia" (R19–R34)

Regras puras em `src/lib/finance/` (`relatorios.ts`, `analises.ts`, `texto.ts`; duplicar/repetir e lixeira em `operations.ts`; PIN em `src/lib/pin.ts`), todas com testes.

- **Relatórios** (`/relatorios`): construtor com filtros R19 (período, conta, cartão físico/virtual, categorias, tipo, situação, texto), prévia ao vivo e modelos R20 (extrato por conta, fatura, gastos por categoria, receitas × despesas, evolução do patrimônio, anual para IR). Exporta **PDF** (jsPDF + autotable, A4 multipágina), **PNG** (canvas próprio) e **CSV** (`;`, BOM). Tudo roda no navegador, sem CDN.
- **Dia a dia:** botão "+" e atalho **N** (lançamento rápido com chips), alertas de orçamento 80%/100% uma única vez (estado em `localStorage`), sugestão de categoria, duplicar/repetir, busca global (`/busca`).
- **Planejamento** (`/planejamento`): reserva de emergência, assinaturas, simulador parcelar × à vista, regra 50/30/20; metas com prazo (`dataAlvo`) em `/metas`; melhor dia de compra em `/cartoes`.
- **Lixeira** (`/lixeira`): exclusões de lançamentos vão para a lixeira por 30 dias (purga ao abrir). Saldo inicial não vai para a lixeira.
- **Conta compartilhada (R32, só modo nuvem):** Configurações → Compartilhar. Publique `firestore.rules` (a regra de `users/{uid}/**` exclui `membros`; há regras para `perfis/{email}` e convites). Cada login grava `perfis/{email}`.
- **PIN (R34):** Configurações → PIN de bloqueio (hash PBKDF2 no `localStorage`, bloqueio por inatividade).

## Recorrências no cartão (R16)

`despesasFixas.cartaoId` define a forma de pagamento: conta ou cartão (físico/virtual). Com cartão, cada ocorrência é uma compra no cartão (`pago=false`, conta do cartão, consome o limite do grupo). Editar a regra permite trocar conta ↔ cartão; trocar a conta de um cartão move as regras vinculadas. **Excluir um cartão (ou virtual) com recorrência vinculada é bloqueado** com mensagem listando as regras (escolha mais segura: nada é apagado ou desvinculado em silêncio). Excluir uma conta, já confirmado, remove as regras dela, inclusive as pagas em cartões dessa conta.

## Conciliação de extratos (R35–R39)

Rota `/conciliacao` (menu "Conciliação"): assistente em 4 passos — arquivo, destino, revisão e resultado. **O arquivo é lido somente no navegador**; só os lançamentos confirmados são gravados.

- **Formatos:** OFX v1 (SGML) e v2 (XML) e CSV (delimitador `;` `,` ou tab, decimal `1.234,56` ou `1,234.56`, datas `dd/MM/aaaa`, `dd/MM/aa`, `aaaa-MM-dd`, cabeçalho detectado, colunas mapeáveis, débito+crédito, inverter sinal para fatura de cartão). O mapeamento do CSV fica lembrado por formato de cabeçalho (`localStorage`). Decodifica UTF-8 e, se necessário, Windows-1252. Datas OFX com fuso nunca deslocam o dia.
- **Destino:** conta (compara com lançamentos sem cartão) ou cartão (compara com o grupo físico+virtuais, R18); a conta é sugerida pelo `ACCTID`. Mostra a diferença de saldo (saldo do banco − saldo do app até a data).
- **Matching:** janela (3 dias; cartão 5) e tolerância (2%, mínimo R$ 1,00), score `0,55·valor + 0,30·data + 0,15·descrição`, atribuição um-para-um determinística. Classes: automático, sugerido, só no extrato, só no app e duplicado (mesmo `fitid` já conciliado). Reimportar o mesmo arquivo marca tudo como duplicado.
- **Aplicar:** prévia com impacto no saldo/limite e confirmação; operação atômica; opções de mescla (data, valor, descrição, pago — em cartão não marca pago). **Desfazer último lote** (em memória, enquanto a aba estiver aberta) e "Desfazer conciliação" por lançamento em `/lancamentos`, que também ganhou o selo "✓ conciliado" e o filtro Conciliados/Não conciliados.
- **Relatório CSV** (`data;descricao;valor;situacao;lancamento_id;acao`).
- **Exemplos:** `public/exemplos/` (`extrato-exemplo.ofx`, `extrato-exemplo.csv`, `fatura-exemplo.csv`) e o botão "Experimentar com arquivo de exemplo", que gera um OFX a partir dos lançamentos de uma conta do app (use os dados de exemplo em Configurações).
- Código em `src/lib/conciliacao/` (puro, sem DOM): `ofx.ts`, `csv.ts`, `texto-banco.ts`, `matching.ts`, `plano.ts`, `saldo.ts`, `relatorio.ts`.
