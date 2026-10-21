# 2. Esquema da base de dados

**Resultado:** as 5 tabelas principais criadas por uma migração Flyway, e testes
que provam que a base de dados recusa dados errados (por exemplo, duas fotografias
da mesma listing no mesmo dia).

**Quem escreve:** tu. Este capítulo diz o que fazer e porquê; a tabela `users` vem
completa como modelo, e as outras quatro escreves tu a partir da especificação.
Quando terminares, pede uma revisão.

## 2.1 Ficheiros deste passo

| Ficheiro | Novo? | O que faz |
| --- | --- | --- |
| `src/main/resources/db/migration/V1__create_core_tables.sql` | novo | Cria as tabelas `users`, `publishers`, `extensions`, `listings` e `snapshots` |
| `src/test/java/com/mccprogrammer/extpulse/SchemaTests.java` | novo | Testa as regras da base de dados (únicos, chaves estrangeiras, checks) |
| `src/main/resources/db/migration/.gitkeep` | apagar | Já não é preciso: a pasta passa a ter um ficheiro |

Ainda **não** há entidades JPA nem repositórios: entram no passo 3. Primeiro a base
de dados tem de estar certa, porque é ela que guarda as regras mais importantes.

## 2.2 Conceitos que vais usar

| Conceito | O que é | Exemplo neste passo |
| --- | --- | --- |
| Chave primária (`PRIMARY KEY`) | Identifica cada linha; única e nunca nula | `id` em todas as tabelas |
| `bigint generated always as identity` | O PostgreSQL gera o número do `id` sozinho (1, 2, 3...) | Todas as tabelas |
| Chave estrangeira (`REFERENCES`) | Uma coluna que tem de apontar para uma linha que existe noutra tabela | `publishers.user_id` → `users.id` |
| `ON DELETE CASCADE` | Se apagares a linha "pai", as "filhas" são apagadas também | Apagar um utilizador apaga os seus publishers, extensões... |
| `UNIQUE` | Não podem existir duas linhas com o mesmo valor (ou a mesma combinação) | Uma fotografia por listing por dia |
| `CHECK` | Uma condição que cada linha tem de cumprir | `installs >= 0` |
| `NOT NULL` | A coluna tem de ter valor | Quase todas |
| `timestamptz` | Data e hora com fuso horário (guardada em UTC) | `created_at` |
| `date` | Só o dia, sem hora | `taken_on` (o dia da fotografia) |
| `numeric(3,2)` | Número decimal exato, aqui de 0.00 a 9.99 | `rating_avg` (4.75) |
| `text` | Texto sem limite; no PostgreSQL é tão rápido como `varchar(n)` | Nomes, emails |

**Porquê `bigint` e não `int`?** Um `int` acaba nos 2,1 mil milhões. Numa tabela que
cresce todos os dias, como `snapshots`, não vale a pena arriscar; o custo são 4 bytes.

**Porquê `numeric` e não `double precision` para a média?** O `double` guarda
aproximações (4.7 pode ficar 4.69999...). Para valores que se mostram e comparam,
`numeric` é exato.

**Porquê `timestamptz` e não `timestamp`?** O `timestamp` não sabe o fuso horário, e
o mesmo valor significa horas diferentes em Lisboa e em Nova Iorque. O `timestamptz`
guarda sempre em UTC.

**Porquê `text` com `CHECK` para a loja, e não um tipo `ENUM` do PostgreSQL?**
Juntar um valor a um `ENUM` exige `ALTER TYPE`, que tem limitações; um `CHECK` muda-se
numa migração simples. E o JPA lê `text` diretamente para um `enum` Java com
`@Enumerated(EnumType.STRING)`.

## 2.3 O modelo

```
users 1 ──< publishers 1 ──< extensions 1 ──< listings 1 ──< snapshots
```

Lê-se: um utilizador tem vários publishers; um publisher tem várias extensões; uma
extensão tem várias listings (uma por loja); uma listing tem uma fotografia por dia.

**Porquê separar `extensions` de `listings`?** O Debug Data Structures Visualizer é
**uma** extensão com **duas** listings (Marketplace e Open VSX). O total combinado
soma as listings da mesma extensão.

## 2.4 A migração `V1__create_core_tables.sql`

**Nome do ficheiro:** `V` + versão + dois underscores + descrição. O Flyway corre-os
por ordem de versão e regista cada um na tabela `flyway_schema_history`.
**Nunca alteres uma migração que já correu**: o Flyway compara um checksum e
recusa-se a arrancar. Uma correção é uma `V2__...`.

### Modelo completo: `users`

```sql
CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Unique ignoring case: Miguel@x.com and miguel@x.com are the same account.
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
```

Repara:
- **`password_hash`** e não `password`: nunca se guarda a password, só o resultado de
  um hash (BCrypt, no passo 7).
- **O índice único sobre `lower(email)`** em vez de `UNIQUE (email)`: um `UNIQUE`
  simples deixava criar a mesma conta com maiúsculas diferentes.
- **Comentários em inglês**, como o resto do código.

### Especificação: escreve tu estas quatro

**`publishers`** — as contas de publisher de cada utilizador (ex.: `MCCProgrammer`).

| Coluna | Tipo | Regras |
| --- | --- | --- |
| `id` | bigint identity | chave primária |
| `user_id` | bigint | obrigatório; aponta para `users`; apagar o utilizador apaga os publishers |
| `name` | text | obrigatório |
| `created_at` | timestamptz | obrigatório; por omissão `now()` |

Regra extra: o mesmo utilizador não pode ter dois publishers com o mesmo `name`.

**`extensions`** — cada extensão, independentemente da loja.

| Coluna | Tipo | Regras |
| --- | --- | --- |
| `id` | bigint identity | chave primária |
| `publisher_id` | bigint | obrigatório; aponta para `publishers`; em cascata |
| `display_name` | text | obrigatório (ex.: `Debug Data Structures Visualizer`) |
| `created_at` | timestamptz | obrigatório; `now()` |

Regra extra: o mesmo publisher não pode ter duas extensões com o mesmo `display_name`.

**`listings`** — a presença de uma extensão numa loja.

| Coluna | Tipo | Regras |
| --- | --- | --- |
| `id` | bigint identity | chave primária |
| `extension_id` | bigint | obrigatório; aponta para `extensions`; em cascata |
| `store` | text | obrigatório; só `VS_MARKETPLACE`, `OPEN_VSX` ou `CHROME_WEB_STORE` |
| `external_id` | text | obrigatório: o ID na loja (`MCCProgrammer.diff-checker` no Marketplace, `MCCProgrammer/diff-checker` no Open VSX, `dmdmnlelljolpkpldndebhpkaomdimil` no Chrome) |
| `url` | text | opcional |
| `created_at` | timestamptz | obrigatório; `now()` |

Regra extra: o mesmo `external_id` não pode aparecer duas vezes **na mesma loja**.

**`snapshots`** — a fotografia diária de uma listing.

| Coluna | Tipo | Regras |
| --- | --- | --- |
| `id` | bigint identity | chave primária |
| `listing_id` | bigint | obrigatório; aponta para `listings`; em cascata |
| `taken_on` | date | obrigatório: o dia a que a fotografia se refere |
| `installs` | bigint | obrigatório; nunca negativo. Installs no Marketplace, downloads no Open VSX, utilizadores no Chrome |
| `rating_avg` | numeric(3,2) | opcional (pode não haver avaliações); entre 0 e 5 |
| `rating_count` | integer | obrigatório; por omissão 0; nunca negativo |
| `version` | text | opcional |
| `collected_at` | timestamptz | obrigatório; `now()`: quando foi recolhida de facto |

**Regra extra, a mais importante do projeto:** uma só fotografia por `listing_id` e
`taken_on`. Esta restrição também cria um índice em `(listing_id, taken_on)`, que é
exatamente o que as consultas de estatísticas vão usar ("a série de uma listing por
dias").

**Dicas de sintaxe:**

```sql
-- foreign key with cascade
user_id bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,

-- unique over two columns (at the end of CREATE TABLE)
UNIQUE (user_id, name)

-- check on a column
installs bigint NOT NULL CHECK (installs >= 0),
store    text   NOT NULL CHECK (store IN ('VS_MARKETPLACE', 'OPEN_VSX', 'CHROME_WEB_STORE')),
```

**Ordem:** cria as tabelas de "pai" para "filho" (`users` → `publishers` →
`extensions` → `listings` → `snapshots`). Uma chave estrangeira não pode apontar
para uma tabela que ainda não existe.

## 2.5 O teste `SchemaTests.java`

Os testes provam que as regras estão mesmo na base de dados. Usam o `JdbcClient` do
Spring para escrever SQL diretamente, sem JPA (que ainda não existe).

Começa por este esqueleto, com o primeiro teste completo:

```java
package com.mccprogrammer.extpulse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database itself enforces the rules that matter, so a bug in the code
 * can't break them.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional // each test runs in a transaction that is rolled back at the end
class SchemaTests {

	@Autowired
	private JdbcClient jdbc;

	private long listingId;

	@BeforeEach
	void createAListing() {
		long userId = insertReturningId(
				"INSERT INTO users (email, password_hash) VALUES ('miguel@example.com', 'x') RETURNING id");
		long publisherId = insertReturningId(
				"INSERT INTO publishers (user_id, name) VALUES (" + userId + ", 'MCCProgrammer') RETURNING id");
		long extensionId = insertReturningId(
				"INSERT INTO extensions (publisher_id, display_name) VALUES (" + publisherId + ", 'Diff Checker') RETURNING id");
		listingId = insertReturningId(
				"INSERT INTO listings (extension_id, store, external_id) VALUES (" + extensionId
						+ ", 'VS_MARKETPLACE', 'MCCProgrammer.diff-checker') RETURNING id");
	}

	@Test
	void rejectsASecondSnapshotOfTheSameListingOnTheSameDay() {
		insertSnapshot("2026-10-21", 1700);

		assertThatThrownBy(() -> insertSnapshot("2026-10-21", 1729))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertSnapshot(String day, long installs) {
		jdbc.sql("INSERT INTO snapshots (listing_id, taken_on, installs) VALUES (:listing, CAST(:day AS date), :installs)")
				.param("listing", listingId)
				.param("day", day)
				.param("installs", installs)
				.update();
	}

	private long insertReturningId(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

}
```

**Escreve tu estes testes** (um método `@Test` cada, no mesmo estilo):

1. `acceptsSnapshotsOfTheSameListingOnDifferentDays`: dois dias diferentes não dão
   erro. (Dica: sem `assertThatThrownBy`; se não houver exceção, o teste passa. Ou
   conta as linhas no fim e compara com `assertThat(...).isEqualTo(2)`.)
2. `rejectsNegativeInstalls`.
3. `rejectsAnUnknownStore`: uma listing com `store = 'APP_STORE'`.
4. `rejectsTheSameEmailWithDifferentCase`: `MIGUEL@example.com` depois de
   `miguel@example.com`.
5. `deletingAUserDeletesEverythingBelowIt`: apaga o utilizador e confirma que
   `snapshots` fica sem linhas (o cascade).

**Porquê `@Transactional` no teste?** Cada teste corre dentro de uma transação que é
desfeita no fim, por isso os testes não se afetam uns aos outros e a base de dados
fica limpa.

**Nota:** no `@BeforeEach` os IDs entram concatenados no SQL para o exemplo ficar
curto. Em código de produção usa-se sempre `.param(...)`, nunca concatenação, para
evitar SQL injection. Fica como melhoria: passa o `@BeforeEach` para `.param(...)`.

## 2.6 Confirmar que funciona

```bash
./mvnw verify
```

Esperado: `BUILD SUCCESS` e os testes de `SchemaTests` a passar.

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
- Porquê `timestamptz` e não `timestamp`?
- Que índice existe em `snapshots` sem o teres criado de propósito, e porque é útil?

## 2.8 Quando terminares

Faz commit (`git add . && git commit -m "Core tables: users, publishers, extensions, listings, snapshots"`)
e pede uma revisão. Revejo o SQL e os testes antes de passarmos ao passo 3
(as entidades JPA e os adaptadores das lojas).
