# 2. Esquema da base de dados

**Resultado:** as 5 tabelas principais criadas por uma migração Flyway, e 8 testes que
provam que a base de dados recusa dados errados (por exemplo, duas fotografias da mesma
listing no mesmo dia). `./mvnw verify` passa com 10 testes.

Ainda **não** há entidades JPA nem repositórios: entram no passo 3. Primeiro a base de
dados tem de estar certa, porque é ela que guarda as regras mais importantes.

## 2.1 Ficheiros deste passo

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `src/main/resources/db/migration/V1__create_core_tables.sql` | novo | Cria `users`, `publishers`, `extensions`, `listings` e `snapshots`, com as restrições |
| `src/test/java/com/mccprogrammer/extpulse/SchemaTests.java` | novo | 8 testes, um por regra da base de dados |
| `src/main/resources/db/migration/.gitkeep` | apagado | Só servia para o Git guardar a pasta vazia; agora a pasta tem a migração |

Nenhum outro ficheiro mudou: o Flyway já estava no `pom.xml` e o `application.yaml` já
apontava para `classpath:db/migration`.

## 2.2 Conceitos usados

| Conceito | O que é | Neste passo |
| --- | --- | --- |
| Chave primária (`PRIMARY KEY`) | Identifica cada linha; única e nunca nula | `id` em todas as tabelas |
| `bigint generated always as identity` | O PostgreSQL gera o `id` sozinho (1, 2, 3...) | Todas as tabelas |
| Chave estrangeira (`REFERENCES`) | Uma coluna que tem de apontar para uma linha que existe noutra tabela | `publishers.user_id` → `users.id` |
| `ON DELETE CASCADE` | Se apagares a linha "pai", as "filhas" são apagadas também | Apagar um utilizador apaga os seus publishers, extensões... |
| `UNIQUE` | Não podem existir duas linhas com o mesmo valor (ou a mesma combinação) | Uma fotografia por listing por dia |
| `CHECK` | Uma condição que cada linha tem de cumprir | `installs >= 0` |
| `NOT NULL` | A coluna tem de ter valor | Quase todas |
| Índice (`INDEX`) | Uma estrutura que torna as procuras por uma coluna rápidas | `listings_extension_id_idx` |
| `timestamptz` | Data e hora com fuso horário (guardada em UTC) | `created_at` |
| `date` | Só o dia, sem hora | `taken_on` (o dia da fotografia) |
| `numeric(3,2)` | Número decimal exato, aqui de 0.00 a 9.99 | `rating_avg` (4.75) |
| `text` | Texto sem limite; no PostgreSQL é tão rápido como `varchar(n)` | Nomes, emails |

**Porquê `bigint` e não `int`?** Um `int` acaba nos 2,1 mil milhões. Numa tabela que
cresce todos os dias, como `snapshots`, não vale a pena arriscar; o custo são 4 bytes.

**Porquê `numeric` e não `double precision` para a média?** O `double` guarda
aproximações (4.7 pode ficar 4.69999...). Para valores que se mostram e comparam,
`numeric` é exato.

**Porquê `timestamptz` e não `timestamp`?** O `timestamp` não sabe o fuso horário, e o
mesmo valor significa horas diferentes em Lisboa e em Nova Iorque. O `timestamptz`
guarda sempre em UTC.

**Porquê `text` com `CHECK` para a loja, e não um tipo `ENUM` do PostgreSQL?** Juntar
um valor a um `ENUM` exige `ALTER TYPE`, que tem limitações; um `CHECK` muda-se numa
migração simples. E o JPA lê `text` diretamente para um `enum` Java com
`@Enumerated(EnumType.STRING)`.

## 2.3 O modelo

```
users 1 ──< publishers 1 ──< extensions 1 ──< listings 1 ──< snapshots
```

Lê-se: um utilizador tem vários publishers; um publisher tem várias extensões; uma
extensão tem várias listings (uma por loja); uma listing tem uma fotografia por dia.

**Porquê separar `extensions` de `listings`?** O Debug Data Structures Visualizer é
**uma** extensão com **duas** listings (Marketplace e Open VSX). O total combinado soma
as listings da mesma extensão.

As outras quatro tabelas do documento do projeto (`reviews`, `alert_rules`,
`alert_events`, `collection_runs`) entram nos passos que as usam, cada uma na sua
migração (`V2__...`, `V3__...`).

## 2.4 A migração, tabela a tabela

**O nome do ficheiro:** `V` + versão + dois underscores + descrição. O Flyway corre-os
por ordem de versão e regista cada um na tabela `flyway_schema_history` (aparece lá como
`1 | create core tables`). **Uma migração que já correu nunca se altera:** o Flyway
compara um checksum e recusa-se a arrancar. Uma correção é uma `V2__...`.

**A ordem das tabelas** é de "pai" para "filho" (`users` → `publishers` → `extensions` →
`listings` → `snapshots`): uma chave estrangeira não pode apontar para uma tabela que
ainda não existe.

### `users`

- **`password_hash`** e não `password`: nunca se guarda a password, só o resultado de um
  hash (BCrypt, no passo 7).
- **Índice único sobre `lower(email)`** em vez de `UNIQUE (email)`: um `UNIQUE` simples
  deixava criar a mesma conta com maiúsculas diferentes.

### `publishers` e `extensions`

- `user_id` e `publisher_id` são obrigatórios e em cascata.
- **`UNIQUE (user_id, name)`:** o mesmo utilizador não pode ter dois publishers com o
  mesmo nome. **`UNIQUE (publisher_id, display_name)`:** o mesmo para extensões.

### `listings`

- **`store`** só aceita `VS_MARKETPLACE`, `OPEN_VSX` ou `CHROME_WEB_STORE` (um `CHECK`).
- **`external_id`** é o ID na loja: `MCCProgrammer.diff-checker` no Marketplace,
  `MCCProgrammer/diff-checker` no Open VSX, `dmdmnlelljolpkpldndebhpkaomdimil` no Chrome.
- **`UNIQUE (store, external_id)`:** a mesma listing não pode aparecer duas vezes na
  mesma loja. Se dois utilizadores seguirem a mesma extensão, a fotografia é guardada uma vez.
- **`url`** é opcional.
- **O índice `listings_extension_id_idx`** foi o único acrescento à especificação. O
  PostgreSQL **não cria índices para chaves estrangeiras**. Sem ele, "as listings desta
  extensão" (o total combinado) e o cascade ao apagar uma extensão leriam a tabela
  inteira. As outras chaves estrangeiras não precisam de índice próprio: em `publishers`
  e `extensions` o `UNIQUE` começa pela chave estrangeira, e um índice serve para
  procurar pela sua primeira coluna.

### `snapshots`

- **`taken_on`** (`date`) é o dia a que a fotografia se refere. **`collected_at`**
  (`timestamptz`) é quando foi recolhida de facto. São coisas diferentes: uma recolha
  falhada às 06:00 e repetida às 10:00 continua a ser a fotografia desse dia.
- **`installs`** guarda installs no Marketplace, downloads no Open VSX e utilizadores no
  Chrome. Nunca negativo.
- **`rating_avg`** é opcional (pode não haver avaliações) e fica entre 0 e 5.
- **`rating_count`** é obrigatório, 0 por omissão, nunca negativo.
- **`UNIQUE (listing_id, taken_on)`, a regra mais importante do projeto:** uma só
  fotografia por listing por dia. A recolha diária do passo 4 vai usar
  `INSERT ... ON CONFLICT (listing_id, taken_on) DO UPDATE`, e correr duas vezes nunca
  duplica dados. O índice criado por esta restrição é exatamente o que as consultas de
  estatísticas vão usar ("a série de uma listing, por dia").

## 2.5 Os testes: `SchemaTests.java`

Usam o `JdbcClient` do Spring para escrever SQL diretamente, porque ainda não há JPA.
O `@BeforeEach` cria, antes de cada teste, um utilizador, um publisher, uma extensão e
uma listing do Marketplace.

| Teste | O que prova |
| --- | --- |
| `rejectsASecondSnapshotOfTheSameListingOnTheSameDay` | A regra de uma fotografia por dia |
| `acceptsSnapshotsOfTheSameListingOnDifferentDays` | A mesma regra não bloqueia dias diferentes |
| `rejectsNegativeInstalls` | `CHECK (installs >= 0)` |
| `rejectsAnAverageRatingAboveFive` | `CHECK (rating_avg BETWEEN 0 AND 5)` |
| `rejectsAnUnknownStore` | O `CHECK` das lojas |
| `rejectsTheSameListingTwiceInOneStore` | `UNIQUE (store, external_id)` |
| `rejectsTheSameEmailWithDifferentCase` | O índice único sobre `lower(email)` |
| `deletingAUserDeletesEverythingBelowIt` | O cascade de `users` até `snapshots` |

Pormenores:

- **`@Transactional` na classe:** cada teste corre dentro de uma transação que é desfeita
  no fim. Os testes não se afetam uns aos outros e a base de dados fica limpa.
- **`DataIntegrityViolationException`:** o Spring traduz os erros do PostgreSQL (código
  `23505` para únicos, `23514` para checks) numa exceção própria. O teste não depende do
  driver.
- **Valores sempre com `.param(...)`,** nunca concatenados no SQL. Assim não há SQL
  injection. A exceção é o nome da tabela em `count(...)`: um nome de tabela não pode ser
  parâmetro, e os nomes vêm só dos próprios testes.
- **Text block (`"""`)** em `rejectsAnAverageRatingAboveFive`: SQL em várias linhas sem
  concatenar strings.

**Como se confirmou que os testes apanham as regras:** retirou-se de propósito o
`UNIQUE (listing_id, taken_on)` e o índice único do email. Falharam exatamente os dois
testes dessas regras. Depois a migração foi reposta. Um teste que nunca falha não prova nada.

## 2.6 Confirmar que funciona

```bash
./mvnw verify
```

Esperado: `BUILD SUCCESS` e 10 testes (1 + 1 + 8).

Ver as tabelas com os teus próprios olhos:

```bash
./mvnw spring-boot:run                               # cria as tabelas na base de dados de desenvolvimento
docker compose exec postgres psql -U extpulse        # noutro terminal
\dt                                                  # lista as tabelas
\d snapshots                                         # colunas, índices e restrições
SELECT version, description, success FROM flyway_schema_history;
\q
```

## 2.7 Perguntas para saberes responder

- Porque é que a regra "uma fotografia por dia" está na base de dados e não só no código Java?
- O que acontece se alterares o `V1` depois de ele ter corrido?
- Porquê `numeric(3,2)` para a média e `bigint` para os installs?
- Porquê `timestamptz` e não `timestamp`? E qual é a diferença entre `taken_on` e `collected_at`?
- O PostgreSQL cria índices para as chaves estrangeiras? Porque é que só `listings` precisou de um?
- Que índice existe em `snapshots` sem o termos criado de propósito, e porque é útil?
- Como sabes que um teste de uma restrição está mesmo a testar essa restrição?
