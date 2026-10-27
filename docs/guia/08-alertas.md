# 8. Alertas (email e Discord) e resumo semanal

**Resultado:** cada conta escolhe o que quer saber (avaliações com poucas estrelas,
picos de installs, versões novas, listings que deixaram de ser recolhidas, resumo
semanal) e onde: por **email** ou num canal do **Discord**. Os alertas são verificados
no fim de cada recolha e saem **uma só vez**, mesmo que a recolha corra outra vez. 118
testes, 94,4% das linhas cobertas.

## 8.1 Os alertas

| Tipo | Quando avisa | `threshold` | Exemplo |
| --- | --- | --- | --- |
| `LOW_RATING` | Avaliação nova com poucas estrelas | Máximo de estrelas, 1–4 (2 por omissão) | "1★ review of Diff Checker on VS Code Marketplace" |
| `INSTALL_SPIKE` | O ganho de um dia é ≥ `threshold` **e** mais do dobro do habitual (média dos 14 dias antes) | Ganho mínimo (10 por omissão) | "Diff Checker: +60 installs on VS Code Marketplace on 26 Oct. Usually about 4 a day…" |
| `NEW_VERSION` | Uma listing mostra outra versão | — | "Diff Checker 0.3.1 is on VS Code Marketplace. The version before was 0.3.0." |
| `COLLECTION_FAILING` | Uma listing está 2 dias sem números novos (ou nunca foi recolhida) | — | "No new numbers for … since 2026-10-24. Last error: stats: …" |
| `WEEKLY_SUMMARY` | Segundas às 08:00, sobre a semana anterior | — | "Week of 19 Oct–25 Oct: +175 installs (+4.3%)" + uma linha por extensão |

**Avaliações antigas não são notícia.** Quando juntas um publisher, a primeira recolha
encontra todas as avaliações de sempre. Só avisam as escritas **depois** de começares
a seguir a listing. Por isso o teu 1★ de janeiro não gerou alerta na recolha real.

## 8.2 Como funciona: deteção, *outbox* e entrega

```
CollectionService.collect()  →  publica CollectionFinished (evento do Spring)
                                       │
AlertJobs @EventListener  ─────────────┘
   1. AlertService.evaluate()   → AlertDetector (SQL) encontra o que avisar
                                 → AlertOutbox.add(): INSERT ... ON CONFLICT DO NOTHING
   2. AlertService.deliverPending() → para cada alerta por enviar:
                                 EmailAlertSender ou DiscordAlertSender
                                 → enviado: sent_at; falhou: attempts + 1, last_error
A cada 5 minutos: deliverPending() outra vez (retries, até 5 tentativas)
Segundas às 08:00: weeklySummary() + deliverPending()
```

### Três ideias que vale a pena saberes explicar

**1. O *outbox* (caixa de saída).** Um alerta é primeiro **escrito** na tabela
`alert_events` e só depois **enviado**. Se o envio falhar (servidor de email em baixo,
Discord a limitar pedidos), o alerta fica lá e volta a ser tentado. Se fosse enviado
logo na verificação, uma falha perdia-o.

**2. A chave de deduplicação.** Cada alerta tem uma chave que diz sobre o que é:
`review:42`, `version:7:0.3.1`, `spike:7:2026-10-26`, `failing:7:2026-10-24`,
`summary:2026-10-19`. A tabela tem `UNIQUE (rule_id, dedup_key)`, e a inserção é
`ON CONFLICT DO NOTHING`. Verificar duas vezes encontra a mesma chave e não acrescenta
nada. É a mesma ideia de idempotência das fotografias, aplicada aos alertas. Por isso
as verificações podem olhar mais para trás (as avaliações dos últimos 2 dias): se uma
verificação não correu, a seguinte apanha o que ficou.

**3. Um evento em vez de uma chamada direta.** A recolha publica `CollectionFinished`
e não sabe que os alertas existem. O pacote `alerts` ouve o evento com `@EventListener`.
É o mesmo desenho que, na v3, passa para o Kafka: a recolha publica e quem quiser ouve.
O listener apanha qualquer erro, para que um problema nos alertas nunca faça falhar a
recolha.

### A deteção, em SQL

Cada tipo é uma consulta que parte das regras e chega às listings do dono da regra:

```sql
FROM alert_rules ar
JOIN publishers p ON p.user_id = ar.user_id
JOIN extensions e ON e.publisher_id = p.id
JOIN listings l ON l.extension_id = e.id
```

- **Pico:** três `JOIN LATERAL` às fotografias da listing: a de hoje (`cur`), a de
  ontem (`prev`) e a de há 14 dias ou mais (`base`). O habitual é
  `(prev - base) / dias`, e o pico é `cur - prev > 2 × habitual` e `≥ threshold`. Só
  conta com dias seguidos e duas semanas de histórico.
- **Versão nova:** a última fotografia contra a anterior, com `IS DISTINCT FROM`.
- **Sem números:** a última fotografia tem 2 dias ou mais. Junta-se o último erro de
  `collection_run_errors`, para o alerta dizer porquê.
- **Resumo semanal:** por listing, os installs no domingo e no domingo anterior, e as
  avaliações vistas pela primeira vez nessa semana. O Java junta tudo por extensão.

## 8.3 Ficheiros deste passo

### Código novo (`alerts/`)

| Ficheiro | O que é |
| --- | --- |
| `AlertType.java`, `AlertChannel.java` | Os 5 tipos e os 2 canais |
| `AlertRule.java`, `AlertRuleRepository.java` | A regra (JPA): tipo, `threshold`, canal, destino |
| `AlertDetector.java` | As 5 consultas de deteção e o texto de cada alerta |
| `AlertOutbox.java` | A tabela `alert_events`: acrescentar sem repetir, pendentes, enviado, falhou |
| `AlertSender.java`, `EmailAlertSender.java`, `DiscordAlertSender.java` | Os canais |
| `AlertService.java` | Regras (validação), verificar, enviar, alerta de teste |
| `AlertJobs.java` | Quando: depois de cada recolha, a cada 5 minutos, às segundas |
| `AlertController.java` | `GET`/`POST /api/alerts`, `DELETE /api/alerts/{id}`, `POST /api/alerts/{id}/test` |
| `AlertProperties.java`, `AlertConfiguration.java` | `extpulse.alerts.*` |
| `collection/CollectionFinished.java` | O evento que a recolha publica |
| `db/migration/V5__create_alerts.sql` | `alert_rules` e `alert_events` |

### Código alterado

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `collection/CollectionService.java` | Publica `CollectionFinished` no fim | Os alertas reagem sem a recolha os conhecer |
| `store/Store.java` | `displayName()`: "VS Code Marketplace", "Open VSX"... | Nomes legíveis nas mensagens |
| `store/MarketplaceSource.java` | Repete uma vez a consulta quando a ligação cai | Ver 8.6 |
| `catalog/CatalogService.java` | Regista no log a causa de uma loja falhar na descoberta | Um 502 sem causa no log não se consegue investigar |
| `application.yaml` | `spring.mail` (Mailpit), `extpulse.alerts.*`, `management.health.mail.enabled: false` | Ver 8.5 |
| `compose.yaml` | Serviço `mailpit` | Ver os emails em desenvolvimento |
| `pom.xml` | `spring-boot-starter-mail`; `greenmail-junit5` 2.1.14 (testes) | Email e um servidor SMTP real nos testes |

### Testes

| Ficheiro | O que testa |
| --- | --- |
| `alerts/AlertDetectionTests.java` | 9 testes, com o relógio fixo numa segunda-feira: 1★ novo avisa uma vez; 5★, antigo ou já visto não avisa; `threshold` 3; versão nova (e a primeira fotografia ou uma mudança antiga não); pico (e ganho pequeno ou pouco histórico não); sem números com o último erro, e "nunca recolhida"; resumo semanal com o texto completo; contas de outras pessoas não avisam |
| `alerts/AlertApiAndDeliveryTests.java` | 10 testes com o WireMock no lugar do Discord e o GreenMail como servidor SMTP: email para a conta por omissão; Discord só com URL do Discord, e o token escondido; `threshold` e tipos inválidos; repetido = 409; só o dono apaga ou testa; teste chega ao Discord sem mencionar ninguém; teste chega à caixa de email; canal em baixo = 502; depois de uma recolha, o 1★ vai **uma vez** ao Discord (com `@everyone` no texto, sem mencionar ninguém); falhou → tenta 5 vezes e desiste; email em baixo → sai na tentativa seguinte |
| `store/MarketplaceSourceTests.java` | A consulta repete-se quando a ligação cai; com duas quedas é `StoreException` |
| `collection/CollectionServiceTests.java` | O construtor recebe o publicador de eventos |

### Documentação

[ADR 0010](../decisions/0010-alerts.md), README (endpoints, Mailpit) e este capítulo.

## 8.4 Discord: como criar o webhook, e a segurança

**Criar:** no Discord, abre as definições do canal → Integrations → Webhooks → New
Webhook → Copy Webhook URL. Depois:

```bash
curl -X POST localhost:8080/api/alerts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type": "LOW_RATING", "channel": "DISCORD", "target": "https://discord.com/api/webhooks/..."}'
curl -X POST localhost:8080/api/alerts/1/test -H "Authorization: Bearer $TOKEN"   # chega ao canal?
```

**Três cuidados de segurança:**

1. **Só URLs do Discord (contra SSRF).** O servidor faz um pedido a um URL escrito por
   um utilizador. Sem verificação, alguém podia pôr `http://169.254.169.254/...`, o
   endereço de metadados da cloud, e usar o ExtPulse para chegar a sítios internos.
   Por isso o URL tem de começar por `https://discord.com/api/webhooks/` (ou
   `discordapp.com`). É verificado ao gravar e outra vez ao enviar. Na recolha real,
   esse endereço deu 400.
2. **Ninguém é mencionado.** O texto de uma avaliação pode ter `@everyone`. O pedido
   ao Discord leva `allowed_mentions: {parse: []}`, por isso não há menções. **Confirmado:**
   sem isto, dois testes falham.
3. **O token não aparece.** Quem tem o URL completo pode escrever no canal. A API
   mostra `https://discord.com/api/webhooks/123/****`.

## 8.5 Email: Mailpit em desenvolvimento, GreenMail nos testes

- **Mailpit** (no `compose.yaml`) é uma caixa de email falsa: a app envia para
  `localhost:1025` e tu vês os emails em <http://localhost:8025>. Nada sai do teu computador.
- **GreenMail** é um servidor SMTP que corre dentro dos testes. Os testes leem os emails
  recebidos (assunto, destinatário, texto).
- **Em produção:** `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` e
  `SPRING_MAIL_PASSWORD` (por exemplo, o Amazon SES por SMTP).
- **`management.health.mail.enabled: false`:** o Spring Boot junta sozinho o servidor de
  email ao `/actuator/health`. Com o servidor em baixo, a app inteira ficava "doente" e
  a plataforma de deploy reiniciava-a sem razão. Os alertas que não saem ficam no
  *outbox*, por isso o email não deve contar para a saúde da app.

## 8.6 Problemas que apareceram

| Problema | Causa | Solução |
| --- | --- | --- |
| `/actuator/health` passou a 503 nos testes | O Spring Boot junta o servidor de email ao health, e nos testes não há SMTP em `localhost:1025` | `management.health.mail.enabled: false` (ver 8.5) |
| Dois testes de email falhavam | Um teste pára e volta a ligar o GreenMail, e com porta dinâmica ele voltava noutra porta | Escolher uma porta livre uma vez e usar sempre essa |
| Um teste do catálogo falhou **uma vez** em cerca de 10 execuções (502 no primeiro pedido) | O cliente HTTP do Java reutiliza ligações abertas. Se o servidor fechou uma, repete sozinho um `GET`, mas nunca um `POST`. O Marketplace é a única loja que usamos com `POST` | A consulta do Marketplace (só de leitura, mas `POST`) repete-se uma vez quando a ligação cai. Há um teste determinístico que corta a ligação com o WireMock. Também em produção: sem isto, juntar um publisher podia falhar por acaso |
| O Mailpit não arrancou com a app | Com o PostgreSQL já a correr, o Spring Boot não arranca os outros serviços do `compose.yaml` | `docker compose up -d` uma vez (está no README) |

O terceiro é o mais interessante: um teste intermitente não se resolve a correr outra
vez até passar. Procura-se a causa. Aqui, o registo mostrou o 502. A diferença entre
`GET` e `POST` explicou porque só falhava o Marketplace. E o teste novo prova a correção
sem depender da sorte.

## 8.7 Experimentar

```bash
docker compose up -d                       # PostgreSQL e Mailpit
./mvnw spring-boot:run
# com um token: um alerta por email (vai para o email da conta) e um teste
curl -X POST localhost:8080/api/alerts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type": "LOW_RATING", "channel": "EMAIL"}'
curl -X POST localhost:8080/api/alerts/1/test -H "Authorization: Bearer $TOKEN"
# abre http://localhost:8025
```

**Confirmado na base de dados de desenvolvimento** (conta temporária):

- Os 5 tipos de alerta foram criados, com os `threshold` por omissão.
- O URL de metadados da cloud deu 400.
- O alerta de teste chegou ao Mailpit.
- A recolha manual recolheu 12 de 12 sem alertas novos: o teu 1★ é de antes de
  começares a seguir a extensão.

Tudo foi reposto no fim.

## 8.8 Confirmar que funciona

```bash
./mvnw verify      # 118 testes, cobertura 94,4% das linhas
```

## 8.9 Perguntas para saberes responder

- Como garantes que o mesmo alerta não sai duas vezes? E se a recolha correr três vezes no mesmo dia?
- O que é o padrão *outbox*, e que problema resolve?
- Porque é que a recolha publica um evento em vez de chamar os alertas?
- Como defines um "pico" de installs sem gerar alertas a mais?
- O que é SSRF, e como o evitas num webhook?
- Porque é que o email não conta para o `/actuator/health`?
- O ExtPulse entrega cada alerta "exatamente uma vez" ou "pelo menos uma vez"? Porquê?
- Porque é que o cliente HTTP repete um `GET` sozinho e não um `POST`?
