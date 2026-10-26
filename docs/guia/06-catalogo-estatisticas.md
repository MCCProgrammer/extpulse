# 6. Catálogo e estatísticas na API

**Resultado:** com uma conta, `POST /api/publishers {"name": "MCCProgrammer"}` encontra
as tuas extensões no Marketplace e no Open VSX e começa a segui-las. As extensões do
Chrome entram pelo ID. `GET /api/extensions` mostra os installs de hoje e o ganho da
semana, e `GET /api/extensions/{id}/stats` mostra a série dia a dia ou semana a semana.
O Swagger documenta tudo, e um admin pode correr a recolha à mão. 80 testes.

## 6.1 Ficheiros deste passo

### Código novo

| Ficheiro | O que é | Porquê |
| --- | --- | --- |
| `catalog/Publisher.java`, `Extension.java` | Entidades das tabelas `publishers` e `extensions` | O catálogo passa a ser escrito pela API. Referem a conta e o publisher por ID, sem relações JPA |
| `catalog/PublisherRepository.java`, `ExtensionRepository.java` | Repositórios; `findOwned` só devolve a extensão se for do utilizador | A verificação de dono fica numa só consulta |
| `catalog/CatalogService.java` | Adicionar publisher (descoberta em todas as lojas), extensão e listing | As regras do catálogo; as lojas são chamadas antes da transação |
| `catalog/CatalogController.java` | `POST`/`GET /api/publishers`, `POST /api/extensions`, `POST /api/extensions/{id}/listings` | A API do catálogo |
| `stats/StatsQueries.java` | O SQL das estatísticas (`JOIN LATERAL`, `LAG ... PARTITION BY`, `FILTER`) | O cálculo fica onde estão os dados |
| `stats/StatsService.java` | Visão geral, série, semanas, validação do intervalo | O que o SQL não precisa de fazer |
| `stats/StatsController.java` | `GET /api/extensions`, `GET /api/extensions/{id}/stats` | A API das estatísticas |
| `collection/AdminCollectionController.java` | `POST /api/admin/collect` | Correr a recolha agora (só admins), por exemplo logo depois de juntar um publisher |
| `auth/AuthenticatedAccount.java` | Lê o ID da conta a partir do token | Os controllers de outros pacotes não repetem o `Long.parseLong(jwt.getSubject())` |
| `ApiProblem.java` (pacote raiz) | `ApiProblem.of(HttpStatus.CONFLICT, "...")` | Um erro Problem Details numa linha |
| `OpenApiConfiguration.java` (pacote raiz) | Título, descrição e o esquema "Bearer" do Swagger | Para o botão "Authorize" aceitar o token |

### Código alterado

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `store/DiscoveredListing.java` (+ os dois adaptadores) | Campo novo `extensionKey` | A mesma extensão tem IDs diferentes no Marketplace e no Open VSX; a chave junta-as |
| `catalog/Listing.java`, `ListingRepository.java` | Construtor para criar; `existsByStoreAndExternalId` | As listings passam a ser criadas pela API |
| `auth/SecurityConfiguration.java` | `/v3/api-docs/**` e `/swagger-ui/**` públicos | A documentação lê-se sem login |
| `pom.xml` | `springdoc-openapi-starter-webmvc-ui` 3.1.1 | Swagger. A 3.1.1 foi feita para o Boot 4.1.0, a nossa linha; a 3.2.0 tinha 2 dias e pede o 4.1.2 |

### Testes

| Ficheiro | O que testa |
| --- | --- |
| `catalog/CatalogApiTests.java` | 11 testes com os adaptadores reais e o WireMock: publisher em duas lojas = 5 extensões e 10 listings; listar; repetido = 409; já seguido por outra conta = 409; desconhecido = 404; loja em baixo = 502 e nada gravado; nome inválido = 400; extensão do Chrome pelo ID; ID que a loja não tem = 422; ID inválido = 400; extensão de outra conta = 404; sem login = 401 |
| `stats/StatsApiTests.java` | 6 testes com fotografias escritas por SQL: visão geral e ordem; série diária com um dia em falta, uma listing que entra tarde e uma descida; semanas ISO; 30 dias por omissão; intervalos inválidos; outra conta = 404 |
| `collection/AdminCollectionApiTests.java` | Admin corre a recolha; utilizador = 403 |
| `OpenApiTests.java` | `/v3/api-docs` sem login, com os caminhos e o esquema Bearer; o Swagger UI abre |
| `TestAccounts.java` | Cria contas e tokens através da API real, para os testes de vários pacotes |
| `TestcontainersConfiguration.java` | Passou a **um só PostgreSQL para todos os testes** (ver 6.5) |
| `SchemaTests.java` | Apaga as contas antes de cada teste (ver 6.5) |

### Documentação

[ADR 0008](../decisions/0008-catalog-and-stats.md), README (tabela de endpoints e exemplos) e este capítulo.

## 6.2 Juntar um publisher

```
POST /api/publishers {"name": "MCCProgrammer"}
  1. Já o tens? → 409
  2. Pergunta a cada loja (fora de qualquer transação):
       Marketplace → 5 listings      Open VSX → 5 listings      Chrome → nenhuma
     Uma loja falhou? → 502 e nada é gravado
  3. Alguma destas listings já é seguida? → 409
  4. Numa transação: o publisher, uma extensão por chave, uma listing por loja
```

**A chave da extensão.** O Marketplace chama-lhe `MCCProgrammer.diff-checker` e o Open
VSX `MCCProgrammer/diff-checker`. Os dois adaptadores devolvem também
`extensionKey = "mccprogrammer.diff-checker"`: o ID de extensão do VS Code, em
minúsculas. Fica no pacote `store`, porque são os adaptadores que conhecem o formato
de cada loja.

**Nomes repetidos.** Ao experimentar com o publisher real PKief, apareceram duas
extensões chamadas "Material Icon Theme" (a normal e a preview). Como uma extensão é
única por (publisher, nome), a segunda fica "Material Icon Theme
(pkief.material-icon-theme-preview)".

**Extensões do Chrome.** A loja não pesquisa por publisher, por isso entram em dois
passos: `POST /api/extensions {"publisherId": 1, "displayName": "Unified Inbox for Gmail"}`
e depois `POST /api/extensions/{id}/listings {"store": "CHROME_WEB_STORE", "externalId": "dmdm..."}`.
Antes de gravar, o ExtPulse lê a página uma vez: um ID que a loja não tem dá 422, e
um ID mal formado dá 400.

**Uma limitação consciente.** Uma listing é única por (loja, ID), por isso só uma conta
pode seguir uma extensão. Para o ExtPulse de hoje (cada developer segue as suas) chega.
Está explicado no [ADR 0008](../decisions/0008-catalog-and-stats.md).

**O dono.** "A extensão 7 não é tua" responde 404, igual a "não existe", para os IDs
não revelarem o que outras contas seguem.

## 6.3 As estatísticas, em SQL

As duas consultas estão em `StatsQueries.java`. Seguem duas regras que vieram dos dados reais.

### Regra 1: um dia sem fotografia usa o último número conhecido

Se a recolha do Open VSX falhar num dia, o total desse dia não pode cair 3811 installs.
Para cada dia e cada listing, procura-se a última fotografia até esse dia:

```sql
JOIN LATERAL (
    SELECT installs FROM snapshots
    WHERE listing_id = l.id AND taken_on <= days.day
    ORDER BY taken_on DESC
    LIMIT 1
) s ON true
```

O `LATERAL` deixa a subconsulta usar `l.id` e `days.day` da linha de fora. "A última
fotografia de uma listing até um dia" é exatamente o que o índice `(listing_id,
taken_on)` do passo 2 responde. Os dias saem de `generate_series`.

### Regra 2: o ganho conta-se por listing e depois soma-se

```sql
installs - LAG(installs) OVER (PARTITION BY listing_id ORDER BY day) AS gain
```

O `LAG` vai buscar o valor da linha anterior. O `PARTITION BY listing_id` faz isso
dentro de cada listing. No primeiro dia de uma listing, o `LAG` é `NULL` e o ganho
também. Depois, `sum(gain)` soma os ganhos conhecidos (o `sum` ignora os `NULL`).

**Porque importa:** sem o `PARTITION BY`, no dia em que juntasses o Open VSX o total
saltava 500 e a API dizia "+495 installs hoje". **Confirmado:** com o cálculo pelo
total, o teste falha exatamente com `495` em vez de `-5`.

**Os ganhos podem ser negativos.** As lojas contam desinstalações (vimos o Markify
passar de 179 para 177), e o teste tem um dia com -5.

### O resto

- **Por loja:** `sum(installs) FILTER (WHERE store = 'OPEN_VSX')`, uma soma por coluna sem subconsultas.
- **Ganho da semana na visão geral:** installs de hoje menos installs de há 7 dias, por
  listing. Uma listing sem fotografia de há 7 dias fica de fora (`weekGain: null`), em
  vez de contar como ganho tudo o que tem.
- **Semanas:** semanas ISO (segunda a domingo), agrupadas em Java a partir da série
  diária. Cada semana fica com os installs do último dia e a soma dos ganhos.
- **Intervalo:** por omissão os últimos 30 dias em Lisboa; no máximo 366; `from` depois de `to` = 400.
- **Calculado ao ler, sem tabelas de resumo.** Não há totais guardados para manter
  certos. Se um dia ficar lento, uma tabela diária preenchida pela recolha resolve.

## 6.4 Swagger (springdoc-openapi)

Com a app a correr, abre <http://localhost:8080/swagger-ui.html>.

1. `POST /api/auth/login` → "Try it out" → copia o `token`.
2. Botão **Authorize** → cola o token.
3. Experimenta qualquer endpoint.

A descrição em JSON (OpenAPI 3) está em `/v3/api-docs`. Gera-se a partir dos
controllers: um endpoint novo aparece sozinho.

## 6.5 Um só PostgreSQL nos testes

Antes, cada configuração de teste diferente arrancava o seu PostgreSQL (eram 3). Agora o
contentor é um campo `static` partilhado por todos, e `destroyMethod = ""` impede que o
primeiro contexto a fechar o pare.

**O que isto mostrou logo:** o `SchemaTests` falhou 8 vezes com `duplicate key
users_email_lower_key`. Com contentores separados, ele tinha sempre uma base vazia.
Agora encontrou as contas que outros testes gravaram. A regra passou a ser explícita:
**cada teste apaga o que precisa antes de começar.** É a regra que já devia valer, e
partilhar o contentor tornou-a visível.

## 6.6 Experimentar

```bash
./mvnw spring-boot:run
```

Depois segue a secção "Try the API" do README, ou o Swagger. Se a tua base de dados
ainda tem as 12 listings que pus por SQL (na conta `dev@extpulse.local`), juntar o
MCCProgrammer à tua conta dá 409, porque já são seguidas. Duas opções:

```sql
-- (a) passar o publisher e o histórico para a tua conta
UPDATE publishers SET user_id = (SELECT id FROM users WHERE email = 'tu@example.com')
WHERE user_id = (SELECT id FROM users WHERE email = 'dev@extpulse.local');

-- (b) apagar a conta de desenvolvimento (e o histórico), e depois fazer POST /api/publishers
DELETE FROM users WHERE email = 'dev@extpulse.local';
```

**Confirmado na base de dados de desenvolvimento**, com uma conta temporária:

- A visão geral mostrou as 7 extensões; o Visualizer com 20 007 (16 196 + 3 811).
- O `weekGain` veio `null`, porque ainda só há um dia de histórico.
- `POST /api/admin/collect` recolheu 12 de 12 em 5 s.
- A descoberta real do PKief encontrou 7 extensões, 6 delas nas duas lojas.

A base de dados ficou como estava.

## 6.7 Confirmar que funciona

```bash
./mvnw verify      # 80 testes
```

## 6.8 Perguntas para saberes responder

- Como sabes que `MCCProgrammer.diff-checker` e `MCCProgrammer/diff-checker` são a mesma extensão?
- O que faz um `JOIN LATERAL`? Porque é que aqui é rápido?
- O que faz `LAG(...) OVER (PARTITION BY ...)`? O que acontecia sem o `PARTITION BY`?
- Porque é que um dia sem dados não é zero?
- Porque é que as lojas são chamadas antes da transação, e não dentro?
- Porque é que a extensão de outra conta responde 404 e não 403?
- Porque é que as estatísticas são calculadas ao ler e não guardadas?
- Porque é que os testes partilham um só PostgreSQL, e que cuidado isso exige?
