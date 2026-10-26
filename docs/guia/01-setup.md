# 1. Setup do projeto

**Resultado:** uma aplicação Spring Boot que arranca, liga-se a um PostgreSQL em
Docker e responde `{"status":"UP"}` em `/actuator/health`. Um teste prova isso e a CI
corre-o em cada push. Ainda não há código do domínio.

Este capítulo explica como o setup foi feito, para que serve cada ficheiro, onde vai
cada coisa que escreveres e como funcionam as dependências. O porquê de cada
ferramenta está em [ferramentas.md](ferramentas.md).

## 1.1 Como foi feito, por ordem

1. **Ferramentas na máquina:** Java 25 (já estava), Git, a linha de comandos do GitHub
   (`gh`) e o Docker Desktop. O Maven não é preciso: vem pelo Wrapper.
2. **Gerar o projeto no [Spring Initializr](https://start.spring.io)** com as escolhas
   da tabela abaixo. O Initializr dá um `.zip` com a estrutura pronta a compilar.
3. **Ajustar o que veio gerado:** o `compose.yaml`, o `application.yaml`, a versão do
   PostgreSQL nos testes e o `.gitignore` (ver 1.3).
4. **Escrever o que faltava:** o teste do health, os pacotes por funcionalidade, a CI,
   o README, os ADRs e este guia.
5. **Confirmar:** `./mvnw verify` com o Docker a correr. 2 testes, `BUILD SUCCESS`.
6. **Git e GitHub:** primeiro commit, repositório privado no GitHub e push. O push
   precisou de `gh auth refresh -s workflow` (ver os problemas em [ferramentas.md](ferramentas.md)).
7. **Confirmar no GitHub:** a CI correu e ficou verde.

### As escolhas no Initializr

| Campo | Valor | Porquê |
| --- | --- | --- |
| Project | Maven | O mais usado em empresas ([0001](../decisions/0001-java-25-and-spring-boot-4.md)) |
| Language | Java | — |
| Spring Boot | 4.1.1 | A versão estável atual; o 3.x já não é oferecido |
| Group | `com.mccprogrammer` | O teu "domínio ao contrário"; identifica-te como autor |
| Artifact | `extpulse` | O nome do projeto e do jar |
| Package name | `com.mccprogrammer.extpulse` | Todo o código Java fica dentro deste pacote |
| Packaging | Jar | Um só ficheiro executável, com o servidor web lá dentro |
| Configuration | YAML | Mais legível do que `.properties` quando há níveis |
| Java | 25 | A LTS mais recente, já instalada |

### As dependências escolhidas, e só estas

| No Initializr | O que entra no `pom.xml` | Para quê |
| --- | --- | --- |
| Spring Web | `spring-boot-starter-webmvc` | Endpoints HTTP (a API REST) |
| Spring Data JPA | `spring-boot-starter-data-jpa` | Ler e gravar na base de dados com classes Java |
| PostgreSQL Driver | `postgresql` | O "tradutor" entre o Java e o PostgreSQL |
| Flyway Migration | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Criar e alterar tabelas com ficheiros SQL |
| Validation | `spring-boot-starter-validation` | Validar dados de entrada (`@NotBlank`, `@Email`...) |
| Spring Boot Actuator | `spring-boot-starter-actuator` | Diagnóstico, como `/actuator/health` |
| Testcontainers | `spring-boot-testcontainers` + `testcontainers-*` | Um PostgreSQL verdadeiro nos testes |
| Docker Compose Support | `spring-boot-docker-compose` | Arrancar o PostgreSQL de desenvolvimento com a app |

O Initializr junta sozinho um starter `-test` por cada starter (`spring-boot-starter-webmvc-test`...).
É o novo modelo do Boot 4: cada funcionalidade traz as suas ferramentas de teste.

Spring Security, Mail e o resto entram quando forem precisos. **Regra:** só se junta
uma dependência quando há código que a usa. Cada dependência a mais é algo que tens de
saber explicar.

## 1.2 Mapa de ficheiros

```
extpulse/
├── pom.xml                         dependências e build (ver 1.5)
├── mvnw, mvnw.cmd                  Maven Wrapper: Mac/Linux e Windows
├── .mvn/wrapper/
│   └── maven-wrapper.properties    que versão do Maven descarregar
├── compose.yaml                    PostgreSQL de desenvolvimento
├── README.md                       a montra do projeto no GitHub (inglês)
├── .gitignore                      o que o Git ignora
├── .gitattributes                  fins de linha por tipo de ficheiro
├── .github/workflows/ci.yml        a CI do GitHub Actions
├── docs/
│   ├── decisions/                  ADRs (inglês)
│   └── guia/                       este guia (português)
├── src/
│   ├── main/
│   │   ├── java/com/mccprogrammer/extpulse/
│   │   │   ├── ExtpulseApplication.java    o arranque da app
│   │   │   ├── store/package-info.java     um pacote por funcionalidade
│   │   │   ├── collection/  stats/  reviews/  alerts/  publicpage/  auth/
│   │   └── resources/
│   │       ├── application.yaml            configuração
│   │       └── db/migration/               migrações Flyway (V1__..., V2__...)
│   └── test/
│       └── java/com/mccprogrammer/extpulse/
│           ├── TestcontainersConfiguration.java   PostgreSQL dos testes
│           ├── ExtpulseApplicationTests.java      a app arranca?
│           ├── HealthEndpointTests.java           a app chega à base de dados?
│           └── TestExtpulseApplication.java       correr a app sem o Compose
└── target/                         gerado pelo Maven; nunca vai para o Git
```

**`src/main` contra `src/test`:** o que está em `main` vai para o jar que corre em
produção. O que está em `test` só existe durante os testes. Dentro de cada um, `java/`
tem código Java e `resources/` tem tudo o resto (configuração, SQL, ficheiros estáticos).
É a convenção do Maven, igual em todos os projetos Java. Por isso, quem abre o projeto
sabe logo onde está cada coisa.

### De onde veio cada ficheiro

| Ficheiro | Origem | O que se mudou ou escreveu, e porquê |
| --- | --- | --- |
| `pom.xml` | Initializr | Ficou como veio; a descrição foi preenchida |
| `mvnw`, `mvnw.cmd`, `.mvn/` | Initializr | Nada |
| `ExtpulseApplication.java` | Initializr | Nada |
| `application.yaml` | Initializr + à mão | Vinha só com o nome da app; juntou-se `ddl-auto: validate`, `open-in-view: false`, UTC e o Actuator, cada linha com um comentário |
| `compose.yaml` | Initializr + à mão | Versão fixa `postgres:17`, porta `5433`, volume e healthcheck |
| `TestcontainersConfiguration.java` | Initializr + à mão | `postgres:17` fixo, igual ao `compose.yaml` |
| `ExtpulseApplicationTests.java`, `TestExtpulseApplication.java` | Initializr | Nada |
| `HealthEndpointTests.java` | À mão | O teste que prova o setup inteiro |
| `*/package-info.java` | À mão | Os 7 pacotes da arquitetura, cada um a dizer o que lá entra |
| `.gitignore` | Initializr + à mão | Juntou-se `.env`, para os segredos locais nunca irem para o Git |
| `.gitattributes` | Initializr | Nada |
| `.github/workflows/ci.yml` | À mão | A CI |
| `README.md`, `docs/` | À mão | Documentação |

## 1.3 Onde faço o quê

| Quero... | Onde |
| --- | --- |
| Juntar uma biblioteca | `pom.xml`, em `<dependencies>` (ver 1.5) |
| Escrever código Java da app | `src/main/java/com/mccprogrammer/extpulse/<funcionalidade>/` (ver 1.4) |
| Escrever um teste | `src/test/java/com/mccprogrammer/extpulse/<funcionalidade>/`, no mesmo pacote da classe testada |
| Criar ou alterar uma tabela | Um ficheiro novo em `src/main/resources/db/migration/` (`V2__...sql`, `V3__...`). Nunca editar um que já correu |
| Mudar uma configuração (porta, fuso, logs) | `src/main/resources/application.yaml` |
| Mudar a base de dados de desenvolvimento | `compose.yaml` (e a mesma versão em `TestcontainersConfiguration.java`) |
| Mudar a base de dados dos testes | `src/test/java/.../TestcontainersConfiguration.java` |
| Mudar o que a CI faz | `.github/workflows/ci.yml` |
| Mudar a versão do Java | `<java.version>` no `pom.xml` **e** `java-version` no `ci.yml` |
| Mudar a versão do Spring Boot | `<parent><version>` no `pom.xml` |
| Mudar a versão do Maven | `distributionUrl` em `.mvn/wrapper/maven-wrapper.properties` |
| Guardar uma password ou chave | Nunca no repositório. Numa variável de ambiente ou num `.env` local (já ignorado pelo Git) |
| Registar uma decisão | Um ficheiro novo em `docs/decisions/` e uma linha no `README` dessa pasta |
| Explicar um passo | Um capítulo em `docs/guia/` e uma linha em [ferramentas.md](ferramentas.md) |

## 1.4 Onde vai o código Java

### A regra das pastas

Em Java, **o pacote é a pasta**. Uma classe com `package com.mccprogrammer.extpulse.reviews;`
tem de estar em `src/main/java/com/mccprogrammer/extpulse/reviews/`. Se não estiver, não compila.

### Só dentro de `com.mccprogrammer.extpulse`

O `@SpringBootApplication` procura componentes (`@Service`, `@RestController`,
`@Repository`, `@Component`, `@Configuration`) no pacote dele e em todos os subpacotes.
Uma classe noutro pacote, por exemplo `com.mccprogrammer.outra`, **não é encontrada**
e o Spring não a cria. Por isso todo o código fica debaixo de `com.mccprogrammer.extpulse`.

### Um pacote por funcionalidade

| Pacote | O que lá entra | Passo |
| --- | --- | --- |
| `store` | Os adaptadores das lojas: a interface `StoreSource` e uma classe por loja. O único código que fala com as lojas | 3 |
| `collection` | A tarefa diária (`@Scheduled`) e o registo de cada recolha | 4 |
| `stats` | Totais e ganhos calculados a partir das fotografias | 5 |
| `reviews` | As avaliações de todas as lojas | 6 |
| `auth` | Contas, login e JWT | 7 |
| `alerts` | Regras de alerta e envio | 8 |
| `publicpage` | Página pública e badge | 9 |

O `package-info.java` de cada pacote diz o que lá pertence. Não pertence a nenhum?
Então ainda não é altura de o escrever, ou falta um pacote, e isso decide-se numa conversa.

### Como fica um pacote por dentro

Um exemplo do que o pacote `reviews` vai ter, para teres a imagem. Os nomes finais
decidem-se em cada passo.

```
reviews/
├── package-info.java
├── Review.java              @Entity: uma linha da tabela reviews
├── ReviewRepository.java    interface do Spring Data: consultas à tabela
├── ReviewService.java       as regras; é o que os outros pacotes usam
├── ReviewController.java    @RestController: /api/reviews
└── ReviewResponse.java      record: o JSON que a API devolve
```

O pedido HTTP entra pelo `Controller`, que chama o `Service`, que usa o `Repository`,
que fala com a base de dados. O `Controller` nunca usa o `Repository` diretamente.

**Visibilidade:** por omissão, uma classe sem `public` só é vista dentro do próprio
pacote. Fica `public` só o que outros pacotes precisam (normalmente o `Service` e os
`record` que ele devolve). Assim, o `stats` não consegue mexer nas entidades do
`reviews` por engano ([0002](../decisions/0002-modular-monolith.md)).

**Testes:** o teste de `ReviewService` chama-se `ReviewServiceTests` e fica em
`src/test/java/com/mccprogrammer/extpulse/reviews/`. No mesmo pacote, consegue usar as
classes sem `public`. O nome tem de acabar em `Tests` (ou `Test`) para o Maven o correr.

### `ExtpulseApplication.java`

```java
@SpringBootApplication
public class ExtpulseApplication {
	public static void main(String[] args) {
		SpringApplication.run(ExtpulseApplication.class, args);
	}
}
```

É o ponto de entrada (`main`). O `@SpringBootApplication` junta três coisas:

1. **Configuração automática:** vê o que está no classpath (o driver do PostgreSQL, o
   JPA, o Web MVC) e configura tudo sozinho.
2. **Procura de componentes:** encontra as tuas classes anotadas, como explicado acima.
3. **Configuração:** a própria classe pode definir `@Bean`s.

Esta classe não leva mais nada. Configuração nova vai para uma classe `@Configuration`
no pacote a que pertence.

## 1.5 As dependências: o `pom.xml` por dentro

O `pom.xml` é o único ficheiro que trata das dependências. O Maven lê-o, descarrega o
que lá está e passa as bibliotecas ao compilador e aos testes.

### As partes do ficheiro

| Parte | O que diz |
| --- | --- |
| `<parent>spring-boot-starter-parent 4.1.1</parent>` | Herda do Spring Boot as versões de centenas de bibliotecas testadas juntas, e a configuração dos plugins. É por isto que as dependências não têm `<version>` |
| `<groupId>`, `<artifactId>`, `<version>` | A identidade deste projeto: `com.mccprogrammer:extpulse:0.0.1-SNAPSHOT`. `SNAPSHOT` quer dizer "versão em desenvolvimento" |
| `<properties><java.version>25` | A versão do Java para compilar. O parent usa-a no plugin do compilador |
| `<dependencies>` | As bibliotecas, uma `<dependency>` cada |
| `<build><plugins>spring-boot-maven-plugin` | Transforma o jar normal num jar executável com tudo lá dentro (`java -jar`), e dá o `./mvnw spring-boot:run` |
| `<url/>`, `<licenses>`, `<developers>`, `<scm>` | Vazios; vieram do Initializr. Só contam se o projeto for publicado no Maven Central |

### Uma dependência

```xml
<dependency>
    <groupId>org.postgresql</groupId>      <!-- quem a publica -->
    <artifactId>postgresql</artifactId>     <!-- o nome -->
    <scope>runtime</scope>                  <!-- quando é precisa -->
</dependency>
```

Sem `<version>`, o Maven usa a que o parent do Spring Boot indica.

### Os scopes: quando cada dependência existe

| Scope | Compila o teu código? | Corre a app? | Corre os testes? | Vai para o jar? | Exemplos |
| --- | --- | --- | --- | --- | --- |
| `compile` (por omissão, sem `<scope>`) | sim | sim | sim | sim | Todos os `spring-boot-starter-*`, Flyway |
| `runtime` | não | sim | sim | sim | `postgresql`: o teu código nunca o chama diretamente; quem o usa é o Hibernate |
| `test` | só os testes | não | sim | não | Os starters `-test`, Testcontainers |

O `spring-boot-docker-compose` é `runtime` e `<optional>true</optional>`. Optional quer
dizer que não passa para outros projetos que dependam deste. Além disso, o plugin do
Spring Boot deixa-o de fora do jar final: em produção não há `compose.yaml`.

### Dependências que trazem outras (transitivas)

Um starter é uma dependência que traz muitas outras. Nunca escreveste "Hibernate" nem
"Tomcat" no `pom.xml`, mas estão lá:

```
spring-boot-starter-webmvc     → Spring MVC 7.0.9, Tomcat 11.0, Jackson 3
spring-boot-starter-data-jpa   → Hibernate 7.4.5, Spring Data JPA, HikariCP 7.0.2
spring-boot-starter-flyway     → Flyway 12.4.0
spring-boot-starter-validation → Hibernate Validator 9.1.3
spring-boot-starter-actuator   → Micrometer 1.17.1
(todos)                        → Logback 1.5 para os logs
spring-boot-starter-*-test     → JUnit 6.0.3, AssertJ 3.27.7, Mockito 5.23.0, MockMvc
testcontainers-postgresql      → Testcontainers 2.0.5
```

Para veres a árvore inteira:

```bash
./mvnw dependency:tree
```

### De onde vêm

1. O Maven lê o `pom.xml`.
2. Para cada dependência, procura primeiro em `~/.m2/repository` (a cache no teu Mac).
3. Se não estiver lá, descarrega-a do [Maven Central](https://central.sonatype.com) e guarda-a na cache.
4. Na CI, `cache: maven` no `ci.yml` guarda essa cache entre execuções para não
   descarregar tudo de novo.

### Juntar uma dependência nova

1. Confirma que há código que a vai usar já neste passo.
2. Escreve a `<dependency>` dentro de `<dependencies>`. Se for do ecossistema Spring (por
   exemplo, `spring-boot-starter-security`), não leva versão. Se não for (por exemplo,
   `springdoc-openapi`), leva uma versão. Guarda-a em `<properties>` para estar num só sítio:

   ```xml
   <properties>
       <java.version>25</java.version>
       <springdoc.version>X.Y.Z</springdoc.version>   <!-- a versão vê-se no Maven Central -->
   </properties>
   ...
   <dependency>
       <groupId>org.springdoc</groupId>
       <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
       <version>${springdoc.version}</version>
   </dependency>
   ```
3. Corre `./mvnw verify`. No IntelliJ ou no VS Code, recarrega o projeto Maven para o
   editor ver a biblioteca nova.
4. Junta uma ficha em [ferramentas.md](ferramentas.md): para que serve e porquê.

## 1.6 O Maven Wrapper

`mvnw` (Mac e Linux) e `mvnw.cmd` (Windows) são scripts. Na primeira vez que corres
`./mvnw`, ele lê o `.mvn/wrapper/maven-wrapper.properties`, descarrega o Maven 3.10.0
para `~/.m2/wrapper/` e passa-lhe os teus argumentos. Das outras vezes usa o que já
descarregou. Toda a gente e a CI usam exatamente a mesma versão.

`./mvnw verify` é o mesmo que `mvn verify`, só que sem instalar o Maven.

## 1.7 O que faz `./mvnw verify`

O Maven trabalha por fases, sempre pela mesma ordem. Pedir uma fase corre todas as anteriores.

| Fase | O que acontece | Resultado em `target/` |
| --- | --- | --- |
| `validate` | Confirma que o `pom.xml` está bem | — |
| `compile` | Compila `src/main/java` e copia `src/main/resources` | `classes/` |
| `test-compile` | Compila `src/test/java` | `test-classes/` |
| `test` | O plugin Surefire corre as classes `*Tests` e `*Test` | `surefire-reports/` (um relatório por classe) |
| `package` | Cria o jar e o plugin do Spring Boot torna-o executável | `extpulse-0.0.1-SNAPSHOT.jar` (o original fica como `.jar.original`) |
| `verify` | Verificações finais (mais tarde: a cobertura do JaCoCo) | — |

Se um teste falha, o Maven pára na fase `test` e mostra `BUILD FAILURE`. Para saber
porquê, abre o relatório em `target/surefire-reports/`.

`target/` é sempre gerado de novo, por isso está no `.gitignore`. `./mvnw clean` apaga-o.

O jar corre sozinho: `java -jar target/extpulse-0.0.1-SNAPSHOT.jar`. Mas precisa de uma
base de dados indicada por variáveis de ambiente, porque o suporte do Compose não vai no jar.

## 1.8 O que acontece quando a app arranca

Com `./mvnw spring-boot:run`:

1. O Maven compila e chama o `main` de `ExtpulseApplication`.
2. O `spring-boot-docker-compose` encontra o `compose.yaml`, corre `docker compose up` e
   espera que o PostgreSQL aceite ligações.
3. Lê do `compose.yaml` os dados de ligação (`localhost:5433`, base de dados, utilizador e
   password `extpulse`). Por isso o `application.yaml` não tem URL.
4. O HikariCP abre o conjunto de ligações à base de dados.
5. O Flyway aplica as migrações de `db/migration` que ainda não correram (por agora, nenhuma).
6. O Hibernate arranca e, com `ddl-auto: validate`, confirma que as entidades batem certo
   com as tabelas (por agora não há entidades).
7. O Tomcat abre a porta 8080 e o Actuator publica `/actuator/health` e `/actuator/info`.
8. O log mostra `Started ExtpulseApplication in ... seconds`.

Ao parar a app (Ctrl+C), o Spring Boot pára o contentor. Os dados ficam no volume.

A ordem 5 → 6 importa: o Flyway cria as tabelas antes de o Hibernate as verificar.

## 1.9 A configuração: `application.yaml`

Cada linha do ficheiro tem um comentário. As importantes:

- **`ddl-auto: validate`:** o Hibernate nunca cria nem altera tabelas, só confirma que as
  entidades batem certo com a base de dados. Quem cria as tabelas é o Flyway
  ([0003](../decisions/0003-postgresql-and-flyway.md)).
- **`open-in-view: false`:** sem isto, a sessão da base de dados fica aberta até a
  resposta HTTP ser escrita, e um controller pode disparar consultas escondidas.
  Desligar é a prática recomendada.
- **`jdbc.time_zone: UTC`:** as datas guardam-se em UTC. Só se convertem para a hora de
  Lisboa quando se mostram.
- **`exposure.include: health,info`:** o Actuator tem endpoints com dados internos
  (variáveis, beans). Só estes dois são seguros sem login.
- **Sem URL da base de dados:** em desenvolvimento vem do `compose.yaml` e nos testes do
  Testcontainers. Em produção vem de variáveis de ambiente.

**De onde vêm os valores:** o Spring Boot lê vários sítios e o mais forte ganha. Uma
variável de ambiente ganha ao `application.yaml`. `SPRING_DATASOURCE_URL` no servidor é
o mesmo que `spring.datasource.url` no ficheiro. É assim que a produção recebe a password
sem ela estar no código.

## 1.10 A base de dados de desenvolvimento: `compose.yaml`

Descreve o PostgreSQL 17 de desenvolvimento. Escolhas:

- **`postgres:17` e não `latest`:** `latest` pode saltar para uma versão nova sem aviso
  e partir a app.
- **Porta `5433` no teu Mac:** para não chocar com um PostgreSQL que já tenhas na `5432`.
  Para te ligares com um cliente (DBeaver, TablePlus): `localhost:5433`, e `extpulse` como
  base de dados, utilizador e password.
- **Volume `postgres-data`:** os dados ficam guardados entre arranques.
  `docker compose down -v` apaga-os.
- **`healthcheck`:** o Docker corre `pg_isready` de 5 em 5 segundos e marca o contentor
  como `healthy` quando a base de dados aceita ligações (vê-se em `docker compose ps`).

A password `extpulse` pode estar aqui porque é só desenvolvimento local. Em produção
nunca.

## 1.11 Os testes

| Ficheiro | O que faz |
| --- | --- |
| `TestcontainersConfiguration` | Define um PostgreSQL 17 descartável para os testes |
| `ExtpulseApplicationTests` | Confirma que a aplicação arranca (o "contexto" carrega) |
| `HealthEndpointTests` | Pede `/actuator/health` e espera `UP` |
| `TestExtpulseApplication` | Não é um teste: arranca a app com o PostgreSQL do Testcontainers |

**`TestcontainersConfiguration`.** `@TestConfiguration` é configuração que só existe nos
testes. O método `@Bean` cria um `PostgreSQLContainer` com a imagem `postgres:17`. O
`@ServiceConnection` diz ao Spring Boot: "liga-te a este contentor". O Spring descobre
sozinho o host, a porta (aleatória) e a password. Os testes que o querem usar escrevem
`@Import(TestcontainersConfiguration.class)`.

**`ExtpulseApplicationTests`.** `@SpringBootTest` arranca a aplicação inteira dentro do
teste. O método `contextLoads()` está vazio de propósito: se a app não arrancar (uma
configuração errada, uma migração com erro), o teste falha antes de lá chegar.

**`HealthEndpointTests`.** `@AutoConfigureMockMvc` dá um `MockMvc`, que faz pedidos HTTP
à app sem abrir uma porta. O teste pede `GET /actuator/health` e verifica o código `200`
e `"status": "UP"` no JSON. Como o health inclui a ligação à base de dados, este teste
prova que a app, o Flyway e o PostgreSQL funcionam juntos.

**`TestExtpulseApplication`.** Tem um `main` que arranca a app com o
`TestcontainersConfiguration` em vez do `compose.yaml`. Serve para correr a app a partir
do IDE com um PostgreSQL descartável, que desaparece quando a app pára.

**Nos testes, o Compose não arranca.** O suporte do Docker Compose desliga-se sozinho nos
testes, e quem dá a base de dados é o Testcontainers.

Porquê testar com um PostgreSQL verdadeiro e não com H2:
[0004](../decisions/0004-docker-compose-and-testcontainers.md).

## 1.12 A CI: `.github/workflows/ci.yml`

| Linha | O que faz |
| --- | --- |
| `on: push: branches: [main]` e `pull_request` | Corre em cada push para o `main` e em cada pull request |
| `runs-on: ubuntu-latest` | Num servidor Linux do GitHub, que já tem Docker |
| `actions/checkout@v5` | Descarrega o código do repositório |
| `actions/setup-java@v5` com `temurin` e `'25'` | Instala o Java 25 (a distribuição Temurin, da Eclipse) |
| `cache: maven` | Guarda `~/.m2/repository` entre execuções |
| `./mvnw -B verify` | Compila, corre os testes e cria o jar. `-B` é o modo sem interação, com logs limpos |

Se um teste falhar, o commit fica com uma cruz vermelha no GitHub, e o pull request mostra-a.

## 1.13 Os ficheiros do Git

- **`.gitignore`:** o que nunca vai para o Git. `target/` (é gerado), as pastas dos
  IDEs (`.idea`, `.vscode`), o `.DS_Store` do macOS, o `HELP.md` do Initializr e o `.env`
  (segredos locais).
- **`.gitattributes`:** obriga o `mvnw` a ter fins de linha Unix (LF) e os `.cmd` a ter
  os do Windows (CRLF). Se o `mvnw` fosse guardado com fins de linha do Windows, deixava
  de correr no Mac e na CI.

## 1.14 A documentação

- `README.md` (inglês): o que um recrutador vê primeiro no GitHub.
- `docs/decisions/` (inglês): uma decisão por ficheiro, no formato usado nas empresas (ADR).
- `docs/guia/` (português): este guia, um capítulo por passo.
- [`docs/guia/ferramentas.md`](ferramentas.md): o registo do que foi feito e as fichas das ferramentas.

## 1.15 Comandos do dia a dia

```bash
./mvnw spring-boot:run      # arranca a app (e o PostgreSQL)
./mvnw verify               # compila e corre todos os testes
./mvnw -DskipTests package  # só cria o jar, em target/
./mvnw clean                # apaga target/
./mvnw dependency:tree      # mostra todas as dependências, incluindo as transitivas
docker compose up -d        # só a base de dados, em segundo plano
docker compose ps           # estado dos contentores (healthy?)
docker compose exec postgres psql -U extpulse   # consola SQL da base de dados
docker compose down         # pára a base de dados (os dados ficam)
docker compose down -v      # pára e apaga os dados
```

## 1.16 Confirmar que funciona

1. Docker a correr (`docker info` não dá erro).
2. `./mvnw verify` termina com `BUILD SUCCESS` e 2 testes passados.
3. `./mvnw spring-boot:run`, e noutro terminal `curl localhost:8080/actuator/health`
   devolve `{"status":"UP"}`.

## 1.17 Perguntas para saberes responder

- O que faz `@SpringBootApplication`? E o que acontece a uma classe fora de `com.mccprogrammer.extpulse`?
- Porque é que o `pom.xml` quase não tem versões?
- Qual é a diferença entre os scopes `compile`, `runtime` e `test`? Porque é que o driver do PostgreSQL é `runtime`?
- O que é uma dependência transitiva? Dá um exemplo deste projeto.
- Para que serve o Maven Wrapper?
- O que acontece, por ordem, quando corres `./mvnw verify`?
- Porquê `ddl-auto: validate` e não `update`?
- Porque é que o Flyway tem de correr antes de o Hibernate validar?
- Porque é que os testes usam Testcontainers e não H2?
- Onde está a password da base de dados em produção?
