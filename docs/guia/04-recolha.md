# 4. Recolha diária

**Resultado:** todos os dias às 06:00 (hora de Lisboa), o ExtPulse pede às lojas os
números de cada listing e grava a fotografia do dia. Correr duas vezes não duplica
nada, uma loja em baixo não pára as outras, e cada execução fica registada. Testado
com as tuas 12 listings reais: 12 recolhidas, 0 falhas, em 6 segundos. 47 testes.

## 4.1 Ficheiros deste passo

### Código novo

| Ficheiro | O que é | Porquê |
| --- | --- | --- |
| `catalog/package-info.java` | Pacote novo `catalog` | Publishers, extensões e listings são lidos por vários pacotes (recolha, estatísticas, página pública); não pertencem a nenhum deles |
| `catalog/Listing.java` | Primeira entidade JPA, mapeada para `listings` | A recolha precisa de saber que listings existem. Só leitura por agora: as listings são criadas pela API no passo 6 |
| `catalog/ListingRepository.java` | Repositório Spring Data | `findAll(Sort.by("id"))` sem escrever SQL |
| `TimeConfiguration.java` (pacote raiz) | Um `Clock` para toda a app, em `extpulse.zone` | "Hoje" é o dia em Lisboa. Os testes trocam o relógio por um fixo |
| `collection/CollectionProperties.java` | `record` com tentativas, espera inicial e intervalo por loja | Configurável no `application.yaml`; os testes usam milissegundos |
| `collection/CollectionConfiguration.java` | Liga o agendamento (`@EnableScheduling`) e as propriedades | Configuração do pacote fica no pacote |
| `collection/CollectionRun.java` | Entidade de `collection_runs`, com a lista de erros | Saber quando correu, quanto recolheu e o que falhou |
| `collection/CollectionRunStatus.java` | `RUNNING`, `SUCCEEDED`, `PARTIAL`, `FAILED` | O estado de uma execução numa palavra |
| `collection/CollectionError.java` | `record` `@Embeddable`: listing, loja, ID, mensagem | Uma linha de `collection_run_errors` |
| `collection/CollectionRunRepository.java` | Repositório das execuções | Gravar e ler execuções |
| `collection/SnapshotWriter.java` | O `INSERT ... ON CONFLICT DO UPDATE` | A gravação idempotente; SQL direto, porque o JPA não faz upserts |
| `collection/ResilientStoreCalls.java` | Retries e ritmo por loja, com Resilience4j | Uma falha momentânea não perde o dia, e nenhuma loja recebe pedidos a mais |
| `collection/CollectionService.java` | `collect()`: o ciclo pelas listings | O coração do passo. `public`, porque o passo 6 vai chamá-lo por um endpoint de admin |
| `collection/DailyCollectionJob.java` | `@Scheduled(cron = ..., zone = ...)` que chama `collect()` | Separado do serviço: o agendamento é só um dos sítios de onde se chama a recolha |

### Base de dados, configuração e build

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `db/migration/V2__create_collection_runs.sql` | Tabelas `collection_runs` e `collection_run_errors` | Histórico das execuções e das falhas. É uma migração nova: a `V1` nunca se altera |
| `application.yaml` | `extpulse.zone` e o bloco `extpulse.collection` | Fuso, cron, tentativas, esperas e ritmo num só sítio |
| `pom.xml` | `resilience4j-retry` e `resilience4j-ratelimiter` 2.4.0 | Retries e ritmo |

### Testes

| Ficheiro | O que testa |
| --- | --- |
| `collection/CollectionServiceTests.java` | 6 testes contra um PostgreSQL real com lojas falsas: grava a fotografia do dia; correr duas vezes atualiza em vez de duplicar; o dia seguinte acrescenta; o dia é o de Lisboa; uma loja a falhar não pára as outras; tudo a falhar = `FAILED` |
| `collection/ResilientStoreCallsTests.java` | 5 testes sem Spring: repete até a loja responder; desiste à 4.ª tentativa com esperas crescentes; não repete "não encontrada" nem um ID inválido; espaça os pedidos à mesma loja mas não entre lojas |
| `collection/DailyCollectionScheduleTests.java` | O agendamento corre às 05:00 UTC no verão e às 06:00 UTC no inverno, ou seja, sempre às 06:00 em Lisboa |
| `collection/FakeStoreSource.java` | Uma loja falsa que responde o que o teste mandar e guarda quando foi chamada |
| `TestcontainersConfiguration.java` | Passou a `public` | Os testes do pacote `collection` também precisam dela |

### Documentação

[ADR 0006](../decisions/0006-daily-collection.md), README (estado, stack, pacote `catalog`) e este capítulo.

## 4.2 Como funciona uma execução

```
DailyCollectionJob (06:00 Lisboa)
  └─ CollectionService.collect()
       1. grava a execução como RUNNING
       2. lê todas as listings
       3. para cada listing:
            ResilientStoreCalls → StoreSource.fetchStats()   (ritmo + retries)
            SnapshotWriter.save()                            (upsert do dia)
            erro? → regista em collection_run_errors e segue
       4. fecha a execução: SUCCEEDED, PARTIAL ou FAILED
```

## 4.3 As decisões, uma a uma

### Idempotência: `ON CONFLICT DO UPDATE`

```sql
INSERT INTO snapshots (listing_id, taken_on, installs, ...)
VALUES (...)
ON CONFLICT (listing_id, taken_on) DO UPDATE SET installs = EXCLUDED.installs, ...
```

Se já existe a fotografia dessa listing nesse dia, atualiza os números (`EXCLUDED` são
os valores que se tentaram inserir) e o `collected_at`. É por isto que a restrição
`UNIQUE (listing_id, taken_on)` do passo 2 existe. Correr a recolha outra vez é a
forma segura de recuperar um dia que falhou.

**Confirmado:** sem o `ON CONFLICT`, o teste `runningTwiceOnTheSameDay...` falha. E
contra as lojas reais, duas execuções seguidas deram 12 fotografias, não 24.

### Falhas isoladas

Cada listing está dentro do seu `try/catch`. Uma exceção é registada (listing, loja, ID,
mensagem) e o ciclo continua. Apanha-se `RuntimeException` de propósito: numa tarefa em
lote, qualquer erro de uma listing (loja, página, base de dados) não deve parar as outras.

### Retries com espera crescente e ritmo por loja (Resilience4j)

- **Retry:** até 4 tentativas, com esperas de 1 s, 4 s e 16 s (`IntervalFunction.ofExponentialBackoff`
  com multiplicador 4). Só se repete uma `StoreException`. Uma `ListingNotFoundException`
  ou um ID inválido falham logo, porque perguntar outra vez não muda a resposta.
- **Rate limiter:** um por loja, com 1 autorização por segundo. Cada tentativa,
  incluindo os retries, espera a sua vez. Lojas diferentes não esperam umas pelas outras.
- **Usados diretamente, sem anotações.** O `@Retry` do starter funciona com proxies, e
  uma chamada de dentro da própria classe passa ao lado sem aviso. Dois objetos
  explícitos leem-se e testam-se melhor ([ADR 0006](../decisions/0006-daily-collection.md)).

### Nenhuma transação à volta da execução

Uma execução faz pedidos à internet que podem demorar segundos. Se tudo estivesse numa
transação, uma ligação à base de dados ficava presa esse tempo todo, e um erro no fim
desfazia as fotografias já gravadas. Assim, cada fotografia é gravada logo, na sua
própria transação curta.

### "Hoje" é o dia em Lisboa

O `Clock` está em `Europe/Lisbon`. Às 23:30 UTC de 15 de julho já é dia 16 em Lisboa,
e a fotografia fica com `taken_on = 2026-07-16`. Há um teste só para isso. O agendamento
usa o mesmo fuso (`zone = "${extpulse.zone}"`), por isso as 06:00 acompanham a hora de
verão. As datas e horas guardam-se sempre em UTC (`timestamptz`). Só o *dia* depende do fuso.

### JPA e SQL lado a lado

`Listing` e `CollectionRun` são entidades JPA: o Spring Data dá os repositórios sem
código. O upsert é SQL direto com `JdbcClient`, porque o JPA não tem "insere ou
atualiza" numa só instrução. Em entrevista: *usa-se JPA para o modelo e SQL onde o SQL é
o ponto*. As estatísticas do passo 6 (com `LAG`) vão pelo mesmo caminho.

**Truques do mapeamento:**
- Não há `@Column(name = ...)`: o Spring Boot converte `externalId` em `external_id` sozinho.
- `@Enumerated(EnumType.STRING)` grava o nome do `enum` (`OPEN_VSX`), nunca a posição
  (`1`), que mudaria se alguém reordenasse o `enum`.
- `CollectionError` é um `record` `@Embeddable` numa `@ElementCollection`: os erros não
  têm vida própria, pertencem à execução.
- O `ddl-auto: validate` confirmou ao arrancar que as entidades batem com as tabelas.

### Erros numa tabela e não numa coluna JSON

O documento do projeto previa `errors jsonb`. Uma tabela à parte deixa responder a "que
listings falharam esta semana?" com um `JOIN` simples. Se a listing for apagada, o
`listing_id` passa a `NULL` (`ON DELETE SET NULL`), mas a loja e o ID ficam, para o
histórico.

## 4.4 O que ficou para depois

- **Endpoint `POST /api/admin/collect`** (correr à mão): passo 6, com a API.
- **Avaliações:** passo 7.
- **Alertas no fim de cada execução:** passo 8.
- **ShedLock** (só uma instância a recolher): quando houver mais do que um servidor.
- **Juntar listings sem SQL:** passo 6 (`POST /api/publishers` faz a descoberta e cria-as).

**Uma coisa que os dados reais mostraram:** o Markify no Marketplace tinha 179 installs
numa consulta e 177 meia hora depois. O número de installs pode descer (desinstalações). As estatísticas
do passo 6 têm de aceitar ganhos negativos.

## 4.5 Experimentar com as lojas reais

```bash
# Corre a recolha a cada minuto em vez de às 06:00 (a variável de ambiente ganha ao yaml)
EXTPULSE_COLLECTION_CRON='0 * * * * *' ./mvnw spring-boot:run
```

Para haver o que recolher, enquanto não há API, as listings entram por SQL na base de
dados de desenvolvimento (`docker compose exec postgres psql -U extpulse`). Depois:

```sql
SELECT id, status, listings_total, listings_succeeded, listings_failed FROM collection_runs;
SELECT * FROM snapshots ORDER BY listing_id, taken_on;
```

**Atenção:** `-Dspring-boot.run.arguments="--extpulse.collection.cron=0 * * * * *"` não
funciona. O plugin do Maven parte o argumento nos espaços e o cron chega como `"0"`. A
variável de ambiente evita isso, e é também assim que a produção vai receber configuração.

## 4.6 Confirmar que funciona

```bash
./mvnw verify      # 47 testes
```

## 4.7 Perguntas para saberes responder

- O que acontece se a recolha correr duas vezes no mesmo dia? E se correr às 00:30 de Lisboa?
- Se o Open VSX estiver em baixo, o que acontece às listings do Marketplace?
- Porque é que uma `ListingNotFoundException` não é repetida?
- Porque é que não há uma transação à volta da execução inteira?
- Porque é que o upsert é SQL e não JPA?
- Porque é que `EnumType.STRING` e não `ORDINAL`?
- O que acontece com dois servidores a correr a app? Como resolverias?
- Como testas código que depende da hora atual?
