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
| `despesasFixas` | `id:int, descricao, valor, conta, categoria, pic, tipo, diaVencimento:1‑31, ultimaDataLancamento:ms\|null, cartaoId:int\|null` |
| `categorias` | `id:int, nome, pic` |
| `orcamentos` | `id:int, categoria, valorLimite` |
| `metas` | `id:int, nome, valorObjetivo, valorGuardado, icone, dataAlvo:ms\|null` |
| `investimentos` | `id:int, nome, tipo, valorInvestido, valorAtual` |
| `cartoes` | `id:int, nome, finalCartao, tipo, limiteDisponivel, limiteTotal, diaFechamento, diaVencimento, contaId:int, cartaoPrincipalId:int\|null` |
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
Se a regra tem `cartaoId` (física ou virtual), cada ocorrência é uma **compra no cartão** (`cartaoId` preenchido, `conta` = conta do cartão, `pago=false`, consome limite — R4/R18); sem `cartaoId`, é lançamento na conta (`conta`). Uma regra tem conta **ou** cartão como origem do pagamento.

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
