# 3. Adaptadores das lojas

**Resultado:** o ExtPulse sabe falar com as três lojas. Encontra as extensões de um
publisher no Marketplace e no Open VSX, e lê installs, média, número de avaliações e
versão de qualquer listing, incluindo as do Chrome. 25 testes com o WireMock no lugar
das lojas, e 3 testes contra as lojas reais que correm uma vez por semana.

Ainda nada é gravado na base de dados: isso é a recolha diária, no passo 4.

## 3.1 Ficheiros deste passo

### Código (`src/main/java/.../store/`)

| Ficheiro | O que é | Porquê |
| --- | --- | --- |
| `Store.java` | `enum` com as três lojas | Os mesmos nomes que o `CHECK` da coluna `listings.store` |
| `StoreSource.java` | A interface: `store()`, `discover(publisher)`, `fetchStats(externalId)` | O resto da app só conhece esta interface. Uma loja nova é uma classe nova |
| `DiscoveredListing.java` | `record`: loja, ID na loja, nome, URL | O que a descoberta devolve |
| `ListingStats.java` | `record`: installs, média, nº de avaliações, versão | O que vai ser uma linha de `snapshots`. Recusa negativos e arredonda a média a 2 casas, como a coluna |
| `StoreException.java` | A loja não respondeu, respondeu com erro ou com algo inesperado | Um só tipo de erro para o passo 4 apanhar, seja qual for a loja |
| `ListingNotFoundException.java` | A loja respondeu e não tem essa listing | Diferente de "a loja está em baixo": uma extensão despublicada não se tenta de novo |
| `StoreProperties.java` | `record` com `@ConfigurationProperties("extpulse.stores")` | URLs e timeouts no `application.yaml`; os testes apontam os URLs para o WireMock |
| `StoreConfiguration.java` | Ativa o `StoreProperties` | Fica no pacote `store`, não na classe principal |
| `StoreHttp.java` | Cria o cliente HTTP: timeouts e User-Agent | As três lojas usam o mesmo cliente, configurado num só sítio |
| `MarketplaceSource.java` | Adaptador do VS Code Marketplace | API `extensionquery` |
| `OpenVsxSource.java` | Adaptador do Open VSX | API REST documentada |
| `ChromeWebStoreSource.java` | Adaptador da Chrome Web Store | Lê a página do item, porque não há API |

As classes `*Source` não são `public`: o resto da app usa-as através da interface
`StoreSource` (o Spring injeta uma `List<StoreSource>` com as três).

### Configuração e build

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `pom.xml` | `jsoup` 1.23.2; `wiremock-standalone` 3.13.2 (teste); `<excludedGroups>live</excludedGroups>` | Ler HTML; simular as lojas; deixar os testes contra as lojas reais fora do build normal |
| `application.yaml` | Bloco `extpulse.stores` (URLs, `connect-timeout: 5s`, `read-timeout: 10s`) | Nada de URLs escritos no código |
| `.github/workflows/live-stores.yml` | Novo: às segundas, 07:00 UTC, corre só os testes `live` | Saber quando uma loja muda |

### Testes (`src/test/...`)

| Ficheiro | O que testa |
| --- | --- |
| `store/MarketplaceSourceTests.java` | 8 testes: descoberta, publisher desconhecido, estatísticas, extensão sem avaliações, extensão desconhecida, cabeçalhos e flags enviados, erro 500, timeout |
| `store/OpenVsxSourceTests.java` | 7 testes: descoberta, namespace desconhecido, estatísticas com e sem avaliações, extensão desconhecida, ID mal formado, erro 503 |
| `store/ChromeWebStoreSourceTests.java` | 10 testes: página com e sem avaliações, item desconhecido, página mudada, ID inválido, redirecionamentos, codificação do URL |
| `store/LiveStoreTests.java` | 3 testes contra as lojas reais (tag `live`) |
| `store/StoreTestSupport.java` | Prepara o WireMock e as propriedades que apontam para ele |
| `resources/wiremock/__files/` | Respostas reais gravadas a 2026-10-26 (16 ficheiros) |
| `resources/wiremock/README.md` | De onde veio cada resposta e como as atualizar |

### Documentação

[ADR 0005](../decisions/0005-store-adapters.md), README (stack, estado, comandos) e este capítulo.

## 3.2 O desenho

```
            StoreSource (interface)
           /         |          \
MarketplaceSource  OpenVsxSource  ChromeWebStoreSource
   API JSON          API JSON         página HTML
```

É o padrão **Adapter** (cada classe adapta uma loja à mesma interface) usado como
**Strategy** (o passo 4 percorre a lista de `StoreSource` sem saber qual é qual).

**Porque os adaptadores devolvem `record`s e não entidades JPA?** O `store` não deve
saber como os dados são gravados. As entidades entram no passo 4, e quem as cria a
partir de um `ListingStats` é a recolha.

**O que ficou de fora deste passo, e porquê:**
- **Avaliações (`fetchReviews`):** entram no passo 6, com a tabela `reviews`.
- **Retries e ritmo de 1 pedido por segundo:** entram no passo 4. É a recolha que sabe
  quantos pedidos vai fazer e se vale a pena repetir. Um adaptador faz uma tentativa,
  com timeout, e diz o que aconteceu ([ADR 0005](../decisions/0005-store-adapters.md)).

## 3.3 Cada loja

Tudo foi testado contra as lojas reais antes de escrever código.

### VS Code Marketplace

- `POST /_apis/public/gallery/extensionquery` com um JSON de filtros.
- **Filter type** `18` = todas as extensões de um publisher; `7` = uma extensão por
  `publisher.nome`.
- **Flags** `769` = `0x1` (versões) + `0x100` (estatísticas) + `0x200` (só a última
  versão). O documento do projeto usava `914`, que traz ficheiros e propriedades que não
  são precisos. No código são constantes com nome, não um número mágico.
- **Cabeçalho `Accept: application/json;api-version=7.2-preview.1`:** a API não é
  documentada oficialmente; fixar a versão evita surpresas.
- **Uma estatística que falta não é zero.** Sem avaliações, a resposta não traz
  `averagerating` nem `ratingcount`. O adaptador devolve média `null` e contagem 0.
- Uma extensão desconhecida devolve `200` com a lista vazia, por isso o "não encontrada"
  vem da lista vazia, não do código HTTP.

### Open VSX

- `GET /api/{namespace}` lista os nomes das extensões; `GET /api/{namespace}/{nome}`
  dá os números.
- **A pesquisa (`/api/-/search?namespace=`) ignora o filtro de namespace** e devolve as
  extensões mais populares de todo o Open VSX. Por isso a descoberta faz uma chamada por
  extensão (6 pedidos para os 5 do MCCProgrammer).
- `404` = não existe. No `discover`, um namespace desconhecido devolve lista vazia (o
  publisher pode simplesmente não estar no Open VSX). No `fetchStats` é
  `ListingNotFoundException`.
- O ID é `namespace/nome` (`MCCProgrammer/diff-checker`), como definido no passo 2.

### Chrome Web Store

- `GET /detail/{id}?hl=en` e leitura do HTML com o jsoup.
- **Lê o texto visível, não as classes CSS.** As classes (`F9iKBc`, `GlMWqe`) são geradas
  e mudam sem aviso. O texto "37 users", "5 out of 5", "2 ratings", "Version 1.0.1
  Updated" é o que a página mostra a uma pessoa.
- **Uma armadilha:** na página de um item sem avaliações aparece a média de *outro*
  item, na secção "relacionados", num `aria-label`. O adaptador procura "X out of 5"
  seguido de "N ratings" ou "No ratings", que só existe na secção do próprio item. Há um
  teste só para isto.
- **Item desconhecido:** a loja responde `200` com uma página genérica sem `<h1>`. Sem
  `<h1>` = `ListingNotFoundException`.
- **Página mudada:** há `<h1>` mas não há "users" ou "out of 5" → `StoreException` com
  "has the page changed?". Nunca grava um zero falso.
- **Sem descoberta:** a loja não pesquisa por publisher. As listings do Chrome entram
  pelo ID (um endpoint da API, no passo 5).
- **Redirecionamentos:** ver 3.6.

## 3.4 Configuração

```yaml
extpulse:
  stores:
    marketplace-url: https://marketplace.visualstudio.com
    open-vsx-url: https://open-vsx.org
    chrome-web-store-url: https://chromewebstore.google.com
    connect-timeout: 5s
    read-timeout: 10s
```

- **`@ConfigurationProperties("extpulse.stores")`** liga este bloco ao `record
  StoreProperties`. O Spring converte `5s` num `Duration` e o texto num `URI`. Os
  nomes com hífen (`open-vsx-url`) ligam-se aos campos em camelCase (`openVsxUrl`).
- **Timeouts:** 5 s para abrir a ligação e 10 s para a resposta. Sem eles, uma loja que
  não responde pendurava a recolha.
- **User-Agent `ExtPulse (+https://github.com/MCCProgrammer/extpulse)`:** quem vê os
  pedidos do lado da loja sabe de onde vêm. É a prática educada para quem lê páginas de
  outros.
- **O cliente HTTP** é o `RestClient` do Spring por cima do `HttpClient` do JDK. Não foi
  preciso nenhuma biblioteca HTTP extra.

## 3.5 Os testes

### Com o WireMock (no build normal)

O WireMock é um servidor HTTP falso. Cada teste diz "quando pedirem X, responde Y" e
o adaptador fala com ele como falaria com a loja. As respostas são **as reais**,
gravadas das lojas. Assim, os testes:

- não dependem da internet nem de as lojas estarem de pé;
- dão sempre os mesmos números (1764 installs no Diff Checker, por exemplo);
- testam os casos difíceis de provocar a sério: erro 500, resposta lenta (timeout),
  página mudada, redirecionamentos.

As páginas do Chrome foram gravadas sem `<script>`, `<style>` e `<link>`: passaram de
~650 KB para menos de 130 KB cada, com o texto visível igual (confirmado na gravação).

Os testes dos adaptadores **não arrancam o Spring**: criam o adaptador com `new`. Por
isso correm em menos de 3 segundos.

### Contra as lojas reais (`LiveStoreTests`, tag `live`)

- Usam o `application.yaml` verdadeiro (URLs e timeouts reais), através de um
  `@SpringBootTest` só com as classes do pacote `store`.
- O `pom.xml` tem `<excludedGroups>live</excludedGroups>`: o `./mvnw verify` e a CI
  normal não os correm. Assim, o build não falha por a internet ou uma loja falharem.
- `./mvnw test -Dgroups=live -DexcludedGroups=` corre só estes. O `-DexcludedGroups=`
  vazio anula o valor do `pom.xml`.
- O workflow `live-stores.yml` corre-os às segundas. Se falharem, o GitHub manda um
  email. Nota: os workflows agendados só correm no branch `main`, por isso este só
  começa depois do merge.

## 3.6 Problemas que apareceram

| Problema | Causa | Solução |
| --- | --- | --- |
| Os testes do Marketplace falhavam com `RST_STREAM: Stream cancelled` | Em `http://`, o cliente do JDK tenta passar para HTTP/2 (h2c), e o Jetty do WireMock corta os pedidos com corpo (os `POST`) | `http2PlainDisabled(true)` no WireMock. As lojas reais são `https`, onde o HTTP/2 se negoceia sem esse passo |
| O teste `live` do Chrome falhava com `Illegal character in path` (só contra a loja real) | A loja redireciona `/detail/{id}` para `/detail/director’s-cut-for-letter/{id}` com o `’` em UTF-8 cru. O JDK lê os cabeçalhos como ISO-8859-1 e o URL fica inválido | O adaptador do Chrome segue os redirecionamentos ele próprio: recupera os bytes UTF-8 e codifica-os (`%E2%80%99`). Testado com o caso real |
| A partir de Portugal há um redirecionamento a mais | A Google manda primeiro para a página de consentimento de cookies (`consent.google.com`), que volta para a loja | O mesmo ciclo de redirecionamentos trata disso, até 5 saltos |

O segundo problema é a razão de existirem testes contra as lojas reais: as respostas
gravadas não mostravam o redirecionamento.

## 3.7 Confirmar que funciona

```bash
./mvnw verify                                   # 35 testes: 10 de antes + 25 das lojas
./mvnw test -Dtest='*SourceTests'               # só os adaptadores, sem Docker
./mvnw test -Dgroups=live -DexcludedGroups=     # contra as lojas reais (precisa de internet)
```

## 3.8 Perguntas para saberes responder

- Como juntavas uma loja nova (por exemplo, o JetBrains Marketplace)?
- Porque é que os adaptadores devolvem `record`s e não entidades?
- Qual é a diferença entre `StoreException` e `ListingNotFoundException`, e porque importa?
- Como testas código que fala com APIs externas sem depender delas?
- Se os testes usam respostas gravadas, como sabes quando a loja muda?
- Porque é que o adaptador do Chrome lê o texto e não as classes CSS?
- Porque é que os retries não estão nos adaptadores?
- O que são as flags `769` do Marketplace?
