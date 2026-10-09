# Contrato de dados e regras financeiras — Meu Dinheiro

Fonte única de verdade para o **app Android (Kotlin/Compose)**, o **Firestore** e o **portal web (Next.js)**.
Qualquer alteração aqui deve ser refletida nos três lugares.

## 1. Armazenamento

| Modo | Onde vivem os dados | Quem acessa |
|---|---|---|
| `LOCAL` | Room (SQLite) no celular | somente o app (portal usa import/export de backup JSON) |
| `FIREBASE` | Firestore (`users/{uid}/…`) é a fonte da verdade; o Room é cache offline‑first sincronizado | app + portal |

Autenticação na nuvem: Firebase Auth e‑mail/senha. Cada usuário só acessa `users/{uid}/**` (ver `firestore.rules`).

## 2. Coleções Firestore (`users/{uid}/<colecao>/{id}`)

`id` do documento = `String(id)`. Campos = nomes das propriedades Kotlin. Datas = **epoch em milissegundos (number)**.
Todo documento tem também `atualizadoEm: number` (epoch ms da última escrita).
IDs gerados pelo portal: tipos `Long` (despesas) → `Date.now()`; tipos `Int` → inteiro aleatório em `[1_000_000_000, 2_000_000_000)` sem colidir com ids existentes.

| Coleção | Campos |
|---|---|
| `contas` | `id:int, saldo:number, banco, pic, agencia, conta, titular` |
| `despesas` | `id:long, descricao, valor:number, data:ms, categoria, conta, pic, tipo:"DEBITO"\|"CREDITO", mes:1‑12, ano, cartaoId:int\|null, valorOriginal, moedaOriginal, cotacaoNaData, pago:bool, natureza, grupoId:string\|null, autor:string\|null, fitid:string\|null, conciliadoEm:ms\|null` |
| `despesasFixas` | `id:int, descricao, valor, conta, categoria, pic, tipo, diaVencimento:1‑31, ultimaDataLancamento:ms\|null, cartaoId:int\|null, pausada?:boolean (R47), pausadaAte?:ms\|null (R47)` |
| `categorias` | `id:int, nome, pic` |
| `orcamentos` | `id:int, categoria, valorLimite` |
| `metas` | `id:int, nome, valorObjetivo, valorGuardado, icone, dataAlvo:ms\|null` |
| `investimentos` | `id:int, nome, tipo, valorInvestido, valorAtual` |
| `cartoes` | `id:int, nome, finalCartao, tipo, limiteDisponivel, limiteTotal, diaFechamento, diaVencimento, contaId:int, cartaoPrincipalId:int\|null, limiteProprio:double\|null` |
| `transferenciasAgendadas` | `id:int, dataAgendada:ms, contaOrigem, contaDestino, valor, executada:bool` |
| `patrimonio` | `id:int, dataMillis:ms, valorTotal, mesReferencia` |
| `lixeira` | `id:int, tipo:"DESPESA", descricao, valor, excluidoEm:ms, payload:string` (JSON da despesa excluída — R31) |
| `transacoes` | `id:int, descricao, valor, bancoNome, categoriaNome, categoriaCorHex, timestamp:ms` (legado, só sincroniza) |

`cartoes.cartaoPrincipalId` = `null` em cartão **físico (principal)**; em **cartão virtual** aponta para o id do físico (ver R18).
`despesas.conta` referencia `contas.conta` (número da conta, string). `cartoes.contaId` referencia `contas.id`.
`natureza` ∈ `NORMAL | SALDO_INICIAL | TRANSFERENCIA | APORTE_META | RESGATE_META | PAGAMENTO_FATURA | AJUSTE`.

## 3. Backup JSON (versão 2) — compatível app ⇄ portal

```json
{ "versaoBackup": 2, "contas": [], "despesas": [], "despesasFixas": [], "categorias": [], "orcamentos": [],
  "metas": [], "investimentos": [], "cartoes": [], "transferenciasAgendadas": [], "patrimonio": [], "transacao": [] }
```
Mesmos campos da seção 2; `despesas[].data` em epoch ms. Backups v1 (legado) podem ter `data` como texto de data; o importador tenta `Date.parse` e, se falhar, usa a data atual.
**Ids são preservados** no restore (v1 zerava ids e quebrava vínculos).

## 4. Regras financeiras (R1–R17)

**R1 – Dinheiro.** Todo valor é arredondado a 2 casas (meio para cima) ao gravar; somas são feitas em centavos inteiros.

**R2 – Direção.** `tipo=CREDITO` = entrada; `tipo=DEBITO` = saída (nada a ver com cartão de crédito).
Compra no cartão = `tipo=DEBITO` + `cartaoId` preenchido (`0` é tratado como `null`).

**R3 – Saldo da conta (derivado do extrato).**
`saldo(conta) = round2( Σ CREDITO − Σ DEBITO )` sobre despesas com `despesa.conta == conta.conta && pago && cartaoId == null`.
`contas.saldo` é só cache desse cálculo e deve ser recalculado a cada mutação (nunca editado "à mão").

**R4 – Limite do cartão (derivado).**
`limiteDisponivel = limiteTotal − Σ DEBITO + Σ CREDITO(estornos)` sobre despesas do cartão com `pago == false`.
Parcelas futuras consomem o limite integralmente. Nunca "abater/estornar" incrementalmente.

**R18 – Cartões virtuais (limite compartilhado).** Um cartão com `cartaoPrincipalId != null` é virtual e pertence a um cartão físico (o principal tem `cartaoPrincipalId == null`; virtual de virtual é inválido).
O **grupo** = principal + seus virtuais. O grupo compartilha: limite, conta, dias de fechamento/vencimento e fatura.
- Ao salvar um virtual, `contaId`, `limiteTotal`, `diaFechamento`, `diaVencimento` e `tipo` são **copiados do principal**; ao editar o principal, propagar para os virtuais.
- R4 por grupo: `limiteDisponivel = limiteTotal(principal) − Σ DEBITO + Σ CREDITO` das despesas `!pago` de **todos os cartões do grupo**; gravar o mesmo valor em todos os cartões do grupo.
- R6/R7 por grupo: existe **uma** fatura por ciclo, a do principal, contendo as compras de todos os cartões do grupo; pagar a fatura (por qualquer cartão do grupo) quita todas elas; `grupoId` do pagamento usa o id do **principal**. Cada compra mantém o `cartaoId` do cartão realmente usado (para identificar qual virtual comprou).
- R15 e R14 tratam o grupo como um cartão só (não somar faturas duplicadas).
- R41: cada cartão tem saldo próprio dentro do limite compartilhado (ver seção abaixo).
- Excluir virtual: só sem compras em aberto; as compras já pagas passam para o principal (`cartaoId = principal`). Excluir principal: só se o grupo todo estiver sem compras em aberto; remove os virtuais e as compras do grupo.

**R5 – KPIs do período** (filtro por `despesa.data` no intervalo `[inicio, fim]`; só `natureza == NORMAL`):
- `receitasRealizadas = Σ CREDITO pago` · `receitasPrevistas = Σ CREDITO !pago`
- `despesasTotal = Σ DEBITO − Σ CREDITO com cartaoId` (competência: compra no cartão conta na data da compra)
- `despesasPagas = Σ DEBITO pago` · `despesasPendentes = despesasTotal − despesasPagas`
- `resultado = receitasRealizadas − despesasTotal` · `taxaPoupanca = receitasRealizadas > 0 ? resultado / receitasRealizadas : 0`
Transferências, aportes, pagamento de fatura, saldo inicial, resgates e ajustes **não** entram em receitas/despesas.

**R6 – Fatura do cartão.**
`fechamento(M,Y) = min(diaFechamento, diasNoMes(M,Y))`. Compra em `d` pertence à fatura do mês de `d` se `dia(d) <= fechamento(mês de d)`, senão à do mês seguinte (a fatura é identificada pelo mês de **fechamento**).
Vencimento da fatura (M,Y): se `diaVencimento > diaFechamento` → mês M, senão mês M+1; dia = `min(diaVencimento, diasNoMes)`.

**R7 – Pagar fatura (cartão, M, Y).** `total = Σ DEBITO − Σ CREDITO` dos itens `!pago` do ciclo; exige `total > 0`.
Cria 1 despesa `DEBITO, pago, natureza=PAGAMENTO_FATURA, categoria="Cartão", conta=conta do cartão, cartaoId=null, valor=total, grupoId="fatura:{cartaoId}:{Y}-{MM}"`;
marca os itens como `pago=true`; recalcula saldo (R3) e limite (R4). A despesa de pagamento não pode ser excluída diretamente.

**R8 – Parcelamento.** `n` parcelas: `base = floor(total/n·100)/100`; a última recebe o resto (soma exata).
Data da parcela `i` = data inicial + `(i−1)` meses a partir da data **original** (dia limitado ao último dia do mês, sem "deriva").
Descrição `"<desc> (i/n)"`, `grupoId` comum. `pago`: cartão → `false`; conta → `data <= agora`.

**R9 – Transferência (origem→destino, valor).** `valor > 0`, contas diferentes e existentes, `saldo(origem) >= valor`.
Grava 2 lançamentos `pago=true, natureza=TRANSFERENCIA, categoria="Transferência"` com o mesmo `grupoId="transf:<uuid>"`: `DEBITO` na origem, `CREDITO` no destino. Excluir um exclui o par.
Agendada: ao vencer, executa o mesmo fluxo e marca `executada=true`.

**R10 – Metas.** Aporte: `valor > 0`, `saldo(conta) >= valor`; `valorGuardado += valor`; lançamento `DEBITO pago natureza=APORTE_META categoria="Reserva"`.
Excluir meta com saldo guardado e conta destino: lançamento `CREDITO natureza=RESGATE_META`, depois remove a meta.

**R11 – Exclusão/edição.** Excluir/editar lançamento ⇒ recalcular saldo(s) e limite(s) afetados. Conta de despesa não paga não "devolve" saldo (nunca foi debitada).

**R12 – Alternar pago.** Em lançamento de conta recalcula o saldo; em compra de cartão só altera o limite (R4).

**R13 – Orçamento.** `gasto(categoria, mês) = Σ DEBITO NORMAL − estornos` do mês corrente por data; categoria comparada sem diferenciar maiúsculas/espaços.
`pct = gasto/limite` (pode passar de 100%); ≥80% atenção, ≥100% estourado.

**R14 – Patrimônio líquido.** `Σ saldos das contas + Σ investimentos.valorAtual + Σ metas.valorGuardado − Σ faturas em aberto (cartão, !pago: DEBITO − CREDITO)`.
Snapshot mensal: **um** registro por mês/ano, **atualizado** no mês (não congelado no 1º acesso).

**R15 – Previsão do mês (fim do mês = F).**
`saldoLivrePrevisto = Σ saldos + Σ CREDITO NORMAL !pago (sem cartão, data<=F) − Σ DEBITO NORMAL !pago (sem cartão, data<=F, inclui atrasadas) − Σ faturas pendentes com vencimento<=F`.
`margem = saldoAtual > 0 ? saldoLivrePrevisto/saldoAtual : (saldoLivrePrevisto > 0 ? 1 : 0)`; ≥40% "Mês seguro", ≥5% "Atenção", senão "Risco".

**R16 – Despesas fixas (recorrências).** Para cada regra e cada mês desde `ultimaDataLancamento` (ou só o mês corrente se nunca lançou) até o mês atual (máx. 12):
`data = dia clampado ao mês`; se `data <= hoje` e o mês ainda não foi lançado ⇒ cria despesa `pago=false, natureza=NORMAL` e atualiza `ultimaDataLancamento`. Dia 29–31 vira o último dia dos meses curtos.
Se a regra tem `cartaoId` (física ou virtual; cartão DÉBITO gera direto na conta, R42), cada ocorrência é uma **compra no cartão** (`cartaoId` preenchido, `conta` = conta do cartão, `pago=false`, consome limite — R4/R18); sem `cartaoId`, é lançamento na conta (`conta`). Uma regra tem conta **ou** cartão como origem do pagamento.

**R17 – Saúde financeira.** `consumo = receitas > 0 ? despesas/receitas : (despesas > 0 ? 1 : 0)`; `≥0,9` PERIGO · `≥0,7` ALERTA · senão SAUDÁVEL.
`variacaoGastos = despesasAnt > 0 ? (desp − despAnt)/despAnt·100 : 0`.

## 5. Sincronização app ⇄ Firestore (modo FIREBASE)

Sincronização em 3 vias com tabela `sync_meta(colecao,id,hash)` no Room: linha local sem meta/hash divergente = alterada localmente (enviar);
meta sem linha local = excluída localmente (apagar na nuvem); doc remoto sem meta nem linha local = novo remoto (baixar).
Itens "sujos" localmente não são sobrescritos por snapshot remoto. Depois de aplicar mudanças remotas o app roda `recalcularTudo()` (R3/R4).
O portal, ao gravar lançamentos, deve atualizar também os caches `contas.saldo` e `cartoes.limiteDisponivel`.


## 6. Pacote "dia a dia" (R19–R34)

Campos novos: `metas.dataAlvo` (ms ou null), `despesas.autor` (texto livre: quem lançou; null em dados antigos), coleção `lixeira`.
Todas as funções abaixo são **puras** (sem I/O), implementadas igualmente em Kotlin (`domain/Analises.kt`) e TypeScript.

**R19 – Filtro de relatório.** `{inicio, fim, contas[], cartoes[], categorias[], tipo: DESPESA|RECEITA|TODOS, pago: null|true|false, texto, incluirInternos=false}`.
Seleciona despesas com `data` em `[inicio, fim]`; só `natureza == NORMAL` (salvo `incluirInternos`).
`tipo=DESPESA` → `DEBITO` (inclui compras de cartão) menos estornos de cartão; `RECEITA` → `CREDITO` sem cartão; `TODOS` → ambos.
`cartoes[]`: ids; escolher um cartão **físico** inclui os virtuais dele (grupo, R18); escolher só um virtual filtra apenas ele.
`contas[]` compara `despesa.conta`. `categorias[]` e `texto` ignoram maiúsculas/acentos/espaços (`texto` busca em descrição e categoria).
**Resultado:** `itens` (data desc), `quantidade`, `total` (DESPESA = despesas líquidas; RECEITA = receitas; TODOS = receitas − despesas), `media = total/quantidade`, `maior`,
`porCategoria[{nome,total,percentual}]` (desc), `porMes[{mes:"AAAA-MM",total}]` (asc) e `anterior` = mesmo cálculo no período imediatamente anterior de mesma duração, com `variacaoPercentual = anterior.total>0 ? (total−anterior.total)/anterior.total·100 : null`.

**R20 – Modelos de relatório.** Extrato por conta · Fatura do cartão (mês) · Gastos por categoria · Receitas × despesas do mês · Evolução do patrimônio (snapshots R14) · Anual para IR (categorias Saúde e Educação, `tipo=DESPESA`, ano inteiro).
Exportação: **PDF** (A4, várias páginas, cabeçalho com filtros aplicados, totais, tabela e gráfico), **PNG** (imagem-resumo: título, filtros, totais e gráfico de categorias) e **CSV** (`;`, UTF-8 com BOM, valores `1234,56`).

**R21 – Sugestão de categoria.** Normaliza a descrição (minúsculas, sem acento, sem dígitos/pontuação, tokens com ≥3 letras). Para cada lançamento NORMAL passado com categoria: similaridade = 1 se descrições normalizadas iguais; senão Jaccard dos tokens. Considera só similaridade ≥ 0,5; soma por categoria; devolve a de maior soma (empate: a mais recente). Sem candidatos → `null`.

**R22 – Alertas de orçamento.** Limiares 80% e 100% (R13). Notificar uma única vez por `(categoria, AAAA-MM, limiar)`; ao cruzar 100% não repetir o aviso de 80%.

**R23 – Duplicar e repetir.** *Duplicar* copia um lançamento NORMAL com `data = agora`, `pago = false`, novo `id`, sem `grupoId`. *Repetir(n, intervalo, unidade ∈ DIAS|SEMANAS|MESES)* cria `n` cópias a partir da data base: ocorrência `k` (1..n) em `base + k·intervalo` (MESES usa a regra sem deriva de R8), `pago = false`, `grupoId = "rep:<uuid>"`. Compra de cartão mantém o `cartaoId` (consome limite). Transferência/fatura/aporte não duplicam.

**R24 – Busca global.** Termo normalizado (sem acento/caixa). Casa em descrição, categoria, conta/banco, nome de cartão e de meta; valor (`"123,45"` ou `"123.45"`, igualdade em centavos) e data (`dd/MM/aaaa` ou `dd/MM`).

**R25 – Reserva de emergência.** `mediaDespesas3m` = média de `despesasTotal` (R5) dos 3 meses completos anteriores (meses sem nenhum lançamento são ignorados; sem dados → `null`).
`liquidez = Σ saldos das contas + Σ valorAtual dos investimentos do tipo "Renda Fixa"`. `meses = liquidez / mediaDespesas3m`. Meta = 6 meses: `faltante = max(0, 6·media − liquidez)`. Status: `< 3` CRITICO · `< 6` ATENCAO · senão OK.

**R26 – Assinaturas e gastos recorrentes.** Todas as regras de `despesasFixas` (origem FIXA) **mais** as detectadas: lançamentos DEBITO NORMAL agrupados pela descrição normalizada (sem sufixo "(i/n)" e sem dígitos) que ocorram em ≥ 3 meses distintos dentro dos últimos 6 meses, com a última ocorrência há ≤ 45 dias, valores com variação ≤ 10% da mediana e dispersão do dia do mês ≤ 5 (origem DETECTADA; ignorar descrições já cobertas por uma regra fixa). Cada item: `nome, valorMedio, ultimaData, categoria, origem, totalMensal = valorMedio, totalAnual = 12·valorMedio`.

**R27 – Meta com prazo.** `restante = max(0, objetivo − guardado)`; `mesesRestantes = max(1, meses inteiros (arredondado p/ cima) entre hoje e dataAlvo)`; `aporteMensalNecessario = restante / mesesRestantes`.
`ritmo` = média mensal dos aportes dessa meta (lançamentos APORTE_META cuja descrição é `"Aporte: <nome>"`) nos últimos 3 meses. Status: `CONCLUIDA` (guardado ≥ objetivo) · `ATRASADA` (dataAlvo < hoje e não concluída) · `NO_RITMO` (ritmo ≥ necessário) · `ABAIXO` (caso contrário). Sem `dataAlvo` → só `restante`.

**R28 – Simulador parcelar × à vista.** Entradas: `valorAVista`, `n`, `valorParcela`, `taxaMensal` (rendimento do dinheiro, padrão 1,0% a.m.), `entrada` (padrão 0). `VP = entrada + Σ_{k=1..n} valorParcela/(1+taxa)^k`. `parcelarVale = VP < valorAVista`. `diferenca = valorAVista − VP` (positivo = parcelar economiza em valor presente). `jurosImplicitosMensais` = taxa `i` tal que `valorAVista − entrada = Σ valorParcela/(1+i)^k` (bisseção em [0, 1]; `0` se `n·parcela + entrada ≤ aVista`).

**R29 – Regra 50/30/20.** Sobre `receitasRealizadas` do mês: *Necessidades* (alvo 50%) = despesas NORMAIS DEBITO nas categorias {Supermercado, Saúde, Educação, Transporte, Combustível, Oficina, Casa, Aluguel, Moradia, Contas, Luz, Água, Internet}; *Poupança* (alvo 20%) = Σ APORTE_META − Σ RESGATE_META + despesas da categoria Reserva; *Desejos* (alvo 30%) = demais despesas NORMAIS DEBITO. Cada grupo: `valor, percentual (valor/receitas, 0 sem receita), alvo, status` (`OK` se Necessidades ≤ 50 / Desejos ≤ 30 / Poupança ≥ 20; senão `ACIMA`/`ABAIXO`).

**R30 – Melhor dia de compra.** `melhorDia = diaFechamento + 1`; se `diaFechamento ≥ último dia do mês` → dia 1 do mês seguinte. `prazoMaximoDias` = dias entre a data `melhorDia` e o vencimento da fatura que a compra cairá (R6).

**R31 – Lixeira (30 dias).** Excluir um lançamento pela UI grava na `lixeira` o JSON do documento (`payload`) e depois exclui (transferência: um registro por lado, mesmo `grupoId`; fatura/saldo inicial não vão para a lixeira). **Restaurar** recria a despesa (mesmo `id`; se ocupado, novo id), restaura o par da transferência e recalcula saldo/limite. Itens com `excluidoEm` > 30 dias são removidos ao abrir o app/portal. Excluir conta/cartão/meta **não** usa a lixeira.

**R32 – Conta compartilhada.** Os dados continuam em `users/{donoUid}/…`. O dono concede acesso criando `users/{donoUid}/membros/{membroUid}` = `{uid, email, criadoEm}`; quem tem esse documento lê/escreve todas as coleções do dono (mas **não** altera `membros`). Descoberta sem busca de usuários: cada usuário, ao logar, grava `perfis/{emailMinusculo}` = `{uid, email}`; o dono convida lendo `perfis/{email}` e criando `perfis/{email}/convites/{donoUid}` = `{donoUid, donoEmail, criadoEm}` (junto com o documento de `membros`). O convidado vê seus convites e escolhe "usar os dados de <dono>": toda a sincronização passa a usar `donoUid` como raiz (histórico de sync é zerado ao trocar de raiz). Lançamentos novos gravam `autor` (nome do usuário). Remover membro apaga `membros/{uid}` e o convite.
Regras Firestore: ver `portal/firestore.rules` (a regra ampla de `users/{uid}/**` exclui a coleção `membros`).

**R33 – Backup automático (app).** Semanal, JSON v2 em armazenamento privado do app (`files/backups/`), mantendo os 4 mais recentes; opção na tela de Configurações; restauração a partir da lista.

**R34 – PIN (portal).** Bloqueio opcional por PIN de 4–8 dígitos, guardado como hash PBKDF2 no `localStorage`; bloqueia a UI após inatividade configurável (padrão 5 min).


## 7. Conciliação de extratos bancários (R35–R39)

Campos novos em `despesas`: `fitid` (identificador da transação no extrato do banco; `null` se nunca conciliada) e `conciliadoEm` (epoch ms da conciliação; `null` = não conciliada).
O app Android só **preserva** esses campos (sincronização/backup); a importação e a conciliação são feitas no portal. O arquivo do extrato é lido **somente no navegador** — nada do arquivo é enviado a servidor; só os lançamentos resultantes são gravados.

**R35 – Leitura de extratos.** Formatos: **OFX** (v1 SGML e v2 XML; `STMTTRN`: `TRNTYPE, DTPOSTED, TRNAMT, FITID, NAME, MEMO, CHECKNUM`; `BANKACCTFROM/ACCTID`, `CREDITCARDMSGSRSV1`, `LEDGERBAL`) e **CSV** (delimitador `;` `,` ou tab detectado; decimal `1.234,56` ou `1,234.56`; datas `dd/MM/aaaa`, `dd/MM/aa`, `aaaa-MM-dd`; cabeçalho detectado; colunas mapeáveis: data, descrição, valor **ou** débito+crédito, saldo opcional; opção "inverter sinal" — CSV de fatura de cartão costuma trazer compra positiva).
Decodificação: UTF-8; se aparecerem `\uFFFD`, reler como Windows‑1252/ISO‑8859‑1 (bancos brasileiros). OFX: `DTPOSTED` `AAAAMMDD[HHMMSS[.XXX][fuso]]` → data **local** do dia (sem deslocar o dia pelo fuso).
Cada linha vira `TransacaoBanco {fitid, data(ms, meio‑dia local), valor (com sinal: entrada +, saída −), descricao, saldo?}`. Sem `fitid` no arquivo: `fitid = "h:" + hash(data|centavos|descricao normalizada|n)` em que `n` é a ordem da ocorrência idêntica no arquivo (duas linhas iguais no mesmo dia continuam distintas e estáveis em reimportações).

**R36 – Destino.** O usuário escolhe a **conta** (extrato corrente; compara com lançamentos `conta == conta.conta && cartaoId == null`) ou o **cartão** (extrato/fatura; compara com lançamentos do **grupo** do cartão, R18). Sugerir automaticamente a conta pelo `ACCTID` do OFX quando coincidir com `contas.conta`. Se o arquivo traz saldo final (`LEDGERBAL` ou coluna de saldo), mostrar **diferença de saldo**: `saldoBanco − saldoApp(até a data)` onde `saldoApp(até d)` = R3 considerando só lançamentos com `data ≤ d`.

**R37 – Casamento (matching).** Candidato = lançamento do destino, ainda **não conciliado** (`conciliadoEm == null`), com mesma direção (valor do extrato < 0 ⇔ DEBITO; > 0 ⇔ CREDITO; em cartão a compra do extrato é DEBITO), qualquer `natureza` (assim transferências e pagamento de fatura também conciliam) e `|data − dataBanco| ≤ janela` (padrão **3 dias**; cartão padrão 5).
Valor: **exato** em centavos → `tipo=EXATO`; diferença ≤ `max(1,00; 2%)` do valor do extrato → `tipo=DIFERENCA` (tarifas/IOF/arredondamento; exige confirmação do usuário).
`score = 0,55·valor + 0,30·proximidadeData + 0,15·similaridadeDescricao` com `valor = 1` (EXATO) ou `0,6` (DIFERENCA); `proximidadeData = 1 − |dias|/(janela+1)`; similaridade = Jaccard de tokens (`Texto.tokens`, R21; 1 se descrições normalizadas iguais).
Atribuição **um‑para‑um**: ordenar todos os pares por `score` desc (empate: menor |dias|, depois id menor) e atribuir greedy sem repetir linha do extrato nem lançamento.
Classificação: `fitid` já usado por lançamento conciliado do mesmo destino → **DUPLICADO**; `score ≥ 0,80` e único melhor candidato → **AUTOMATICO**; demais com candidato → **SUGERIDO**; sem candidato → **SO_NO_EXTRATO**. Lançamentos do destino dentro do período do arquivo (`[min data − janela, max data + janela]`) sem par → **SO_NO_APP** (informativo: pode estar pendente/futuro/esquecido no banco).

**R38 – Ações e mescla.** Por linha:
- **Conciliar** (AUTOMATICO/SUGERIDO/DIFERENCA): grava `fitid` e `conciliadoEm = agora` no lançamento. Opções de mescla (independentes): usar **data do banco**, usar **valor do banco** (obrigatório na prática para DIFERENCA, o usuário confirma), usar **descrição do banco**, **marcar como pago** (padrão ligado para lançamentos de conta; em cartão **não** marca pago — a fatura é quitada por R7). Ao mudar valor/data/pago ⇒ recalcular saldo/limite (R3/R4).
- **Criar lançamento** (SO_NO_EXTRATO): cria `natureza=NORMAL`, `pago=true` (conta) ou `pago=false` (cartão), `data` e `valor` do extrato, `descricao` do extrato (limpa), `categoria` sugerida por R21 (ou "Outros" para escolher), `autor = "Importação"`, `fitid`, `conciliadoEm`. Compra parcelada reconhecida por "(i/n)"/"PARC i/n" no texto não é expandida; cria só a parcela do extrato.
- **Ignorar**: nada muda (a linha continua aparecendo como pendente numa nova importação). **Marcar como ignorada permanentemente** não existe (evita estado extra).
- **Desfazer conciliação**: zera `fitid`/`conciliadoEm` (não reverte valor/data já mesclados).
Aplicar é **atômico** (um único `aplicarConciliacao(plano)` → recalcula e persiste uma vez) e devolve um **plano inverso** para **desfazer o último lote** (restaura campos alterados e remove lançamentos criados).

**R39 – Relatório da conciliação.** Resumo: contagens por classe, soma das entradas e saídas do extrato × do app no período, diferença de saldo (R36). Exportar como CSV (`;`, BOM) com uma linha por transação do extrato: `data;descricao;valor;situacao;lancamento_id;acao`.

## R40 — Antecipação de pagamento (portal)

Sem campos novos: usa só `despesas` e `despesasFixas` já existentes (compatível com o app).

- **Dívida quitada** `E = valorPago + desconto`, consumida na ordem dos lançamentos escolhidos; o desconto é rateado em centavos (o último leva o resto).
- Lançamento quitado por inteiro: `valor = pago - desconto rateado`, `pago = true`, `data` = data do pagamento; a descrição guarda o vencimento original (`… (antecipada de dd/mm/aaaa, desc. x,xx)`). O desconto não é receita nem despesa.
- Pagamento parcial: o último lançamento é dividido — nasce um lançamento pago (`… (adiantamento)`) e o original continua em aberto com `valor - quitado`, mesmo vencimento.
- Só despesas `NORMAL` em conta (compras de cartão são pagas pela fatura), todas da mesma conta.
- Recorrência: paga o mês futuro criando a ocorrência paga com `grupoId = fixa:<id>:<AAAA-MM>`; o R16 (idempotente por `grupoId`) não a lança de novo.

**R41 – Saldo por cartão no grupo.** O limite total continua compartilhado (R18; `limiteDisponivel` armazenado segue sendo o do grupo), mas cada cartão (físico e virtuais) mostra o seu próprio uso.
- `limiteProprio: double|null` (opcional, default `null`): teto de gasto do cartão dentro do limite compartilhado. Deve ser `> 0` e `<= limiteTotal` do principal; `null`/`0`/vazio = sem teto. Erro: "O limite próprio deve ser maior que zero e não pode passar do limite total do cartão físico." Vale para físico e virtual.
- Ao editar o físico, a propagação aos virtuais **não** sobrescreve o `limiteProprio` dos virtuais; se o `limiteTotal` do físico baixar abaixo de algum `limiteProprio` do grupo, é erro.
- `saldoDoCartao(cartao)`: `usado` = Σ DEBITO − Σ CREDITO das despesas `!pago` **apenas deste cartão** (centavos, mínimo 0); `disponivelGrupo` = limite disponível do grupo (R18); `disponivel = limiteProprio != null ? min(limiteProprio − usado, disponivelGrupo) : disponivelGrupo`; `razao = usado / (limiteProprio ?? limiteTotal do principal)` (0 se denominador 0).
- UI: o topo do grupo é "Limite compartilhado"; abaixo, uma linha por cartão (físico primeiro) com usado próprio, percentual, barra e disponível. Nova compra acima do `limiteProprio` apenas **avisa** (não bloqueia).

## R42 — Vínculo editável, modalidade da compra no cartão e ajuste de saldo (portal)

Sem campos novos nem mudança de formato de dados (compatível com o app).

**A) Vínculo físico↔virtual editável.** `editarCartao` aceita `cartaoPrincipalId: number | null`.
- `número`: o cartão vira/continua virtual e compartilha o saldo (limite) daquele físico. O alvo deve existir, ser físico (`cartaoPrincipalId == null`) e diferente do próprio cartão; o cartão editado **não pode ter virtuais próprios**. Ele herda do físico `limiteTotal`, `contaId`, `diaFechamento`, `diaVencimento` e `tipo`; as despesas e recorrências dele passam para a `conta` do físico; `limiteProprio` é revalidado (`> 0` e `<= limiteTotal` do físico, senão erro R41) e `limiteDisponivel` dos grupos antigo e novo é recalculado (R4/R18).
- `null` (em um virtual): vira físico independente, mantendo os valores herdados atuais; `limiteProprio` vira `null` (a menos que o patch informe um); os dois grupos são recalculados.
- O **físico é a base**: o limite/saldo do grupo é sempre o do físico principal. Virtual órfão (aponta para cartão inexistente) é corrigido por esta edição.
- UI: o formulário (também na edição) tem "Compartilha o saldo do cartão físico" com "Nenhum (cartão físico independente)" + os físicos; ao escolher um físico, os campos herdados ficam travados com os valores dele. Cada virtual mostra "Compartilha o saldo de <físico>".

**B) Modalidade da compra no cartão.** `modalidadeDaCompra(cartao, pedida)`: tipo `CRÉDITO` → sempre crédito; `DÉBITO` → sempre débito; `MÚLTIPLO` → usa `modalidade` (`CREDITO`|`DEBITO`, padrão `CREDITO`).
- Crédito: como antes (R4/R6/R18; compra em aberto, consome limite, entra na fatura).
- Débito: **não** consome limite nem entra na fatura; vira lançamento direto da conta do cartão (`cartaoId = null`, `conta` = conta do cartão, `pago = data <= agora`, parcelas forçadas a 1, `tipo` conforme o input).
- Vale em `adicionarLancamento` e em `editarLancamento` ao trocar o cartão/modalidade (`modalidade` no patch). Recorrência (R16) vinculada a cartão `DÉBITO` gera as ocorrências direto na conta (`cartaoId = null`, `pago = false` como toda recorrência em conta); `CRÉDITO` e `MÚLTIPLO` continuam no cartão (crédito). Dados existentes não são migrados.
- UI: com entrada "Cartão", o seletor Crédito/Débito aparece só para cartões `MÚLTIPLO`; no débito mostra "Compra no débito: sai direto da conta", esconde parcelas e o aviso de limite próprio (R41) vale só no crédito.

**C) Ajuste de saldo da conta.** `ajustarSaldoConta(ds, { conta, saldoReal, data?, observacao? })`: `diff = saldoReal − saldo(conta)` (R3, em centavos). `diff == 0` → erro "O saldo já confere com o informado."; senão cria 1 despesa `natureza = AJUSTE`, `tipo = CREDITO` se `diff > 0` senão `DEBITO`, `valor = |diff|`, `pago = true`, `cartaoId = null`, `categoria = "Ajuste de saldo"`, `descricao = "Ajuste de saldo (conferido com o banco)"` (+ observação), `data = data ?? agora`. Retorna `{ ajuste, diferenca }`.
- `AJUSTE` entra no saldo da conta (R3) mas **não** em receitas/despesas, orçamentos, relatórios, fluxo e análises (R5/R13: só `NORMAL`). Pode ser excluído para desfazer (aparece como "Ajuste" no extrato).
- UI (Contas): botão "Ajustar saldo" por conta; modal com "Saldo no sistema", "Saldo real no banco" e prévia da diferença (+/−) antes de confirmar.

**R42b) Vínculo da compra no débito com o cartão (convenção de `grupoId`, sem campo novo).** Para a compra no débito (R42-B) continuar visível na tela do cartão, o lançamento direto da conta (`cartaoId = null`) recebe `grupoId = "debito:<id do cartão usado>"` (físico **ou** virtual; débito não parcela, então nunca conflita com `parc:`).
- Gravação: `adicionarLancamento` e `editarLancamento` (ao converter para débito; trocar de cartão de débito reaponta; voltar para crédito, mover para outra conta ou excluir o cartão remove/ajusta o vínculo; excluir um virtual reaponta `debito:<virtual>` para o físico). Um `grupoId` existente de outro tipo (`parc:`, `fixa:`, `rep:`…) nunca é sobrescrito.
- Helpers puros (calc.ts): `cartaoDeDebito(d)` (id do prefixo ou `null`), `grupoIdDebito(id)`, `debitosDoCartao(cartoesDoGrupo, despesas, mes, ano, somenteCartaoId?)` (NORMAL, `cartaoId` nulo, mês/ano pela data civil do lançamento, mais recentes primeiro), `totalDebitos(itens)`.
- UI (Cartões): abaixo da fatura, "Compras no débito (saem direto da conta)" lista esses lançamentos do mês exibido (respeita o filtro por cartão) com subtotal. **Não** entram em total/em aberto da fatura, limite (R4/R18) nem "Pagar fatura"; continuam no saldo da conta (R3) e nos relatórios como despesa normal.
- `debito:` **não** é parcelamento nem grupo: "excluir todas as parcelas" só existe para `parc:`; exclusão/edição atuam sobre um único lançamento. Conciliação, backup e relatórios apenas preservam o `grupoId`.
- Limitações: recorrências (R16) em cartão `DÉBITO` geram `fixa:<id>:<AAAA-MM>` (idempotência) e ficam **sem** vínculo; lançamentos de débito anteriores a esta regra não são migrados; duplicar/repetir troca o `grupoId` (perde o vínculo).

## R43 — Conciliação em lote de vários arquivos (portal)

Sem campos novos nem mudança de formato (compatível com o app). Estende R35–R39; a implementação pura está em `lib/conciliacao/lote-multiplo.ts`, sem dependência de React/store (o dataset e a aplicação são injetados).

- **Fila e leitura.** Vários OFX/CSV de uma vez (limite de 25 MB cada). Leitura assíncrona, um arquivo por vez, cedendo ao navegador entre etapas. Arquivo com mesmo nome e tamanho já na fila é ignorado com aviso. CSV sem mapeamento de colunas detectado nem lembrado fica "Precisa de revisão" até o usuário mapear as colunas. O destino de cada arquivo é sugerido (R36) e editável.
- **Opções.** "Conciliar automáticos" é sempre ligado. Opcionais (padrão desligados): incluir **sugeridos de alta confiança** (SUGERIDO com valor EXATO, `score >= 0,70` e score maior que o do melhor concorrente em mais de 0,05) e **criar lançamentos que só estão no extrato** (categoria por R21, senão "Outros"). Janela e tolerância: padrão por destino (R37) ou valor único para o lote. A prévia por arquivo não aplica nada: simula o lote em sequência sobre uma cópia do dataset.
- **Ordem e semântica.** Os arquivos são processados **em sequência, na ordem da fila**. Para cada arquivo: lê o dataset **atual**, `casar` (R37) contra os lançamentos do destino, `montarPlano` (automáticos [+ sugeridos altos] [+ criar]) e aplica via `aplicarConciliacao` (R38). Assim, o arquivo B casa contra o estado já atualizado por A: lançamentos conciliados não são reutilizados e `fitid` repetido entre arquivos de períodos sobrepostos vira **DUPLICADO** (nunca gera lançamento duplicado). Ações com `fitid` repetido dentro do mesmo plano são descartadas.
- **Atomicidade.** Atômica **por arquivo** (R38). Se a aplicação de um arquivo falha (erro ou exceção), nada dele é aplicado, o erro fica registrado nele e os demais continuam. **Cancelar** para após o arquivo corrente: o já aplicado permanece (e pode ser desfeito); os restantes ficam "Cancelado".
- **Contadores.** Por arquivo e somados no lote: conciliados, criados, valores/datas atualizados, marcados como pagos (de `ResumoAplicacao`), duplicados ignorados e **pendentes** = transações − duplicadas − conciliadas − criadas. Taxa de conciliação = `(conciliados + criados + duplicados) / transações`. Percentual geral = `(arquivos concluídos + fração da etapa) / total`, com frações LENDO 10%, COMPARANDO 40%, APLICANDO 75%, CONCLUÍDO 100%.
- **Desfazer.** Cada arquivo aplicado guarda seu plano inverso. "Desfazer lote inteiro" aplica os inversos em **ordem inversa** (último arquivo primeiro), devolvendo o dataset ao estado anterior ao lote. Vale enquanto a aba estiver aberta (estado em memória, `useUltimoLote.multiplo`); o `ultimo` (R38) é zerado ao rodar um lote.
- **Relatório consolidado.** CSV (`;`, BOM): `arquivo;data;descricao;valor;situacao;lancamento_id;acao`, uma linha por transação de cada arquivo (`acao` = `conciliar`, `criar` ou `nenhuma`). `csvRelatorio` (R39, por arquivo) não mudou.
- **Revisar pendências.** Abre o fluxo detalhado (R38) arquivo a arquivo, só para os que terminaram com SUGERIDO/SO_NO_EXTRATO pendentes, recalculando `casar` contra o dataset atualizado. O modo "Revisar um por um" continua disponível.

## R44 — Parcelas em andamento, limite comprometido e pagamento seletivo da fatura (portal)

Sem campos novos nem mudança de formato (compatível com o app). Confirma e estende R4/R7/R8/R18: compra parcelada no cartão **já** cria as N parcelas em aberto (`pago=false`) e o limite do grupo desconta o total (ex.: 1.200 em 12x com limite 5.000 → disponível 3.800; pagar a fatura de 1 mês devolve só aquela parcela). As regras abaixo cobrem onde isso não acontecia ou não ficava visível.

**A) Parcelas em andamento.**
- **Conciliação de cartão (R38, criar).** Se a transação SO_NO_EXTRATO de um **cartão** é compra (valor < 0) e indica parcela `i/n` com `i < n` ("(3/10)", "PARC 3/10" ou `i/n` solto como "COMPRA 03/10" — este só quando **não** parece a data da transação: dd/mm a ≤ 10 dias da data do extrato — e com `n ≥ 2`, `i ≤ n`), a ação CRIAR pode também lançar as parcelas `i+1..n`: `pago=false`, `cartaoId` do destino, mesmo `grupoId = "parc:…"`, **valor igual ao da parcela do banco** (não divide), data = `somaMeses(data do banco, j−i)` (R8, sem deriva), descrição `"<base> (j/n)"` (a da parcela do banco passa a `"<base> (i/n)"`, sem o marcador original), mesma categoria, `autor = "Importação"`. **Só a parcela do banco recebe `fitid`/`conciliadoEm`** (idempotência por fitid; reimportar vira DUPLICADO e não recria nada).
- **Sem duplicar.** Parcela futura não é criada se o grupo do cartão já tem lançamento de mesma descrição normalizada (sem o marcador "(j/n)"), mesmo valor em centavos e mesmo mês/ano; se já existe lançamento do mesmo grupo `parc:`, a parcela do banco reaproveita esse `grupoId`.
- **Opção** "Lançar parcelas restantes (reserva o limite)": padrão **ligado** em Revisão (por linha criada, visível só em cartão com parcela i<n) e no modo lote (`OpcoesLote.parcelasRestantes`, padrão `true`; só tem efeito junto de "Criar lançamentos"). Não vale para conta, estornos (crédito) nem parcela final (i = n).
- **Atomicidade/inverso (R38/R43).** Tudo no mesmo `aplicarConciliacao`; `PlanoInverso.remover` lista primeiro os ids criados a partir do extrato (na ordem das ações CRIAR) e depois as parcelas restantes, então desfazer remove todas. `ResumoAplicacao.parcelasRestantes` conta as extras; `criados` continua contando só as do extrato.
- **Lançamento manual com "Parcela atual" (k).** `adicionarLancamento({ parcelas: N, parcelaAtual: k })` em cartão de **crédito**: o valor informado continua sendo o **TOTAL da compra**; as parcelas são `dividirParcelas(total, N)` (R8) e só `k..N` são criadas, a parcela `k` na data informada e as seguintes mês a mês, descrição `"<desc> (j/N)"`, `grupoId` comum, `pago=false`. Validação: `1 ≤ k ≤ N`; `k > 1` só em cartão de crédito. Padrão `k = 1` (comportamento anterior). Todas as parcelas criadas consomem o limite.

**B) Limite comprometido.** `comprometimentoCartao(cartao, cartoes, despesas, agora)` (puro, por grupo R18) devolve `faturaAtual` (em aberto no ciclo corrente, R6), `anteriores` (em aberto de ciclos já passados), `parcelasFuturas` (ciclos posteriores), `emAbertoTotal` (= limite usado, R4), `limiteTotal`, `disponivel` e `liberacaoPorFatura: {mes, ano, valor, vencimento}[]` em ordem cronológica (valor = DEBITO − CREDITO em aberto da fatura, só `> 0`: quanto de limite volta ao pagar aquela fatura). `faturasEmAberto` lista os itens em aberto por fatura. UI (Cartões): barra empilhada fatura atual × parcelas futuras × disponível, com legenda e valores, e bloco "Limite que volta ao pagar".

**C) Pagamento seletivo.** `pagarItensFatura(ds, { cartaoId, itemIds }, ctx)` (qualquer cartão do grupo; antecipa faturas futuras):
- valida: ao menos um item; todos existem, pertencem ao grupo (físico + virtuais) e estão em aberto; líquido = Σ DEBITO − Σ CREDITO dos itens em centavos; `≤ 0` → "Não há valor a pagar nos itens selecionados.".
- marca os itens como `pago=true` e cria 1 despesa `PAGAMENTO_FATURA` (`DEBITO`, `pago`, `cartaoId=null`, conta do cartão, `data = agora`, categoria "Cartão") com o valor líquido. Saldo da conta cai pelo líquido (R3) e o limite volta pelo mesmo valor (R4); pagar o restante depois fecha a fatura sem resíduo de centavos.
- descrição/`grupoId`: todos os itens de **uma só** fatura e **todos** os itens em aberto dela → `"Fatura <cartão> MM/AAAA"` e `fatura:<principal>:<AAAA-MM>` (idêntico ao R7); só alguns itens de uma fatura → `"Fatura <cartão> MM/AAAA (parcial)"` e `fatura:<principal>:<AAAA-MM>:p<ts>`; itens de várias faturas → `"Fatura <cartão> (itens selecionados)"` e `fatura:<principal>:multi:p<ts>` (`ts` = agora, incrementado até ser único). Nada no portal lê o `grupoId` do pagamento (a exclusão é bloqueada por `natureza`), então o sufixo é só informativo/único.
- `pagarFatura(cartao, M, Y)` (R7) continua igual e delega a `pagarItensFatura` com os itens em aberto da fatura (erro "Não há valor em aberto nesta fatura." preservado).
- UI (Cartões): "Pagar fatura" abre modal com atalhos (Pagar toda a fatura do mês / Selecionar itens / Antecipar parcelas futuras), lista de itens em aberto por fatura com checkbox de grupo (estado indeterminado) e subtotal, "Selecionar tudo", "Só esta fatura", "Limpar", total, limite restaurado e saldo da conta após o pagamento (aviso, sem bloquear, se negativo) e confirmação antes de pagar. Abre com os itens da fatura exibida marcados (= comportamento antigo em 1 clique + confirmação).

## R45 — Conferência e recálculo de saldos (portal)

Sem campos novos nem mudança de formato (compatível com o app). Confirma R3/R4/R18 e acrescenta uma auditoria somente leitura mais uma ação de recálculo; mesma semântica no app.

- **Por que os números parecem não bater.** Entradas (R5 `receitasRealizadas`) contam só receitas **já recebidas**, enquanto Saídas (`despesasTotal`) incluem **pendentes e parcelas futuras**; já o saldo da conta (R3) é o histórico total de lançamentos **pagos, sem cartão**, e inclui saldo inicial, transferências, aportes/resgates de meta, pagamentos de fatura e ajustes. Logo `Entradas − Saídas ≠ Saldo`. Os KPIs não mudaram; o Dashboard apenas separa em legenda "pagas R$ X · a pagar R$ Y" sob Despesas.
- **`auditarSaldos(ds, agora)`** (puro, `lib/finance/conferencia.ts`; não altera nada). Devolve:
  - por **conta**: `saldoGravado` (cache), `saldoCalculado` (R3), `diferenca = gravado − calculado`, `ok`, **decomposição em centavos** dos lançamentos pagos sem cartão da conta (saldo inicial; receitas NORMAL; despesas NORMAL; pagamentos de fatura; transferências recebidas/enviadas; aportes e resgates de meta; ajustes; "outros" para natureza desconhecida) cuja soma é exatamente `toCents(saldoCalculado)`, e **pendências** (receitas previstas; despesas pendentes atrasadas = `data < início do dia`, a vencer em 30 dias, futuras; compras de cartão em aberto da conta);
  - por **grupo de cartão** (R18): `limiteDisponivel` gravado em cada cartão do grupo × `limiteGrupo` calculado, e `faturaAtual` / `anteriores` / `parcelasFuturas` / `emAbertoTotal` via `comprometimentoCartao` (R44);
  - **totais** de todo o histórico (`kpisTotal`): entradas realizadas/previstas, saídas pagas/pendentes/total, resultado, saldo das contas e a frase `explicacao`;
  - **inconsistências** (nunca apaga nem corrige): conta inexistente, `cartaoId` inexistente, valor `≤ 0` ou não finito, natureza desconhecida, ids duplicados (lançamentos, contas, cartões), cartão virtual com principal inexistente (severidade `erro`) e grupo `parc:` com menos parcelas lançadas que o `n` de "(j/n)" (`info`: pode ser parcela inicial > 1 ou exclusão).
- **`recalcularSaldos(ds)`** → `{ ds, correcoes[] }`: aplica `recalcularTudo` (R3/R4/R18: regrava `contas.saldo` e `cartoes.limiteDisponivel`) e lista `{tipo: conta|cartao, id, rotulo, antes, depois}` só do que mudou. Idempotente: a segunda execução retorna 0 correções e o mesmo dataset. Lançamentos nunca são alterados.
- **UI (portal).** Página `/conferencia` ("Conferência de saldos"), linkada em Configurações e no Dashboard: cartões por conta (gravado × calculado, selo OK/DIVERGENTE, decomposição com total, pendências), limites de cartão, totais com explicação, lista de inconsistências e botão "Recalcular saldos agora" com confirmação e resultado "N saldo(s) e M limite(s) corrigidos" ou "Tudo conferido". Ações `acoes.conferirSaldos` e `acoes.recalcularSaldos` (esta persiste como as demais).

## R46 — Fatura já paga por fora: apenas marcar como paga (portal)

Sem campos novos nem mudança de formato (compatível com o app). Complementa R7/R44.

- **Quando usar.** A fatura (ou parte dela) já foi paga fora do portal: o débito já está no extrato/saldo real da conta, ou foi lançado/conciliado de outra forma. Criar um `PAGAMENTO_FATURA` de novo debitaria a conta duas vezes.
- **Operação.** `marcarItensFaturaComoPagos(ds, { cartaoId, itemIds })` (qualquer cartão do grupo, R18). Mesmas validações de `pagarItensFatura`: ao menos um item; todos existem, pertencem ao grupo (físico + virtuais) e estão em aberto (item já pago → "Algum item selecionado já está pago."). Marca os itens `pago=true` e recalcula limite/finaliza como as demais operações (limite do grupo restaurado pelo líquido dos itens: Σ DEBITO − Σ CREDITO, em centavos).
- **O que NÃO faz.** Não cria `PAGAMENTO_FATURA`, não cria nenhum lançamento, não altera o saldo de nenhuma conta. Aceita qualquer líquido, inclusive `≤ 0` (fatura só com estornos), desde que haja itens.
- **Combinações.** Pode-se quitar parte por fora e pagar o resto normalmente (`pagarItensFatura`). Depois de quitar toda a fatura por fora, `pagarFatura` da mesma fatura retorna "Não há valor em aberto nesta fatura.".
- **UI (Cartões).** No modal de pagamento, a opção "Esta fatura já foi paga (apenas marcar como paga, sem debitar da conta)"; o botão "Já foi paga", ao lado de "Pagar fatura", abre o modal com a opção já ligada. Resumo mostra "Saldo da conta: não será alterado" e o limite restaurado; a confirmação explicita que nenhum pagamento é registrado. Aviso: "N itens marcados como pagos (R$ X). Saldo da conta inalterado.".
- **Limitações.** Não deixa rastro de pagamento (não há `PAGAMENTO_FATURA`; o histórico só mostra os itens como pagos). Desfazer = editar o item e marcá-lo como pendente, se necessário. Relatórios/KPIs não mudam: compras de cartão já contam como despesa na data da compra (R5).

## R47 — Pausar recorrência (portal e app)

Complementa R16 (despesas/receitas fixas), R26 e R40. Campos **opcionais** em `despesasFixas` (documento/backup/export): `pausada: boolean` (padrão `false`) e `pausadaAte: ms | null` (retomada automática). Dados antigos, sem os campos, valem como "não pausada"; backups/exports novos continuam legíveis por versões antigas (campos extras ignorados). App: Room v7 → v8 (`MIGRATION_7_8`: `pausada INTEGER NOT NULL DEFAULT 0`, `pausadaAte INTEGER`; schema em `app/schemas/.../8.json`).

- **Pausada de fato** = `pausada && (pausadaAte == null || agora < pausadaAte)`.
- **Processamento mensal** (portal `processarDespesasFixas`, app `processarRecorrencias`): regra pausada de fato **não lança nada e não altera `ultimaDataLancamento`**. Lançamentos já criados antes da pausa permanecem intactos (pausar não apaga nem altera nada). A idempotência por `grupoId` `fixa:<id>:<AAAA-MM>` continua valendo.
- **Sem catch-up.** Os meses do período pausado **não** são recuperados. Ao retomar (manualmente ou quando `pausadaAte` vence, no próximo processamento): `ultimaDataLancamento = max(ultima, data da última ocorrência da regra com data <= momento da retomada)` e `pausada=false`, `pausadaAte=null`; só ocorrências futuras são lançadas. (Dia 29–31 segue o último dia do mês, como em R16; no app a ocorrência do dia conta até o fim do dia, como em R16.)
- **Operações.** `pausarRecorrencia(id, ate?)` (valida `ate` > agora; pausar de novo atualiza a data) e `retomarRecorrencia(id)` (erro se não estiver pausada). Portal: `operations.ts` + `acoes.pausarRecorrencia/retomarRecorrencia`; app: `MainRepository` + `ContaSaldoViewModel`. Editar a regra, excluir e alterar a origem continuam permitidos com a regra pausada.
- **R40.** Regra pausada de fato **não pode ser antecipada** (`adiantarOcorrenciaFixa` recusa: "Recorrência pausada: retome-a antes de antecipar um mês."). O app não tem antecipação de ocorrência de regra (só de lançamentos já criados).
- **Previsões.** `detectarAssinaturas` / `Analises.assinaturas` (R26, aba Planejamento) ignoram regras pausadas de fato (a descrição continua "coberta", para a regra não reaparecer como DETECTADA). Regras não pausadas: números inalterados.
- **UI.** Portal `/recorrencias`: botão Pausar/Retomar por regra (diálogo "Pausar até (opcional)" com o aviso de que os meses pausados não são lançados nem recuperados), selo "Pausada" / "Pausada até dd/mm/aaaa", linha esmaecida (a página não exibe total mensal). App `GerenciarRecorrencia`: ação Pausar/Retomar, seletor de data opcional (calendário do app), selo "PAUSADA".
- **Limitações.** `pausadaAte` é interpretado com o relógio do dispositivo; a retomada automática só acontece quando o processamento roda (abertura do portal/app).

## R48 — Comprovante do lançamento e detalhamento dos relatórios (app e portal)

Só apresentação: sem campos novos, sem mudar cálculo, seleção ou formato de dados. Os testes de R19/R20 continuam valendo.

**A) Comprovante (PNG; no portal também PDF).** Função pura monta as linhas (`montarComprovante`: app `funcoes/Comprovante.kt`, portal `lib/finance/comprovante.ts`) e outra desenha (app `desenharComprovante`, portal `lib/exportar/comprovante.ts`, canvas, altura dinâmica, texto longo quebrado sem cortar). Cabeçalho "MeuDinheiro" + id `MD-nnnnnn`; **selo** colorido por natureza (receita, despesa, transferência, pagamento de fatura, aporte/resgate de meta, ajuste de saldo, saldo inicial); valor em destaque com sinal; **status** Pago / Pendente / Vencido (vencido = pendente com data anterior a hoje e **sem cartão**, igual a `estaAtrasada`). Linhas só quando existem: descrição (sem o marcador "(i/n)"), categoria, natureza (≠ NORMAL), data do lançamento, vencimento (pendente em conta) ou data do pagamento, **fatura de referência MM/AAAA e vencimento da fatura** (compra no cartão, ciclo do principal, R6/R18), conta (banco, agência, número **mascarado** — só os 4 últimos dígitos), origem → destino em transferências (pela outra perna do `grupoId`), cartão (nome •••• final, físico/virtual) e modalidade Crédito/Débito (`debito:<id>`, R42b), parcela "i/n", **valor total da compra** (soma em centavos do grupo `parc:`, só com ≥ 2 parcelas conhecidas) e parcelas restantes (n − i), recorrência (portal: despesa fixa de mesma descrição/valor/conta; app: só se informada), valor original em moeda estrangeira, autor e conciliação (portal). Rodapé: "Emitido em dd/MM/aaaa às HH:mm" e "Documento gerado pelo MeuDinheiro — sem valor fiscal". Sem QR/serial. A data do pagamento exibida é a data do lançamento (o modelo não guarda a data da baixa).
- App: `compartilharComprovante` / `gerarBitmapComprovanteUltraPremium` mantêm a assinatura, com parâmetro opcional `extras: ComprovanteExtras` (conta, cartão, irmãos do grupo, contas, recorrência, observações); o menu do lançamento (`LancamentoAcoesHost`) já informa conta e cartão. Demais chamadores continuam funcionando com o que a despesa tem.
- Portal: ação "Comprovante" (ícone de recibo) na linha de `ListaLancamentos` abre `ComprovanteModal` (prévia com as mesmas linhas, "Baixar PNG" e "Baixar PDF", este uma página com a mesma imagem).

**B) Detalhamento nos relatórios.** Além do total (por categoria, mês etc.), PDF e imagem trazem **os lançamentos que o compõem**: data, descrição, parcela i/n, conta/cartão, situação e valor, agrupados (categoria por padrão; extrato por conta agrupa por mês; `AgruparPor` = categoria | mês | conta), com **subtotal do grupo**. Receitas e despesas em **seções separadas** quando o tipo é "Receitas e despesas" (resultado = receitas − despesas). Linhas por data crescente; grupos por |subtotal| decrescente (meses em ordem cronológica).
- **Fechamento (invariante testado):** o valor de cada linha é exatamente a contribuição do R19 em **centavos inteiros** (`contribuicao`: despesa +, estorno de cartão −, receita +, TODOS = crédito − débito); soma das linhas = subtotal do grupo; soma dos grupos = total da seção; soma das seções = `total` do relatório; contagem = `quantidade`. Natureza ≠ NORMAL (AJUSTE, transferência, aporte, fatura…) continua fora, salvo `incluirInternos`, como antes. Fatura do cartão: itens da fatura (DEBITO − CREDITO) fecham com o total da fatura (R18).
- **PDF** leva tudo: cabeçalho da tabela repetido em cada página, linha nunca partida entre páginas, rodapé "Página X de Y" (duas passadas no app) e data de emissão; sem detalhamento, a tabela simples anterior. **PNG** só os 5 maiores itens por grupo (até 8 grupos) + "… e mais K lançamentos" / "… e mais G grupos". **CSV** inalterado (já tem todas as linhas).
- **Opção "Incluir detalhamento"** (padrão ligada) na tela Relatórios do portal (checkbox) e do app (chip); desligada, PDF/PNG saem como antes.
- **Extrato mensal do app (`ExtratoPdf`):** mesma paginação (cabeçalho repetido, "Página X de Y", emissão), coluna Situação, parcela "(i/n)" e descrição quebrada em até 2 linhas, e subtotais em centavos: entradas, saídas, **saldo do extrato** e saldo por categoria (a soma das categorias = saldo).
- **Limitações:** o app só informa "valor total da compra" e a conta de destino da transferência quando recebe `irmaos` em `ComprovanteExtras` (o menu de ações ainda não os passa); PDF do comprovante no portal é raster (mesma imagem do PNG).

## R49 — Escanear cupom no formulário de lançamento (app)

Sem campos novos nem mudança de formato (compatível com o portal). No formulário "Nova despesa" (`AddDespesaDialog`) o botão **Escanear cupom** abre o scanner de câmera (mesmo visual neon e mesma permissão de câmera do scanner de boleto) e preenche **valor total**, **descrição** (só se estiver vazia) e **data** (só se exata) para o usuário revisar. **Nunca salva sozinho**; Voltar/Cancelar não altera o formulário. Dinheiro sempre em centavos inteiros (`Dinheiro`); valor ≤ 0, negativo, com mais de 2 casas ou acima de R$ 10 milhões é rejeitado (campo fica vazio e o usuário digita).

- **Parser puro** `funcoes/LeitorCupomFiscal.interpretar(texto): CupomLido?` (`valorCentavos?`, `chave?`, `cnpj?`, `emissaoMs?`, `estabelecimento?`, `origem`, `urlConsulta?`, `emissaoAproximada`). Testes em `LeitorCupomFiscalTest`.
- **Formatos aceitos**
  - **CF-e SAT** (`chave44|aaaaMMddHHmmss|valorTotal|cpfCnpjDest|assinatura`): valor e data/hora exatos, offline.
  - **NFC-e QR v1** (query com `vNF`/`vNFe`, `dhEmi` em hexadecimal ASCII): valor e data offline.
  - **NFC-e QR v2** (`?p=chave44|2|tpAmb|...`): **o QR não traz o valor** (a chave de 44 posições também não). Só a contingência offline (`p=chave|2|tpAmb|dia|vNF|...`) traz. Da chave saem CNPJ do emitente (pos. 7–20), UF, AAMM e modelo; com isso sugere-se a descrição "Compra – CNPJ xx.xxx.xxx/xxxx-xx" e um **mês aproximado** (nunca aplicado à data do lançamento).
  - **Boleto/arrecadação**: 44 dígitos (código de barras), 47 (linha digitável bancária) e 48 (arrecadação); bancário: fator + valor (10 dígitos, 0 = valor livre); arrecadação: só com identificador de valor 6/7 (8/9 = referência, sem valor). O leitor antigo (`LeitorBoletoAnalyzer`) segue igual e é usado só como fonte de frames; o scanner de cupom reinterpreta o texto com o parser acima.
  - **PIX BR Code (EMV)**: campo 54 = valor, 59/60 = nome/cidade do recebedor; CRC16 verificado (CRC inválido → ignorado). QR aberto (sem 54) pede o valor.
  - **Chave de acesso** (44): validada por dígito verificador módulo 11 + UF/mês/modelo (55/59/65). Chave inválida é ignorada (sem CNPJ/data derivados), sem quebrar.
- **NFC-e v2: consulta do total (melhor esforço).** `ConsultaNfceRede` consulta a URL do QR **só se** for `https`, host terminando em `.gov.br`, sem credenciais na URL e porta padrão; timeout de 8 s por operação (15 s total), resposta limitada a 512 KB, em `Dispatchers.IO`, sem cookies, sem enviar nenhum dado do usuário, redirecionamentos tratados manualmente (máx. 3) e **revalidados** (nunca saem de `.gov.br`). O HTML é lido por regex tolerante (prefere "Valor a pagar", depois "Valor total R$"), tratado como texto: nada é executado nem aberto. Cada UF tem layout próprio, usa captcha ou renderiza por JavaScript: pode falhar. Se falhar/sem internet: "Esse cupom não traz o valor no código; digite o valor — chave e emitente foram preenchidos." e o diálogo oferece o campo de valor.
- **Segurança.** O conteúdo do código é dado não confiável: links nunca são abertos automaticamente, só texto/números são extraídos, tamanho do texto limitado (4 KB).
- **Limitações.** A chave de acesso e a descrição não são gravadas (não há campo no lançamento); a data só é aplicada quando exata (SAT/NFC-e v1). CNPJ alfanumérico (2026) é aceito na chave (DV módulo 11 com `código ASCII − 48`).
