# Meu Dinheiro

App Android (Kotlin + Jetpack Compose) de finanças pessoais + portal web (Next.js).

- `app/` — app Android. Room (SQLite) local; Firebase opcional.
- `portal/` — portal web (ver `portal/README.md`).
- `docs/CONTRATO_DADOS.md` — **fonte única** do esquema do Firestore, do backup JSON e das regras financeiras R1–R17.

## Onde os dados ficam (Configurações → Armazenamento de dados)

| Modo | Comportamento |
|---|---|
| Somente neste celular | Tudo no Room. Nenhuma chamada de rede. |
| Nuvem (Firebase) | Firestore é a fonte da verdade (`users/{uid}/…`); Room funciona como cache offline. Sincronização em 3 vias, em tempo real, com o portal e outros aparelhos. Conflito: a nuvem prevalece. |

Na tela também: login (e-mail/senha), "Sincronizar agora", "Enviar tudo" / "Baixar tudo" (substituem um lado).

### Configurar o Firebase
1. Console Firebase → projeto `meudinheiro-1b054`: ative **Authentication → E-mail/senha** e crie o banco **Firestore**.
2. Publique as regras: `firebase deploy --only firestore:rules` (arquivo `portal/firestore.rules`).
3. Portal: crie um app **Web** no projeto e preencha `portal/.env.local` (ver `portal/.env.example`).

## Correções de lógica financeira (resumo)
- Saldo e limite do cartão agora são **derivados do extrato** (antes eram incrementados à mão e divergiam: transferências sumiam no recálculo, limite era abatido 2×, fatura debitava a conta 2×).
- Transferências são pares de lançamentos; só entram receita/despesa os lançamentos reais (`natureza`).
- Fatura usa o fechamento real do cartão; pagar fatura é uma operação atômica.
- Parcelas sem erro de centavos nem "deriva" de datas; recorrências recuperam meses perdidos e tratam dia 29–31.
- Previsão do mês usa saldo real e contas a pagar reais (antes somava transferências agendadas e usava saldo 0).
- Patrimônio líquido inclui investimentos e metas e desconta faturas; snapshot mensal único e atualizado.
- Aporte/depósito em meta sai da conta (antes criava dinheiro); excluir conta/meta/cartão não deixa órfãos.
- Backup v2 preserva ids e datas em ms; restore é tudo-ou-nada. `REPLACE` em contas (que apagava cartões em cascata) trocado por `@Upsert`.
- Banco v2 com migração (sem `fallbackToDestructiveMigration` em upgrade); senha agora com PBKDF2 (antes texto puro).

## Build e testes
```
./gradlew :app:assembleDebug :app:testDebugUnitTest   # JDK do Android Studio (jbr)
cd portal && npm install && npm run lint && npm test && npm run build
```
Os testes do app incluem Robolectric (Room em memória) para as operações de dinheiro.

## Recursos do dia a dia (app e portal)
- **Relatórios** com filtros combináveis (período, conta, cartão físico/virtual, categoria, tipo, pago, texto) e modelos prontos; exporta **PDF, PNG e CSV**. App: ícone de grade no cabeçalho → Ferramentas → Relatórios.
- **Lançamento rápido** (lançamentos recentes em 1 toque + atalho "Nova despesa" no launcher; tecla `N` no portal), **sugestão de categoria** pelo histórico, **duplicar/repetir** lançamento, **busca global**.
- **Alertas de orçamento** (80% e 100%, uma vez por categoria/mês).
- **Planejamento**: reserva de emergência, regra 50/30/20, assinaturas detectadas, metas com prazo (aporte mensal necessário), melhor dia de compra no cartão, simulador parcelar × à vista.
- **Lixeira de 30 dias**, **backup automático semanal** no app (4 mais recentes), **conta compartilhada** por convite (modo nuvem) e **PIN** no portal.
- Regras: `docs/CONTRATO_DADOS.md` §6 (R19–R34). Publique `portal/firestore.rules` para o compartilhamento funcionar.
