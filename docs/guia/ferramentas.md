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
- **Porquê:** a API REST do passo 5 e a página pública do passo 9.
- **Alternativa que ficou de fora:** Spring WebFlux (reativo). Mais difícil de ler e de
  testar, e o Web MVC com virtual threads chega para este projeto.
- **Onde está:** `spring-boot-starter-webmvc` no `pom.xml`. A porta é a 8080 (o valor por omissão).
- **Em que já ajudou:** serve o `/actuator/health`.

### Spring Data JPA, Hibernate e HikariCP

- **O que é:** o JPA mapeia classes Java (entidades) para tabelas. O Hibernate é a
  implementação. O Spring Data gera os repositórios a partir de uma interface. O
  HikariCP mantém um conjunto de ligações abertas para não abrir uma por pedido.
- **Porquê:** é a forma mais comum de aceder a bases de dados em Spring. Consultas
  difíceis, como a do `LAG` no passo 5, escrevem-se em SQL.
- **Alternativa que ficou de fora:** só JDBC (`JdbcClient`) ou jOOQ. O `JdbcClient`
  (que vem com o mesmo starter) é usado nos `SchemaTests` do passo 2, porque o JPA ainda
  não tem entidades.
- **Onde está:** `spring-boot-starter-data-jpa`; `ddl-auto: validate` e `open-in-view: false`
  no `application.yaml`.
- **Em que já ajudou:** ainda não há entidades (entram no passo 3).

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
- **Em que já ajudou:** ainda não (entra com a API, no passo 5).

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
| Resilience4j | Retries com espera crescente e 1 pedido por segundo a cada loja | 4 |
| `@Scheduled` (+ ShedLock mais tarde) | A recolha diária | 4 |
| springdoc-openapi | Swagger gerado a partir do código | 5 |
| JaCoCo | Cobertura de testes na CI | 5 |
| Spring Security + JWT | Contas e login | 7 |
| Spring Mail | Alertas e resumo semanal por email | 8 |
| Render ou Fly.io | Deploy da v1 | 10 |
| AWS (ECR, ECS Fargate, RDS, SES) | Deploy da v2 | v2 |
| Kafka, Redis, Prometheus + Grafana | v3 | v3 |

Propostas ainda por decidir (fora do documento do projeto): GitHub Issues e Projects
com um branch e um pull request por passo, Spotless, Spring Modulith, SonarCloud,
Dependabot, CodeQL e Terraform para a AWS.
