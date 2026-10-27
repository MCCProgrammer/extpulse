# Ferramentas e registo

O que foi feito em cada passo e porquê, os problemas que apareceram, e cada
ferramenta do projeto: para que serve, porque foi escolhida e em que já ajudou.
Atualiza-se no fim de cada passo.

As decisões formais e curtas estão em [`../decisions`](../decisions) (em inglês,
no formato que as empresas usam). Este ficheiro é a explicação longa, para estudares
e para preparares as entrevistas.

## Registo

Do mais antigo para o mais recente.

| Data | Passo | O que foi feito | Porquê |
| --- | --- | --- | --- |
| 2026-10-21 | Plano | Documento do projeto: funcionalidades por versão, fontes de dados testadas contra as APIs reais das três lojas, modelo de dados, API, stack e calendário | Decidir antes de programar. Testar as APIs primeiro evita descobrir a meio que uma loja não dá os dados (a Chrome Web Store não tem API pública: só se pode ler a página) |
| 2026-10-21 | 1 | Projeto gerado no Spring Initializr: Spring Boot 4.1.1, Java 25, Maven | O Initializr é a forma oficial de começar um projeto Spring, com as versões compatíveis entre si |
| 2026-10-21 | 1 | PostgreSQL 17 em `compose.yaml` para desenvolvimento e Testcontainers para os testes | A mesma base de dados em todo o lado, sem instalar o PostgreSQL à mão ([0004](../decisions/0004-docker-compose-and-testcontainers.md)) |
| 2026-10-21 | 1 | `application.yaml` com `ddl-auto: validate`, `open-in-view: false`, UTC e só `health` e `info` expostos | O Flyway é o único que mexe nas tabelas, nada de consultas escondidas nos controllers e nada de dados internos à vista |
| 2026-10-21 | 1 | Um pacote por funcionalidade, cada um com `package-info.java` | A arquitetura do documento fica escrita no próprio código ([0002](../decisions/0002-modular-monolith.md)) |
| 2026-10-21 | 1 | Teste do `/actuator/health` (`HealthEndpointTests`) | Um só teste que prova que a app, o Flyway e o PostgreSQL funcionam juntos |
| 2026-10-21 | 1 | GitHub Actions a correr `./mvnw -B verify` em cada push e pull request | Prova que o projeto compila e passa nos testes fora do teu Mac |
| 2026-10-21 | 1 | README, ADRs 0001–0004 e o capítulo 1 do guia | O README é o que um recrutador lê primeiro; os ADRs respondem a "porque escolheste isto?" |
| 2026-10-21 | 1 | Docker Desktop instalado; `./mvnw verify` passa com 2 testes | Os testes precisam do Docker para arrancar o PostgreSQL |
| 2026-10-21 | 1 | Repositório privado no GitHub, primeiro commit e primeiro push; CI verde | O código fica guardado fora do Mac e a CI corre sozinha |
| 2026-10-21 | 2 | Capítulo 2 do guia (esquema da base de dados); a migração e os testes ficam para escreveres tu | És tu que tens de saber explicar o SQL numa entrevista |
| 2026-10-26 | — | Este ficheiro; capítulo 1 reescrito com mais detalhe; README corrigido (JUnit 6) | Ter num só sítio o que foi feito e porquê, e saber onde está cada coisa |
| 2026-10-26 | 2 | Migração `V1__create_core_tables.sql`: `users`, `publishers`, `extensions`, `listings`, `snapshots` | As regras que importam (uma fotografia por dia, lojas válidas, nada negativo) ficam na base de dados, onde um bug no Java não as consegue partir |
| 2026-10-26 | 2 | Índice `listings_extension_id_idx`, além da especificação | O PostgreSQL não indexa chaves estrangeiras sozinho, e o total combinado procura as listings por extensão |
| 2026-10-26 | 2 | `SchemaTests`: 8 testes, um por regra; 10 testes no total | Provar cada regra. Confirmado ao retirar duas restrições: falharam os dois testes certos |
| 2026-10-26 | 2 | Trabalho feito no branch `feat/core-schema`; PR #1 | Primeiro passo com um branch e um pull request por passo, como numa equipa |
| 2026-10-26 | 3 | Interface `StoreSource` e um adaptador por loja (Marketplace, Open VSX, Chrome Web Store) | O resto da app fala com uma interface; uma loja nova é uma classe nova |
| 2026-10-26 | 3 | Chamadas reais às três lojas antes de escrever código; respostas gravadas para os testes | Descobrir como as lojas respondem de facto (estatísticas que faltam, pesquisa do Open VSX que ignora o namespace, página do Chrome sem `<h1>` para IDs desconhecidos) |
| 2026-10-26 | 3 | Chrome lido pelo texto visível com jsoup ([0005](../decisions/0005-store-adapters.md)) | Não há API; as classes CSS são geradas e mudam |
| 2026-10-26 | 3 | 25 testes com WireMock; 3 testes `live` contra as lojas reais, fora do build normal, num workflow semanal | Testes rápidos e estáveis, e um aviso quando uma loja muda |
| 2026-10-26 | 3 | O adaptador do Chrome segue os redirecionamentos ele próprio | Encontrado pelo teste `live`: a loja redireciona para um URL com UTF-8 cru que o JDK não aceita |
| 2026-10-26 | 3 | PR #3 para levar o passo 3 ao `main`; daqui em diante, todos os PRs com base no `main` | O PR #2 tinha base no branch do passo 2 e o merge foi para lá, não para o `main` |
| 2026-10-26 | 4 | Migração `V2`: `collection_runs` e `collection_run_errors` | Histórico de cada execução e do que falhou, consultável com SQL simples |
| 2026-10-26 | 4 | Pacote novo `catalog` e a primeira entidade JPA (`Listing`) | Publishers, extensões e listings são lidos por vários pacotes |
| 2026-10-26 | 4 | `CollectionService` com upsert (`ON CONFLICT DO UPDATE`), falhas isoladas por listing e sem transação à volta da execução | Correr duas vezes é seguro; uma loja em baixo não estraga o dia; nenhuma ligação à base de dados fica presa durante pedidos à internet ([0006](../decisions/0006-daily-collection.md)) |
| 2026-10-26 | 4 | Resilience4j: 4 tentativas (1 s, 4 s, 16 s) e 1 pedido por segundo a cada loja | Falhas momentâneas não perdem o dia; nenhuma loja é sobrecarregada |
| 2026-10-26 | 4 | `@Scheduled` às 06:00 em `Europe/Lisbon` e um `Clock` para toda a app | "Hoje" é o dia em Lisboa, também na hora de verão; os testes fixam a hora |
| 2026-10-26 | 4 | Recolha real das 12 listings na base de dados de desenvolvimento, duas vezes | Prova de ponta a ponta: 12 recolhidas, 0 falhas, 6 s, e 12 fotografias (não 24) depois da segunda execução |
| 2026-10-26 | — | Ordem dos passos mudada: contas e login passam a ser o passo 5, e o catálogo e as estatísticas o 6 | Tudo em `/api` pertence a uma conta, e nada deve ir para um servidor sem login |
| 2026-10-26 | 5 | Spring Security como resource server: login devolve um JWT (HS256, 1 h), verificado em cada pedido; sem sessões ([0007](../decisions/0007-accounts-and-jwt.md)) | Stateless: qualquer instância verifica qualquer token. A mesma configuração serviria para tokens do Keycloak ou do Cognito |
| 2026-10-26 | 5 | Passwords com BCrypt (`DelegatingPasswordEncoder`), máximo 72 bytes; login com a mesma resposta e o mesmo tempo para email desconhecido e password errada | Nunca guardar a password; não revelar que emails têm conta |
| 2026-10-26 | 5 | Migração `V3`: coluna `role` (`USER`/`ADMIN`); `/api/admin/**` só para `ADMIN` | O endpoint de recolha manual (passo 6) é só para administradores |
| 2026-10-26 | 5 | Erros em Problem Details (RFC 9457), com a lista dos campos inválidos | Todas as respostas de erro da API no mesmo formato, que é um standard |
| 2026-10-26 | 5 | 12 testes de API com o MockMvc; experimentado com `curl` na base de dados de desenvolvimento | Registo, conflito, validação, login, token alterado, expirado, de outra chave, regra de admin |
| 2026-10-26 | 6 | `POST /api/publishers`: descoberta em todas as lojas, agrupada pela chave da extensão; lojas chamadas antes da transação, e nada gravado se uma falhar | Um pedido junta tudo; nunca um catálogo a meio |
| 2026-10-26 | 6 | Extensões e listings à mão (`POST /api/extensions`, `.../listings`), com a listing verificada na loja | As extensões do Chrome não se encontram por publisher |
| 2026-10-26 | 6 | Estatísticas em SQL: último número conhecido nos dias em falta (`JOIN LATERAL`), ganho por listing (`LAG ... PARTITION BY`), por loja (`FILTER`), semanas ISO ([0008](../decisions/0008-catalog-and-stats.md)) | Os totais não caem quando uma loja falha, e uma listing nova não aparece como "ganho" |
| 2026-10-26 | 6 | `POST /api/admin/collect` e Swagger (springdoc 3.1.1) | Recolher à mão; documentação da API gerada do código |
| 2026-10-26 | 6 | Um só PostgreSQL para todos os testes | 3 contentores passaram a 1; obrigou cada teste a limpar antes de começar |
| 2026-10-26 | 6 | 21 testes novos (80 no total); experimentado com os dados reais e a descoberta real do publisher PKief | Total combinado das 7 extensões, recolha manual 12/12, nomes repetidos distinguidos pela chave |
| 2026-10-26 | — | JaCoCo 0.8.15: o build falha abaixo de 85% das linhas, 70% dos ramos ou 80% das linhas de qualquer `*Service`; a CI mostra a cobertura e guarda o relatório | Medido antes: 93,8% das linhas e 78,2% dos ramos. Os limites ficam um pouco abaixo, para apanhar uma descida real |
| 2026-10-26 | 7 | Respostas reais das lojas pedidas antes de desenhar as avaliações | O Open VSX não tem IDs nem respostas; o Chrome mostra-as numa página sem IDs; o `filterOptions=1` do plano escondia as avaliações sem texto |
| 2026-10-26 | 7 | `fetchReviews` nas três lojas; tabela `reviews` (V4) com upsert por `(listing_id, external_id)`, `first_seen_at` e `read_at` ([0009](../decisions/0009-reviews.md)) | Uma caixa de entrada para todas as lojas; os alertas do passo 8 vão saber o que é novo |
| 2026-10-26 | 7 | A recolha lê as avaliações depois de gravar a fotografia | Se as avaliações falharem, os números do dia não se perdem |
| 2026-10-26 | 7 | `GET /api/reviews` (filtros, total, páginas) e `PATCH /api/reviews/{id}` | Ver o 1★ que interessa e marcar o que já foi visto |
| 2026-10-26 | 7 | Autores anonimizados nas respostas gravadas para os testes | São dados públicos, mas não precisam de ficar no repositório |
| 2026-10-26 | 7 | 17 testes novos (97 no total; 93,9% das linhas); recolha real trouxe o 1★ do Diff Checker com a tua resposta | — |
| 2026-10-27 | 8 | Regras de alerta (5 tipos) com email e Discord; deteção em SQL; *outbox* com chave de deduplicação; entrega com retries ([0010](../decisions/0010-alerts.md)) | Cada alerta sai uma vez; um envio falhado volta a ser tentado |
| 2026-10-27 | 8 | A recolha publica `CollectionFinished`; os alertas ouvem com `@EventListener` | A recolha não depende dos alertas (prepara o Kafka da v3) |
| 2026-10-27 | 8 | Webhooks do Discord só para `discord.com`, sem menções, com o token escondido | SSRF, `@everyone` num texto de avaliação, um URL secreto |
| 2026-10-27 | 8 | Mailpit no `compose.yaml`; GreenMail nos testes; email fora do `/actuator/health` | Ver os emails sem enviar nada; testar com SMTP real; um servidor de email em baixo não reinicia a app |
| 2026-10-27 | 8 | A consulta do Marketplace repete-se uma vez quando a ligação cai | Explicou um teste intermitente: o Java repete `GET`s sozinho, mas não `POST`s |
| 2026-10-27 | 8 | 21 testes novos (118 no total; 94,4% das linhas); alerta de teste chegou ao Mailpit com dados reais | — |

## Problemas que apareceram

| Problema | Causa | Solução | O que fica |
| --- | --- | --- | --- |
| O Initializr não deixa escolher o Spring Boot 3.x | Em outubro de 2026 só oferece o 4.0 e o 4.1 | Usar o 4.1 ([0001](../decisions/0001-java-25-and-spring-boot-4.md)) | Muitos tutoriais ainda são do Boot 3 e mostram nomes antigos |
| Nomes de tutoriais que não existem no Boot 4 | O Boot 4 dividiu starters e mudou pacotes de teste | `spring-boot-starter-webmvc` em vez de `starter-web`; `AutoConfigureMockMvc` está em `org.springframework.boot.webmvc.test.autoconfigure` | Quando um import de um tutorial não compila, procura o nome novo na documentação do Boot 4 |
| Os testes não corriam no Mac | Não havia Docker, e o Testcontainers precisa dele | Instalar o Docker Desktop | `docker info` tem de funcionar antes de `./mvnw verify` |
| O primeiro push foi recusado | O token do `gh` não tinha a permissão `workflow`, que o GitHub exige para enviar ficheiros de `.github/workflows/` | `gh auth refresh -s workflow` | O GitHub protege os workflows porque correm código com acesso ao repositório |
| Os 10 testes falharam todos de uma vez, até os que já passavam (passo 2) | O Docker Desktop estava fechado. O erro no relatório: `Previous attempts to find a Docker environment failed` | Abrir o Docker Desktop e correr de novo | Quando falham todos os testes ao mesmo tempo com `Failed to load ApplicationContext`, o problema costuma ser o ambiente, não o código. Ver o primeiro `Caused by` em `target/surefire-reports/` |
| Testes do Marketplace com `RST_STREAM: Stream cancelled` (passo 3) | Em `http://` o cliente do JDK tenta HTTP/2 por upgrade (h2c), e o Jetty do WireMock corta os pedidos com corpo | `http2PlainDisabled(true)` na configuração do WireMock | Um erro só nos testes pode vir do servidor falso e não do código. As lojas reais são `https` |
| O teste `live` do Chrome falhou com `Illegal character in path` (passo 3) | A loja redireciona para `/detail/director’s-cut-for-letter/{id}` com UTF-8 cru no cabeçalho `Location`; o JDK lê-o como ISO-8859-1 | O adaptador segue os redirecionamentos e codifica o URL (`%E2%80%99`) | As respostas gravadas não mostram tudo. Um teste contra o sistema real apanha o que elas escondem |
| O PR #2 entrou no branch do passo 2 e não no `main` (passo 3) | O PR #2 tinha base em `feat/core-schema`. O GitHub só muda a base para `main` se o branch do PR anterior for apagado no merge, o que não está ligado neste repositório | PR #3 do `feat/store-adapters` para o `main`. Daqui em diante, todos os PRs com base no `main` | Um PR "empilhado" sobre outro é frágil. Com base no `main`, mostra os commits do anterior até esse ter merge, e mais nada |
| `--extpulse.collection.cron=0 * * * * *` chegou à app como `"0"` (passo 4) | O `-Dspring-boot.run.arguments` do plugin do Maven parte os argumentos nos espaços | Passar por variável de ambiente: `EXTPULSE_COLLECTION_CRON='0 * * * * *'` | O Spring Boot lê variáveis de ambiente (`EXTPULSE_COLLECTION_CRON` = `extpulse.collection.cron`), e é assim que a produção recebe configuração |
| `SchemaTests` falhou 8 vezes com `duplicate key users_email_lower_key` (passo 6) | Com um só PostgreSQL para todos os testes, encontrou contas gravadas por outras classes de teste | Cada teste apaga o que precisa antes de começar | Um teste não pode assumir uma base de dados vazia. Contentores separados escondiam isso |
| `/actuator/health` a 503 nos testes depois de juntar o Spring Mail (passo 8) | O Boot junta sozinho o servidor de email ao health | `management.health.mail.enabled: false` | Uma dependência nova pode mudar o health. E um serviço que não é essencial não deve marcar a app como doente |
| Um teste do catálogo falhou uma vez em cerca de 10 (passo 8) | O Java reutiliza ligações; se o servidor fechou uma, repete um `GET` sozinho, nunca um `POST`. O Marketplace usa `POST` | Uma repetição na consulta do Marketplace, com um teste que corta a ligação de propósito | Um teste intermitente é um bug à espera de ser percebido. O log (agora com a causa) e a diferença `GET`/`POST` explicaram-no |
| Testes de email a falhar depois de reiniciar o GreenMail (passo 8) | Com porta dinâmica, o servidor voltava noutra porta | Uma porta livre escolhida uma vez | — |
| O Mailpit não arrancou com a app (passo 8) | Com algum serviço do `compose.yaml` já a correr, o Spring Boot não arranca os outros | `docker compose up -d` | — |
| O `filterOptions=1` da API de avaliações do Marketplace, previsto no plano, esconde as avaliações sem texto (passo 7) | É o filtro "sem avaliações vazias" da loja | Usar o filtro por omissão; um teste confirma que o parâmetro não vai | Ler a documentação (ou testar) cada parâmetro copiado. Um 1★ sem texto era o que mais importava |
| Um redirecionamento a mais a partir de Portugal (passo 3) | A Google manda primeiro para o consentimento de cookies (`consent.google.com`) | O mesmo ciclo de redirecionamentos, até 5 saltos | O que um servidor responde pode depender do país de onde se pede |

## As ferramentas em resumo

| Ferramenta | Versão | Para que serve | Desde |
| --- | --- | --- | --- |
| Java (OpenJDK) | 25 | A linguagem | passo 1 |
| Spring Boot | 4.1.1 | O framework: arranque, configuração, web, base de dados | passo 1 |
| Spring Web MVC + Tomcat | 7.0.9 / 11.0 | Receber pedidos HTTP (a API) | passo 1 |
| Spring Data JPA + Hibernate | 7.4 | Ler e gravar na base de dados com classes Java | passo 1 (usado a partir do passo 3) |
| HikariCP | 7.0 | Manter um conjunto de ligações abertas à base de dados | passo 1 |
| Bean Validation (Hibernate Validator) | 9.1 | Validar dados de entrada | passo 1 (usado a partir do passo 5) |
| Spring Boot Actuator | 4.1.1 | Diagnóstico: `/actuator/health` | passo 1 |
| Maven + Maven Wrapper | 3.10.0 | Compilar, descarregar dependências, correr testes, criar o jar | passo 1 |
| PostgreSQL | 17 | A base de dados | passo 1 |
| Flyway | 12.4 | Criar e alterar tabelas com ficheiros SQL versionados | passo 1 (primeira migração no passo 2) |
| Docker + Docker Compose | — | Correr o PostgreSQL sem o instalar | passo 1 |
| Testcontainers | 2.0.5 | PostgreSQL verdadeiro e descartável nos testes | passo 1 |
| JUnit, AssertJ, Mockito, MockMvc | 6.0 / 3.27 / 5.23 / — | Escrever e correr os testes | passo 1 |
| GitHub Actions | — | CI: testes em cada push; testes contra as lojas reais todas as semanas | passo 1 (semanal: passo 3) |
| Spring RestClient + HttpClient do JDK | 7.0.9 / 25 | Pedidos HTTP às lojas | passo 3 |
| `@ConfigurationProperties` | 4.1.1 | Configuração tipada (`extpulse.stores`) | passo 3 |
| jsoup | 1.23.2 | Ler o HTML da página da Chrome Web Store | passo 3 |
| WireMock | 3.13.2 | Servidor HTTP falso no lugar das lojas, nos testes | passo 3 |
| Tags do JUnit (`@Tag`) | 6.0 | Separar os testes contra as lojas reais | passo 3 |
| Resilience4j (retry, ratelimiter) | 2.4.0 | Retries com espera crescente e ritmo por loja | passo 4 |
| `@Scheduled` e `Clock` | Spring 7.0 / Java 25 | Agendar a recolha diária; "hoje" em Lisboa | passo 4 |
| Spring Security (resource server) | 7.0 | Regras de acesso; verificar o JWT em cada pedido | passo 5 |
| JWT com Nimbus JOSE | — | Assinar e ler os tokens de login (HS256) | passo 5 |
| BCrypt (`DelegatingPasswordEncoder`) | — | Guardar passwords como hash | passo 5 |
| Problem Details (RFC 9457) | Spring 7.0 | Formato único das respostas de erro | passo 5 |
| springdoc-openapi (Swagger) | 3.1.1 | Documentação da API gerada dos controllers | passo 6 |
| JaCoCo | 0.8.15 | Cobertura de testes, com limite mínimo no build | depois do passo 6 |
| Spring Mail, Mailpit, GreenMail | 4.1.1 / 1.31.5 / 2.1.14 | Enviar emails; vê-los em desenvolvimento; testá-los | passo 8 |
| Webhooks do Discord | — | Alertas num canal do Discord | passo 8 |
| Eventos do Spring (`@EventListener`) | 7.0 | A recolha avisa quem quiser ouvir que acabou | passo 8 |
| Padrão *outbox* | — | Alertas escritos antes de enviados; uma vez cada um | passo 8 |
| Funções de janela do PostgreSQL (`LAG`, `FILTER`, `LATERAL`) | 17 | Estatísticas calculadas na base de dados | passo 6 |
| Git, GitHub e `gh` | — | Histórico do código e repositório remoto | passo 1 |
| ADRs | — | Registar as decisões | passo 1 |

As versões das bibliotecas vêm do Spring Boot 4.1.1. Para as ver: `./mvnw dependency:tree`.

## Ficha de cada ferramenta

### Java 25

- **O que é:** a linguagem e a máquina virtual (JVM) que corre o código.
- **Porquê:** é a LTS (versão com suporte longo) mais recente e já estava instalada.
  As virtual threads vão dar jeito quando a recolha fizer muitos pedidos às lojas
  ao mesmo tempo.
- **Alternativa que ficou de fora:** Java 21 ou 17, que muitas empresas ainda usam.
  O código deste projeto escreve-se quase igual nessas versões.
- **Onde está:** `<java.version>25</java.version>` no `pom.xml`; na CI, `java-version: '25'`.
- **Em que já ajudou:** `record`, `var` e text blocks vão poupar código a partir do
  passo 3. Para já, nada de específico.

### Spring Boot 4.1

- **O que é:** um framework que monta a aplicação sozinho a partir das dependências
  que tens. Vê o driver do PostgreSQL e o JPA e configura a ligação, vê o Web MVC e
  arranca um servidor Tomcat, e por aí fora.
- **Porquê:** é o padrão no back end Java, em bancos, consultoras e empresas de produto.
- **Alternativa que ficou de fora:** Quarkus e Micronaut (mais leves, mas muito menos
  pedidos em vagas); Spring Boot 3.5 (não oferecido para projetos novos).
- **Onde está:** `<parent>` no `pom.xml`, `@SpringBootApplication` em `ExtpulseApplication.java`,
  configuração em `application.yaml`.
- **Em que já ajudou:** a app liga-se ao PostgreSQL sem uma linha de configuração da
  ligação, e o `pom.xml` não tem uma única versão escrita à mão nas dependências do Spring.

### Spring Web MVC (com Tomcat)

- **O que é:** a parte que recebe pedidos HTTP e os entrega aos teus `@RestController`.
  O Tomcat é o servidor web, que vem dentro do jar.
- **Porquê:** a API REST (passos 5 e 6) e a página pública do passo 9.
- **Alternativa que ficou de fora:** Spring WebFlux (reativo). Mais difícil de ler e de
  testar, e o Web MVC com virtual threads chega para este projeto.
- **Onde está:** `spring-boot-starter-webmvc` no `pom.xml`. A porta é a 8080 (o valor por omissão).
- **Em que já ajudou:** serve o `/actuator/health`.

### Spring Data JPA, Hibernate e HikariCP

- **O que é:** o JPA mapeia classes Java (entidades) para tabelas. O Hibernate é a
  implementação. O Spring Data gera os repositórios a partir de uma interface. O
  HikariCP mantém um conjunto de ligações abertas para não abrir uma por pedido.
- **Porquê:** é a forma mais comum de aceder a bases de dados em Spring. Consultas
  difíceis, como a do `LAG` no passo 6, escrevem-se em SQL.
- **Alternativa que ficou de fora:** só JDBC (`JdbcClient`) ou jOOQ. O `JdbcClient`
  (que vem com o mesmo starter) é usado nos `SchemaTests` do passo 2, porque o JPA ainda
  não tem entidades.
- **Onde está:** `spring-boot-starter-data-jpa`; `ddl-auto: validate` e `open-in-view: false`
  no `application.yaml`.
- **Em que já ajudou:** no passo 4 entraram as primeiras entidades (`Listing`,
  `CollectionRun` com uma `@ElementCollection` de erros). Os repositórios não têm uma
  linha de código, e o `ddl-auto: validate` confirmou ao arrancar que as entidades batem
  com as tabelas. O upsert das fotografias ficou em SQL (`JdbcClient`), porque o JPA não
  faz "insere ou atualiza".

### Spring Boot Actuator

- **O que é:** endpoints de diagnóstico prontos a usar.
- **Porquê:** o `/actuator/health` diz se a app está viva e se chega à base de dados. O
  Render, o Fly.io e a AWS usam-no para saber se devem reiniciar a app.
- **Onde está:** `spring-boot-starter-actuator`; `management.endpoints.web.exposure.include`
  no `application.yaml`.
- **Em que já ajudou:** é a base do teste `HealthEndpointTests`, que prova o setup inteiro.

### Bean Validation

- **O que é:** anotações como `@NotBlank` e `@Email` que validam os dados que chegam à API.
- **Porquê:** rejeitar dados errados à entrada, com um erro claro, antes de chegarem à base de dados.
- **Onde está:** `spring-boot-starter-validation`.
- **Em que já ajudou:** no passo 5, `@Email`, `@NotBlank` e `@Size` nos pedidos de registo
  e login. Um pedido inválido nem chega ao serviço: volta 400 com a lista dos campos errados.

### Maven e o Maven Wrapper

- **O que é:** o Maven lê o `pom.xml`, descarrega as dependências, compila, corre os
  testes e cria o jar. O Wrapper (`./mvnw`) descarrega a versão certa do Maven, por isso
  não precisas de o instalar.
- **Porquê:** é mais comum em empresas portuguesas do que o Gradle.
- **Alternativa que ficou de fora:** Gradle (mais rápido, mais flexível, menos comum).
- **Onde está:** `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`.
- **Em que já ajudou:** a CI usa o mesmo Maven 3.10.0 que o teu Mac, sem instalar nada.

### PostgreSQL 17

- **O que é:** a base de dados relacional.
- **Porquê:** os dados têm relações (utilizador → publishers → extensões → listings →
  fotografias), a regra mais importante ("uma fotografia por dia") é uma restrição
  única, e os ganhos diários calculam-se com funções de janela.
- **Alternativa que ficou de fora:** MySQL (também serviria), MongoDB (os dados têm relações)
  e H2 (não é o PostgreSQL). Ver [0003](../decisions/0003-postgresql-and-flyway.md).
- **Onde está:** `compose.yaml` (desenvolvimento) e `TestcontainersConfiguration` (testes),
  os dois com `postgres:17`.
- **Em que já ajudou:** desenvolvimento, testes e CI usam a mesma versão.

### Flyway

- **O que é:** corre ficheiros SQL numerados (`V1__...sql`, `V2__...sql`) por ordem e
  regista quais já correram na tabela `flyway_schema_history`.
- **Porquê:** cada mudança ao esquema fica num ficheiro revisto no Git e é aplicada da
  mesma forma em todo o lado.
- **Alternativa que ficou de fora:** Liquibase (mesma ideia, com XML ou YAML) e
  `ddl-auto: update` (o Hibernate adivinha as mudanças e não deixa histórico).
- **Onde está:** `spring-boot-starter-flyway` e `flyway-database-postgresql` no `pom.xml`;
  as migrações em `src/main/resources/db/migration/`.
- **Em que já ajudou:** a mesma `V1` criou as tabelas nos testes (Testcontainers) e na
  base de dados de desenvolvimento, sem nenhum passo à mão. A tabela `flyway_schema_history`
  mostra `1 | create core tables | t`.

### Docker e Docker Compose

- **O que é:** o Docker corre programas em contentores isolados. O Compose descreve num
  ficheiro os contentores que a app precisa. O `spring-boot-docker-compose` arranca-os
  quando a app arranca.
- **Porquê:** o PostgreSQL corre igual em qualquer máquina, sem instalação, e apaga-se com um comando.
- **Alternativa que ficou de fora:** instalar o PostgreSQL no Mac (cada máquina fica diferente).
- **Onde está:** `compose.yaml`; `spring-boot-docker-compose` no `pom.xml`.
- **Em que já ajudou:** o `application.yaml` não tem URL, utilizador nem password da base
  de dados. O Spring Boot lê-os do `compose.yaml`.

### Testcontainers

- **O que é:** uma biblioteca que arranca contentores Docker durante os testes e os apaga no fim.
- **Porquê:** os testes correm contra um PostgreSQL verdadeiro. Coisas como `ON CONFLICT`,
  `LAG` e `timestamptz` não se comportam igual no H2.
- **Onde está:** `TestcontainersConfiguration.java`; `@ServiceConnection` liga o contentor à app.
- **Em que já ajudou:** o `HealthEndpointTests` passa no Mac e no GitHub Actions sem
  nenhuma configuração de base de dados. No passo 2, os `SchemaTests` testaram a
  migração tal como corre em produção, incluindo o índice único sobre `lower(email)`,
  um índice sobre uma expressão que nem todas as bases de dados suportam.

### JUnit 6, AssertJ, Mockito e MockMvc

- **O que é:** o JUnit corre os testes (`@Test`). O AssertJ escreve as verificações de forma
  legível (`assertThat(x).isEqualTo(2)`). O Mockito cria objetos falsos para testar uma
  classe sozinha. O MockMvc faz pedidos HTTP à app sem abrir uma porta.
- **Porquê:** vêm com o `spring-boot-starter-*-test` e são o padrão em Java.
- **Onde está:** os starters `-test` no `pom.xml`; os testes em `src/test/java/`.
- **Em que já ajudou:** o `HealthEndpointTests` usa MockMvc. Nota: o Spring Boot 4 traz o
  **JUnit 6**. A escrita dos testes é igual à do JUnit 5 que aparece nos tutoriais.

### GitHub Actions

- **O que é:** CI (integração contínua): o GitHub corre comandos num servidor dele em cada push.
- **Porquê:** nenhum código entra no `main` sem compilar e passar nos testes, e no GitHub
  vê-se um visto verde em cada commit.
- **Alternativa que ficou de fora:** Jenkins e GitLab CI (comuns em empresas, mas o
  código está no GitHub).
- **Onde está:** `.github/workflows/ci.yml`.
- **Em que já ajudou:** verde no primeiro push, o que prova que o projeto não depende do teu Mac.
  No passo 3 juntou-se um segundo workflow, `live-stores.yml`, agendado (`schedule` com
  `cron`) para as segundas: corre os testes contra as lojas reais.

### Spring RestClient (com o HttpClient do JDK)

- **O que é:** o cliente HTTP do Spring, com uma API fluente
  (`http.get().uri(...).retrieve().body(Tipo.class)`). Converte o JSON da resposta
  diretamente num `record`. Por baixo usa o `HttpClient` que vem com o Java.
- **Porquê:** é o cliente recomendado pelo Spring para código síncrono, e não precisa de
  biblioteca extra.
- **Alternativa que ficou de fora:** `RestTemplate` (o antigo, em manutenção), `WebClient`
  (reativo, para quem usa WebFlux), OkHttp ou Apache HttpClient (mais uma dependência).
- **Onde está:** `StoreHttp.java` (timeouts e User-Agent) e nos três adaptadores.
- **Em que já ajudou:** as respostas do Marketplace e do Open VSX passam a `record`s numa
  linha. O `exchange(...)` deixou o adaptador do Chrome tratar os redirecionamentos à mão.

### `@ConfigurationProperties`

- **O que é:** liga um bloco do `application.yaml` a um `record` Java, com tipos
  (`Duration`, `URI`) e validação ao arrancar.
- **Porquê:** nada de URLs nem timeouts escritos no código, e um só objeto em vez de
  vários `@Value`.
- **Onde está:** `StoreProperties.java`, `StoreConfiguration.java`, bloco `extpulse.stores`.
- **Em que já ajudou:** os testes trocam os três URLs pelo do WireMock só com um `new StoreProperties(...)`.

### jsoup

- **O que é:** uma biblioteca para ler HTML como uma árvore (`page.selectFirst("h1")`,
  `page.text()`).
- **Porquê:** a Chrome Web Store não tem API de estatísticas; os números estão no HTML.
- **Alternativa que ficou de fora:** um browser automático (Playwright, Selenium), pesado
  demais para três números que já vêm no HTML; expressões regulares sobre o HTML cru
  (frágeis com tags pelo meio).
- **Onde está:** `ChromeWebStoreSource.java`; versão em `<jsoup.version>` no `pom.xml`
  (não é gerida pelo Spring Boot, por isso leva versão).
- **Em que já ajudou:** o `text()` junta o texto visível sem os atributos, por isso a
  média de outro item escondida num `aria-label` não engana o adaptador.

### WireMock

- **O que é:** um servidor HTTP falso para testes. Diz-se "quando pedirem isto, responde
  aquilo" (incluindo erros, atrasos e redirecionamentos) e verifica-se o que foi pedido.
- **Porquê:** testar os adaptadores sem internet, com respostas sempre iguais, e provocar
  falhas que a loja real não dá quando queremos.
- **Alternativa que ficou de fora:** `MockRestServiceServer` do Spring (não passa pela
  rede, por isso não testa timeouts nem o cliente HTTP real); MockServer.
- **Onde está:** `StoreTestSupport.java` e os `*SourceTests`; respostas gravadas em
  `src/test/resources/wiremock/__files/`. Usa-se o jar `standalone`, que traz o seu
  próprio Jetty e Jackson e não choca com os da app.
- **Em que já ajudou:** 25 testes em menos de 3 segundos, incluindo timeout, erro 500 e
  página mudada.

### Tags do JUnit e `excludedGroups`

- **O que é:** `@Tag("live")` marca testes; o Maven Surefire escolhe quais corre com
  `groups` e `excludedGroups`.
- **Porquê:** os testes contra as lojas reais dependem da internet e das lojas. Não podem
  partir o build normal, mas têm de correr de vez em quando.
- **Onde está:** `LiveStoreTests.java`; `<excludedGroups>live</excludedGroups>` no `pom.xml`;
  `-Dgroups=live -DexcludedGroups=` no `live-stores.yml`.
- **Em que já ajudou:** o primeiro teste `live` encontrou o bug do redirecionamento do
  Chrome, que as respostas gravadas não mostravam.

### Resilience4j

- **O que é:** uma biblioteca de tolerância a falhas: retry, rate limiter, circuit
  breaker, bulkhead. Cada um envolve uma chamada (`Retry.decorateSupplier(...)`).
- **Porquê:** as lojas falham de vez em quando, e pedir depressa demais pode levar a um
  bloqueio. É a biblioteca que mais aparece em vagas de Java para isto.
- **Alternativa que ficou de fora:** o starter com anotações `@Retry` (funciona por
  proxies, e uma chamada de dentro da mesma classe passa ao lado sem aviso); o
  `@Retryable` novo do Spring Framework 7 (não tem rate limiter); escrever o ciclo de
  retries à mão.
- **Onde está:** `ResilientStoreCalls.java`; os valores em `extpulse.collection` no
  `application.yaml`; só os módulos `resilience4j-retry` e `resilience4j-ratelimiter`.
- **Em que já ajudou:** na recolha real, as 12 listings demoraram 6 segundos, sem
  nenhuma loja a receber mais de um pedido por segundo. Os testes provam que os retries
  esperam cada vez mais e que uma listing que não existe não é repetida.

### `@Scheduled` e `Clock`

- **O que é:** o `@Scheduled(cron = ...)` do Spring corre um método a horas marcadas. O
  `Clock` do Java diz que horas são, e pode ser trocado por um relógio fixo nos testes.
- **Porquê:** a recolha corre sozinha todos os dias, e "hoje" tem de ser o dia em Lisboa.
- **Alternativa que ficou de fora:** Quartz (muito mais completo, com tabelas próprias; a
  mais para uma tarefa por dia); um cron do sistema operativo a chamar um endpoint (fica
  fora da app e dos testes).
- **Onde está:** `DailyCollectionJob.java` (o cron e o fuso vêm do `application.yaml`),
  `CollectionConfiguration.java` (`@EnableScheduling`) e `TimeConfiguration.java` (o `Clock`).
- **Em que já ajudou:** os testes provam que o agendamento corre às 05:00 UTC no verão e
  às 06:00 UTC no inverno, e que uma recolha às 23:30 UTC de 15 de julho fica com o dia 16.
  Para experimentar, `EXTPULSE_COLLECTION_CRON='0 * * * * *'` pôs a recolha a correr a cada minuto.

### Spring Security (como resource server)

- **O que é:** o framework de segurança do Spring. Uma cadeia de filtros corre antes
  dos controllers e decide quem pode fazer o quê. Em modo "resource server", lê o
  `Authorization: Bearer <token>`, verifica o JWT e põe o utilizador no contexto.
- **Porquê:** é o standard em Spring, e a configuração de resource server é a mesma que
  se usa com o Keycloak, o Auth0 ou o AWS Cognito.
- **Alternativa que ficou de fora:** sessões com cookie (precisam de CSRF e de sessões
  partilhadas entre servidores); um fornecedor de identidade externo (um segundo
  sistema para manter, para uma só app).
- **Onde está:** `SecurityConfiguration.java` (regras, `PasswordEncoder`, `JwtEncoder`,
  `JwtDecoder`); `spring-boot-starter-security-oauth2-resource-server` no `pom.xml`.
- **Em que já ajudou:** `/api/me` sem token dá 401; um token alterado, expirado ou de
  outra chave dá 401; `/api/admin/**` com um token de `USER` dá 403. Tudo testado, e tudo
  sem uma linha de código nos controllers.

### JWT (com o Nimbus JOSE)

- **O que é:** um token assinado com três partes em base64 (`cabeçalho.dados.assinatura`).
  Os dados leem-se, mas não se alteram sem a chave. O Nimbus é a biblioteca que o
  Spring Security usa para os criar e verificar.
- **Porquê:** o login fica sem estado no servidor, e qualquer instância verifica
  qualquer token.
- **Alternativa que ficou de fora:** RS256 com um par de chaves (precisa-se quando outros
  serviços verificam tokens sem os poderem emitir); tokens opacos guardados na base de dados.
- **Onde está:** `TokenService.java` (emite), `SecurityConfiguration.java` (verifica com
  assinatura, validade e emissor); a chave em `EXTPULSE_AUTH_JWT_SECRET`.
- **Em que já ajudou:** os testes criam tokens expirados ou de administrador com o mesmo
  `TokenService` e um `Clock` fixo, sem truques.

### BCrypt e o `DelegatingPasswordEncoder`

- **O que é:** o BCrypt é uma função de hash para passwords, lenta de propósito e com
  "sal" aleatório. O `DelegatingPasswordEncoder` guarda o algoritmo como prefixo
  (`{bcrypt}$2a$10$...`).
- **Porquê:** uma base de dados roubada não deve revelar passwords. Um hash rápido
  (SHA-256) deixava testar milhares de milhões de passwords por segundo.
- **Alternativa que ficou de fora:** Argon2 (mais recente e mais forte, mas o BCrypt é o
  que vem por omissão no Spring e chega aqui). Graças ao prefixo, mudar depois não
  invalida as passwords antigas.
- **Onde está:** `SecurityConfiguration.passwordEncoder()` e `AccountService`.
- **Em que já ajudou:** um teste confirma que a base de dados tem `{bcrypt}$2...` e
  nunca a password. Outro confirma que uma password de mais de 72 bytes é recusada,
  em vez de ser cortada sem aviso.

### Problem Details (RFC 9457)

- **O que é:** um formato standard para erros de APIs HTTP: `type`, `title`, `status`,
  `detail`, `instance`, mais campos próprios, com `Content-Type: application/problem+json`.
- **Porquê:** um cliente trata todos os erros da mesma forma, e não há um formato
  inventado por nós.
- **Onde está:** `ApiExceptionHandler.java` (junta `errors` com os campos inválidos); as
  exceções do `AccountService` estendem `ErrorResponseException`.
- **Em que já ajudou:** os erros de registo (400 com os campos, 409) e de login (401)
  saem no mesmo formato sem código de tratamento em cada controller.

### springdoc-openapi (Swagger)

- **O que é:** lê os controllers e gera a descrição da API em OpenAPI 3 (`/v3/api-docs`)
  e uma página para a experimentar (Swagger UI, em `/swagger-ui.html`).
- **Porquê:** a documentação da API nunca fica desatualizada, porque vem do código. É o
  que as equipas Spring usam.
- **Alternativa que ficou de fora:** escrever o ficheiro OpenAPI à mão (fica sempre
  atrás do código); Spring REST Docs (documentação a partir dos testes, mais trabalho).
- **Onde está:** `springdoc-openapi-starter-webmvc-ui` no `pom.xml` (versão em
  `<springdoc.version>`), `OpenApiConfiguration.java` (título e esquema Bearer), e as
  regras públicas em `SecurityConfiguration`.
- **Em que já ajudou:** os 8 endpoints aparecem sozinhos, e o botão "Authorize" aceita o
  token do login. Um teste confirma que a documentação abre sem login.

### Funções de janela do PostgreSQL (`LAG`, `FILTER`, `LATERAL`)

- **O que é:** o `LAG(x) OVER (PARTITION BY ... ORDER BY ...)` lê o valor da linha
  anterior dentro de um grupo. O `FILTER (WHERE ...)` filtra o que entra numa soma. O
  `JOIN LATERAL` corre uma subconsulta por linha, que pode usar os valores dessa linha.
- **Porquê:** os ganhos diários e "o último número conhecido" calculam-se numa só
  consulta, onde estão os dados. São perguntas clássicas de entrevista de SQL.
- **Alternativa que ficou de fora:** carregar as fotografias para Java e calcular lá
  (mais código e mais dados a viajar); tabelas de resumo (mais uma coisa a manter certa).
- **Onde está:** `stats/StatsQueries.java`.
- **Em que já ajudou:** com o `LAG` pelo total em vez de por listing, o teste falha com
  um ganho de 495 que não existiu. É a prova de que o `PARTITION BY` é necessário.

### JaCoCo

- **O que é:** mede que linhas e ramos (cada lado de um `if`) os testes correm. Um
  agente Java regista isso enquanto os testes correm; depois escreve um relatório e
  pode falhar o build abaixo de um limite.
- **Porquê:** uma barreira na CI impede que código novo entre sem testes. Cobertura
  alta não prova que os testes são bons (por isso este projeto também parte o código de
  propósito para ver os testes falhar), mas cobertura baixa prova que falta testar.
- **Alternativa que ficou de fora:** SonarCloud (análise de qualidade mais completa, que
  também lê o relatório do JaCoCo; fica para quando o repositório for público); só
  o relatório, sem limite (ninguém o lê).
- **Onde está:** `jacoco-maven-plugin` no `pom.xml`, com três execuções:
  `prepare-agent` (antes dos testes), `report` e `check` (na fase `verify`). Os limites
  são 85% das linhas e 70% dos ramos no projeto, e 80% das linhas em cada classe
  `*Service`. Na CI, o passo `Coverage summary` mostra os números na página do run e o
  `Upload coverage report` guarda o HTML. Em local: `target/site/jacoco/index.html`.
- **Em que já ajudou:** a primeira medição mostrou 93,8% das linhas e 78,2% dos ramos.
  A classe com menos é o `ExtpulseApplication` (o `main` não corre nos testes), e o
  serviço com menos é o `CatalogService` (85%). Com o limite dos serviços a 99%, o
  build falha com `Coverage checks have not been met`.

### Spring Mail, Mailpit e GreenMail

- **O que é:** o Spring Mail envia emails por SMTP (`JavaMailSender`). O Mailpit é uma
  caixa de email falsa para desenvolvimento, com página web. O GreenMail é um servidor
  SMTP que corre dentro dos testes.
- **Porquê:** alertas e resumo semanal por email. Em desenvolvimento nada sai do
  computador, e nos testes verifica-se o email que chegou de facto.
- **Alternativa que ficou de fora:** um serviço com API (SendGrid, Mailgun) em vez de
  SMTP (prende a um fornecedor); um `JavaMailSender` falso nos testes (não testa o envio real).
- **Onde está:** `spring-boot-starter-mail`, `EmailAlertSender.java`, `spring.mail` no
  `application.yaml`, o serviço `mailpit` no `compose.yaml`, e o GreenMail em
  `AlertApiAndDeliveryTests`.
- **Em que já ajudou:** o alerta de teste chegou ao Mailpit com os dados reais. Nos
  testes, um servidor de email parado e depois ligado mostrou que o alerta sai na
  tentativa seguinte.

### Webhooks do Discord

- **O que é:** um URL secreto de um canal. Um `POST` com `{"content": "..."}` escreve
  uma mensagem no canal.
- **Porquê:** muitos developers vivem no Discord, e é a forma mais simples de integrar
  (sem bot e sem OAuth).
- **Alternativa que ficou de fora:** um bot do Discord (mais poder, mais trabalho);
  Slack (a mesma ideia, fica para depois).
- **Onde está:** `DiscordAlertSender.java`; os prefixos permitidos em
  `extpulse.alerts.discord-webhook-prefixes`.
- **Em que já ajudou:** os testes provam que só aceita URLs do Discord (contra SSRF), que
  manda `allowed_mentions: {parse: []}` (um `@everyone` numa avaliação não menciona
  ninguém) e que o token não aparece na API.

### Eventos do Spring e o padrão *outbox*

- **O que é:** o `ApplicationEventPublisher.publishEvent(...)` avisa quem tiver um
  `@EventListener` para esse tipo. O *outbox* é uma tabela onde se escreve o que é
  preciso enviar, antes de o enviar.
- **Porquê:** a recolha não precisa de conhecer os alertas. Um alerta nunca se perde
  numa falha de envio, e nunca sai duas vezes, graças à chave única.
- **Alternativa que ficou de fora:** Kafka ou RabbitMQ (para comunicar entre serviços;
  dentro de uma app, o evento e a tabela chegam). O plano da v3 passa estes eventos para o Kafka.
- **Onde está:** `CollectionFinished.java`, `AlertJobs.afterCollection`, `AlertOutbox.java`
  e a tabela `alert_events`.
- **Em que já ajudou:** o teste publica `CollectionFinished` duas vezes e o Discord
  recebe **um** pedido.

### Git, GitHub e `gh`

- **O que é:** o Git guarda o histórico. O GitHub guarda o repositório remoto. O `gh` é a
  linha de comandos do GitHub.
- **Onde está:** `.gitignore` (o que não entra no Git) e `.gitattributes` (fins de linha).
- **Em que já ajudou:** o `.gitignore` já deixa de fora `target/` e `.env`, por isso nem os
  ficheiros compilados nem os segredos vão parar ao GitHub por engano.

### ADRs (Architecture Decision Records)

- **O que é:** um ficheiro curto por decisão, com o contexto, a decisão, o custo e as alternativas.
- **Porquê:** numa entrevista, "porque escolheste X?" tem resposta escrita. Nas empresas
  usa-se para que quem chega perceba porque o código é assim.
- **Onde está:** `docs/decisions/`.
- **Em que já ajudou:** a escolha do Boot 4 em vez do 3 está justificada em [0001](../decisions/0001-java-25-and-spring-boot-4.md).

## O que vem a seguir

Do documento do projeto, por passo. Cada ferramenta ganha a sua ficha quando entrar.

| Ferramenta | Para quê | Passo |
| --- | --- | --- |
| ShedLock | Só uma instância a recolher, quando houver mais do que um servidor | deploy |
| Render ou Fly.io | Deploy da v1 | 10 |
| AWS (ECR, ECS Fargate, RDS, SES) | Deploy da v2 | v2 |
| Kafka, Redis, Prometheus + Grafana | v3 | v3 |

Propostas ainda por decidir (fora do documento do projeto): GitHub Issues e Projects
com um branch e um pull request por passo, Spotless, Spring Modulith, SonarCloud,
Dependabot, CodeQL e Terraform para a AWS.
