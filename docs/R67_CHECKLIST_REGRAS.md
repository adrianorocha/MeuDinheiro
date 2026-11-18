# R67 - Checklist manual das regras (Simulador de regras do Firebase)

Nada disto foi executado contra as regras reais: rode no console **antes** de liberar para a família.
Console Firebase -> Firestore Database -> Regras -> **Simulador de regras** (e Storage -> Regras -> Playground).
Publicar: `firebase deploy --only firestore:rules,storage` (dentro de `portal/`).

## Preparação (dados de teste)

Use 4 contas: **DONO** (`uidD`), **ADMIN** (`uidA`), **EDITOR** (`uidE`), **LEITOR** (`uidL`) e **EXTERNO** (`uidX`, não é membro).
Crie manualmente (console, como admin) em `users/uidD/membros/`:

| Documento | Campos |
|---|---|
| `uidA` | `{uid:"uidA", papel:"ADMIN", entrouEm:1, nome:"A"}` |
| `uidE` | `{uid:"uidE", papel:"EDITOR", entrouEm:1, nome:"E"}` |
| `uidL` | `{uid:"uidL", papel:"LEITOR", entrouEm:1, nome:"L"}` |
| `uidR32` | `{uid:"uidR32", email:"x@y.com", criadoEm:1}` (sem `papel`: tem de se comportar como EDITOR) |

No simulador, "Autenticado" com o uid indicado e o provedor/e-mail preenchido (a regra de entrada por convite lê `request.auth.token.email`).

## 1. Dados (`users/uidD/<colecao>/<id>`) - permitido (P) / negado (N)

| Caso | DONO | ADMIN | EDITOR | LEITOR | EXTERNO | Sem login |
|---|---|---|---|---|---|---|
| `get`/`list` em `despesas`, `contas`, `metas`... | P | P | P | P | N | N |
| `create` em `despesas` (com `autorUid` = próprio uid) | P | P | P | N | N | N |
| `create` em `despesas` com `autorUid` de OUTRO uid | P | P | **N** | N | N | N |
| `update` em `despesas` mudando `autorUid` | P | P | **N** | N | N | N |
| `delete` em `despesas` (vai para a lixeira) | P | P | P | N | N | N |
| `create`/`update` em `categorias`, `projetos`, `pessoas`, `acertos`, `dividas`, `regrasCategoria`, `despesasFixas`, `lixeira` | P | P | P | N | N | N |
| `delete` em `lixeira` / `categorias` / `pessoas` (exclusão definitiva) | P | P | **N** | N | N | N |
| `create`/`delete` em `contas`, `cartoes`, `orcamentos`, `metas`, `investimentos` | P | P | **N** | N | N | N |
| `update` em `contas` mudando SÓ `saldo` (+`atualizadoEm`) | P | P | P | N | N | N |
| `update` em `contas` mudando `banco` ou `titular` | P | P | **N** | N | N | N |
| `update` em `cartoes` só `limiteDisponivel`; em `metas` só `valorGuardado` | P | P | P | N | N | N |
| `update` em `cartoes` mudando `limiteTotal` | P | P | **N** | N | N | N |
| `write` em `configuracoes/geral` | P | P | **N** | N | N | N |
| qualquer acesso a coleção nova/desconhecida (ex.: `xpto`) | P | P | `read` P, `write` **N** | `read` P | N | N |
| membro legado (`uidR32`, sem `papel`) cria `despesas` | - | - | P (= EDITOR) | - | - | - |

## 2. Membros (`users/uidD/membros/<uid>`)

| Caso | Esperado |
|---|---|
| DONO `create` de `membros/uidN` com `{papel:"ADMIN"}` | P |
| ADMIN `create` com `{papel:"EDITOR"}` | P |
| ADMIN `create` com `{papel:"ADMIN"}` | **N** |
| EDITOR/LEITOR `create` para outro uid | **N** |
| DONO `create` de `membros/uidD` (dono entrando como membro de si mesmo) | **N** |
| DONO `update` `{papel:"LEITOR"}` em `uidE` | P |
| ADMIN `update` `{papel:"LEITOR"}` em `uidE` | P |
| ADMIN `update` em `uidA` ou promovendo alguém a ADMIN | **N** |
| EDITOR `update` do próprio doc `{papel:"ADMIN"}` (auto-promoção) | **N** |
| EDITOR `update` mudando qualquer campo além de `papel` | **N** |
| `get` do próprio doc por EXTERNO (`membros/uidX`, não existe) | resposta "não existe", sem erro de permissão |
| `list` em `membros` por DONO/ADMIN/EDITOR/LEITOR | P |
| `list` em `membros` por EXTERNO | **N** |
| `delete` de `uidE` por DONO / por ADMIN | P |
| `delete` de `uidA` por ADMIN | **N** (só o dono) |
| `delete` do próprio doc por EDITOR (sair) | P |
| EDITOR `delete` do doc de outro membro | **N** |

## 3. Convites por código (`convites/<codigo>`)

Código de teste válido: `ABCD2345`. Documento: `{donoUid:"uidD", papel:"EDITOR", criadoEm:<ms agora>, expiraEm:<agora+7d>, usado:false, criadoPor:"uidD"}`.
(No simulador use a "hora da requisição"; `expiraEm` é epoch ms.)

| Caso | Esperado |
|---|---|
| DONO `create` com o documento acima | P |
| DONO `create` com `papel:"ADMIN"` | P |
| ADMIN `create` (`criadoPor:"uidA"`) com `papel:"EDITOR"` | P |
| ADMIN `create` com `papel:"ADMIN"` | **N** |
| EDITOR/LEITOR/EXTERNO `create` com `donoUid:"uidD"` | **N** |
| `create` com código inválido: `ABCD234` (7), `ABCD23456` (9), `ABCD234O` (O), `ABCD2341` (1), `abcd2345` (minúscula), `ABCD-234` | **N** |
| `create` com `usado:true` | **N** |
| `create` com `expiraEm` = agora + 30 dias | **N** |
| `create` com `expiraEm` no passado | **N** |
| `create` com `criadoPor` de outro uid ou campo extra (`admin:true`) | **N** |
| `create` sobre código já existente (vira update) | **N** |
| `get` por qualquer usuário logado que sabe o código | P |
| `get` sem login | **N** |
| `list` em `convites` sem filtro, ou por EXTERNO | **N** |
| `list` por DONO filtrando `donoUid == uidD` | P |
| `delete` de convite não usado por DONO / por ADMIN | P |
| `delete` por EDITOR/EXTERNO | **N** |
| `delete` de convite JÁ usado | **N** |

### Entrar com o convite (batch de duas escritas, como `aceitarConvite`)

Autenticado como convidado `uidN` (e-mail `n@x.com`), batch:
1. `set users/uidD/membros/uidN` = `{uid:"uidN", papel:"EDITOR", entrouEm:<ms>, criadoEm:<ms>, nome:"N", email:"n@x.com", convite:"ABCD2345"}`
2. `update convites/ABCD2345` = `{usado:true, usadoPor:"uidN", usadoEm:<ms>}`

(O simulador testa uma operação por vez; para o batch teste cada uma sabendo que a regra consulta o estado "depois" via `getAfter`. A validação ponta a ponta é: gerar um convite no portal com uma conta e entrar com outra.)

| Caso | Esperado |
|---|---|
| Batch completo acima, convite válido | P |
| Passo 1 sem o passo 2 (não marca usado) | **N** (`getAfter(...).usado` continua `false`) |
| Passo 2 sem o passo 1 (usar sem criar o membro) | **N** |
| Convite já `usado:true` | **N** |
| Convite expirado (`expiraEm` < agora) | **N** |
| Convite de OUTRO dono (`donoUid` diferente do caminho `users/<dono>`) | **N** |
| Código forjado (documento `convites/<codigo>` inexistente) | **N** |
| Convidado grava `papel:"ADMIN"` num convite `EDITOR` (escolher o papel) | **N** |
| Convidado grava `papel:"EDITOR"` num convite `LEITOR` | **N** |
| Convite `EDITOR` e o membro grava `papel:"EDITOR"` | P |
| Membro com campo extra (`isAdmin:true`) | **N** |
| `email` diferente do e-mail do login | **N** |
| `nome` com mais de 80 caracteres | **N** |
| DONO tentando usar o próprio convite (batch com `uidD`) | **N** |
| O mesmo código usado por uma 2ª pessoa depois | **N** |
| Convidado muda `papel`/`expiraEm`/`donoUid` do convite junto com `usado` | **N** |
| Convidado já membro tentando de novo | **N** |

## 4. Tokens de push (`users/<uid>/dispositivos/<id>`) - para o futuro push/FCM

| Caso | Esperado |
|---|---|
| Próprio `uid` lê/grava | P |
| DONO lendo `users/uidE/dispositivos/x` (de um membro) | **N** |
| Membro lendo `users/uidD/dispositivos/x` (do dono) | **N** |

## 5. Perfis e convites por e-mail (R32, inalterado)

| Caso | Esperado |
|---|---|
| Logado lê `perfis/<qualquer e-mail>` | P |
| Gravar `perfis/<e-mail de outro>` | **N** |
| Dono grava `perfis/<e-mail>/convites/<donoUid>` | P |

## 6. Storage (`usuarios/uidD/anexos/<despesaId>/<arquivo>`)

Tipo `image/jpeg`, 1 MB, salvo indicação.

| Caso | DONO | ADMIN | EDITOR | LEITOR | EXTERNO |
|---|---|---|---|---|---|
| `get` | P | P | P | P | N |
| `create` | P | P | P | N | N |
| `update` (sobrescrever) | P | P | P | N | N |
| `delete` | P | P | **N** | N | N |
| `create` com `text/html` ou 6 MB | N | N | N | N | N |
| sem login | N | N | N | N | N |

Membro legado (sem `papel`) = EDITOR.

## 7. Roteiro ponta a ponta (2 contas reais)

1. Conta A (dono) -> `/membros` -> escolher **Editor** -> "Gerar código" -> copiar.
2. Conta B -> `/membros` -> "Entrar com um código" -> colar -> ver "conta de A como Editor" -> confirmar. B passa a usar os dados de A.
3. B lança uma despesa: aparece "por B" na lista de A; filtro "Quem lançou" e "Minhas despesas" funcionam.
4. B tenta excluir uma conta/cartão (não aparece o botão; se forçar pela API, o servidor nega).
5. A muda B para **Leitor**: B perde os botões de criar/editar (após recarregar) e o servidor nega gravações.
6. Reutilizar o mesmo código em C -> "Este convite já foi usado."
7. Gerar outro código e esperar passar de 7 dias (ou editar `expiraEm` no console) -> "Este convite expirou."
8. B -> "Sair da conta compartilhada" -> volta aos próprios dados; A não vê mais B.
