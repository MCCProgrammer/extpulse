# 1. Setup do projeto

**Resultado:** uma aplicação Spring Boot que arranca, liga-se a um PostgreSQL em
Docker e responde `{"status":"UP"}` em `/actuator/health`, com um teste que prova
isso e uma CI que o corre em cada push. Ainda não há código do domínio.

## 1.1 Como o projeto foi criado

Com o [Spring Initializr](https://start.spring.io), a ferramenta oficial que gera
a estrutura de um projeto Spring Boot. As escolhas:

| Campo | Valor | Porquê |
| --- | --- | --- |
| Project | Maven | O mais usado em empresas (decisão [0001](../decisions/0001-java-25-and-spring-boot-4.md)) |
| Spring Boot | 4.1.1 | A versão estável atual; o 3.x já não é oferecido |
| Java | 25 | A LTS mais recente, já instalada |
| Group / Artifact | `com.mccprogrammer` / `extpulse` | O group é o teu "domínio ao contrário"; o artifact é o nome do projeto |
| Package | `com.mccprogrammer.extpulse` | Todo o código fica dentro deste pacote |

**Dependências escolhidas, e só estas:**

| Dependência | Para quê |
| --- | --- |
| Spring Web (MVC) | Criar endpoints HTTP (a API REST) |
| Spring Data JPA | Ler e gravar na base de dados com classes Java (entidades e repositórios) |
| PostgreSQL Driver | O "tradutor" entre o Java e o PostgreSQL |
| Flyway | Criar e alterar as tabelas com ficheiros SQL versionados |
| Validation | Validar dados de entrada (`@NotBlank`, `@Email`...) |
| Actuator | Endpoints de diagnóstico, como `/actuator/health` |
| Testcontainers | Arrancar um PostgreSQL verdadeiro durante os testes |
| Docker Compose Support | Arrancar o PostgreSQL de desenvolvimento quando a app arranca |

Spring Security, Mail e o resto entram quando forem precisos. **Regra:** só se
junta uma dependência quando há código que a usa. Cada dependência a mais é algo
que tens de saber explicar.

## 1.2 Os ficheiros, um a um

### `pom.xml`

O ficheiro do Maven: diz que versão do Spring Boot e do Java usar, e que
dependências descarregar.

- **`<parent>spring-boot-starter-parent`:** herda as versões testadas em conjunto
  de todas as bibliotecas do Spring. Por isso as dependências não têm `<version>`.
- **Os "starters"** (`spring-boot-starter-...`) são pacotes que trazem tudo o que
  uma funcionalidade precisa. O `starter-data-jpa` traz o Hibernate, o Spring Data e
  o pool de ligações (HikariCP).
- **`<scope>test</scope>`:** só existe nos testes, não vai para o jar final.
- **`<scope>runtime</scope>`:** não é usado no código, só a correr (o driver do PostgreSQL).

### `mvnw`, `mvnw.cmd` e `.mvn/`

O **Maven Wrapper**. Em vez de instalares o Maven, corres `./mvnw` e ele descarrega
a versão certa na primeira vez. Toda a gente e a CI usam a mesma versão.

### `src/main/java/.../ExtpulseApplication.java`

O ponto de entrada. `@SpringBootApplication` liga três coisas:

1. **Configuração automática:** vê o que está no classpath (por exemplo, o driver
   do PostgreSQL e o JPA) e configura tudo sozinho.
2. **Procura de componentes:** encontra as classes com `@Service`, `@RestController`,
   `@Repository`... dentro de `com.mccprogrammer.extpulse` e subpacotes.
3. **Configuração:** a própria classe pode definir `@Bean`s.

### Os pacotes por funcionalidade

```
store/  collection/  stats/  reviews/  alerts/  publicpage/  auth/
```

Cada um tem um `package-info.java` que diz o que lá pertence. É a arquitetura do
documento do projeto, escrita no próprio código (decisão
[0002](../decisions/0002-modular-monolith.md)). Estão vazios: o código entra a
partir do passo 2.

### `src/main/resources/application.yaml`

A configuração. Cada linha tem um comentário a explicar. As três importantes:

- **`ddl-auto: validate`:** o Hibernate nunca cria nem altera tabelas, só confirma
  que as entidades batem certo com a base de dados. Quem cria as tabelas é o Flyway
  (decisão [0003](../decisions/0003-postgresql-and-flyway.md)).
- **`open-in-view: false`:** sem isto, a sessão da base de dados fica aberta até a
  resposta HTTP ser escrita, e um controller pode disparar consultas escondidas.
  Desligar é a prática recomendada.
- **Sem URL da base de dados:** em desenvolvimento vem do `compose.yaml`, nos testes
  do Testcontainers e em produção de variáveis de ambiente. Nada de passwords no código.

### `src/main/resources/db/migration/`

Onde vão viver as migrações do Flyway: `V1__create_tables.sql`,
`V2__...sql`. Ao arrancar, o Flyway corre as que ainda não correram, por ordem, e
regista-as na tabela `flyway_schema_history`. **Uma migração que já correu nunca se
edita:** uma correção é uma migração nova. Está vazia até ao passo 2 (o `.gitkeep`
só serve para o Git guardar a pasta).

### `compose.yaml`

Descreve o PostgreSQL 17 de desenvolvimento. Escolhas:

- **`postgres:17` e não `latest`:** `latest` pode saltar para uma versão nova sem
  aviso e partir a app.
- **Porta `5433` no teu Mac:** para não chocar com um PostgreSQL que já tenhas na `5432`.
  Para te ligares com um cliente (DBeaver, TablePlus): `localhost:5433`, base de dados,
  utilizador e password `extpulse`.
- **Volume `postgres-data`:** os dados ficam guardados entre arranques.
  `docker compose down -v` apaga-os.
- **`healthcheck`:** o Spring Boot espera que a base de dados esteja pronta antes de se ligar.

Com a dependência `spring-boot-docker-compose`, o `./mvnw spring-boot:run` faz
`docker compose up` sozinho, lê os dados de ligação do contentor e, no fim, pára-o.

### `src/test/java/...`

| Ficheiro | O que faz |
| --- | --- |
| `TestcontainersConfiguration` | Define um PostgreSQL 17 descartável para os testes. `@ServiceConnection` liga-o à app sem configurar URL |
| `ExtpulseApplicationTests` | Confirma que a aplicação arranca (o "contexto" carrega) |
| `HealthEndpointTests` | Pede `/actuator/health` e espera `UP`. Como o health inclui a base de dados, prova que app, Flyway e PostgreSQL funcionam juntos |
| `TestExtpulseApplication` | Arranca a app com o PostgreSQL do Testcontainers em vez do Compose. Útil para correr no IDE sem `compose.yaml` |

Porquê testar com um PostgreSQL verdadeiro e não com H2 (base de dados em memória):
decisão [0004](../decisions/0004-docker-compose-and-testcontainers.md).

### `.github/workflows/ci.yml`

Em cada push para `main` e em cada pull request, o GitHub Actions instala o Java 25
e corre `./mvnw -B verify`: compila, corre todos os testes (o Testcontainers usa o
Docker do GitHub) e cria o jar. Se um teste falhar, o commit fica com uma cruz vermelha.

### `README.md` e `docs/`

- `README.md` (inglês): o que um recrutador vê primeiro no GitHub.
- `docs/decisions/` (inglês): uma decisão por ficheiro, no formato usado nas empresas (ADR).
- `docs/guia/` (português): este guia.

## 1.3 Comandos do dia a dia

```bash
./mvnw spring-boot:run      # arranca a app (e o PostgreSQL)
./mvnw verify               # compila e corre todos os testes
./mvnw -DskipTests package  # só cria o jar, em target/
docker compose up -d        # só a base de dados, em segundo plano
docker compose down         # pára a base de dados (os dados ficam)
docker compose down -v      # pára e apaga os dados
```

## 1.4 Confirmar que funciona

1. Docker a correr (`docker info` não dá erro).
2. `./mvnw verify` termina com `BUILD SUCCESS` e 2 testes passados.
3. `./mvnw spring-boot:run`, e noutro terminal `curl localhost:8080/actuator/health`
   devolve `{"status":"UP"}`.

## 1.5 Perguntas para saberes responder

- O que faz `@SpringBootApplication`?
- Porque é que o `pom.xml` quase não tem versões?
- Porquê `ddl-auto: validate` e não `update`?
- Porque é que os testes usam Testcontainers e não H2?
- Onde está a password da base de dados em produção?
