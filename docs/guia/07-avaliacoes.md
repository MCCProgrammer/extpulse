# 7. Avaliações

**Resultado:** a recolha diária também lê as avaliações do Marketplace e do Open VSX e
guarda-as. `GET /api/reviews` é uma caixa de entrada única, com filtros (por ler, sem
resposta, poucas estrelas, loja, extensão), e `PATCH /api/reviews/{id}` marca uma
avaliação como lida. Antes disso, entrou a barreira de cobertura na CI (JaCoCo). 97
testes, 93,9% das linhas cobertas.

## 7.0 Como podia ser, e como ficou

O documento do projeto dizia só "avaliações do Marketplace e do Open VSX num só sítio,
com estrelas, texto, versão e estado respondida / por responder". Antes de desenhar,
pedi às lojas o que elas dão de facto:

| | Marketplace | Open VSX | Chrome Web Store |
| --- | --- | --- | --- |
| Onde | `GET .../publishers/{p}/extensions/{n}/reviews` | `GET /api/{ns}/{n}/reviews` | Página `/detail/{nome}/{id}/reviews` |
| ID da avaliação | Sim (`id`) | Não; uma avaliação por utilizador | Não |
| Texto, estrelas, data | Sim | Sim (o texto vem muitas vezes vazio) | Sim, no HTML |
| Versão avaliada | Sim | Não | Não |
| Resposta do publisher | Sim (`reply`) | Não existe | Não lida |

Daí saíram as decisões:

- **O que identifica uma avaliação:** o `id` no Marketplace; no Open VSX, o autor
  (`github:login`), porque cada pessoa só pode deixar uma avaliação por extensão.
- **"Respondida" só existe no Marketplace.** No Open VSX a API devolve `replied: null`
  ("não se aplica"), e o filtro `unanswered` só mostra avaliações do Marketplace.
- **"Por ler" é teu, não da loja.** A coluna `read_at` muda quando a marcas como lida.
  Se a avaliação for editada (outras estrelas ou outro texto), volta a "por ler".
- **Chrome fica para depois.** As avaliações estão no HTML, mas sem ID: a chave teria
  de ser autor + data, e isso parte quando alguém edita a avaliação. As tuas extensões
  do Chrome ainda não têm avaliações escritas para testar.
- **Uma descoberta importante:** o `filterOptions=1` que o documento do projeto
  indicava **esconde as avaliações sem texto**. Nas 100 avaliações mais recentes do
  Prettier, 20 eram só estrelas. Um 1★ sem comentário é exatamente o que queres ver,
  por isso o ExtPulse usa o filtro por omissão. Há um teste que confirma que o
  parâmetro não é enviado.

## 7.1 Ficheiros: a barreira de cobertura (PR à parte)

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `pom.xml` | `jacoco-maven-plugin` 0.8.15: `prepare-agent`, `report`, `check` | Medir a cobertura e falhar abaixo de 85% das linhas, 70% dos ramos ou 80% das linhas de um `*Service` |
| `.github/workflows/ci.yml` | Passos `Coverage summary` e `Upload coverage report` | A cobertura na página do run e o relatório HTML para descarregar |

Medido antes: 93,8% das linhas, 78,2% dos ramos. Com o limite dos serviços subido para
99% de propósito, o build falha com `Coverage checks have not been met`. Mais em
[ferramentas.md](ferramentas.md#jacoco).

## 7.2 Ficheiros: as avaliações

### Código novo

| Ficheiro | O que é | Porquê |
| --- | --- | --- |
| `store/StoreReview.java` | `record`: ID, estrelas, texto, autor, versão, data, resposta | A avaliação como a loja a mostra, igual para todas as lojas |
| `reviews/ReviewWriter.java` | Upsert das avaliações de uma listing, numa transação | Visto outra vez = atualizado, nunca duplicado; guarda o dia em que foi vista pela primeira vez |
| `reviews/ReviewQueries.java` | A consulta da caixa de entrada (filtros, total) e o "marcar como lida" | SQL com quatro `JOIN`s e filtros opcionais |
| `reviews/ReviewController.java` | `GET /api/reviews`, `PATCH /api/reviews/{id}` | A API |
| `db/migration/V4__create_reviews.sql` | Tabela `reviews` | Ver 7.3 |

### Código alterado

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `store/StoreSource.java` | Método novo `fetchReviews(externalId)` | Todas as lojas respondem à mesma pergunta |
| `store/MarketplaceSource.java` | `fetchReviews`: as últimas 100, sem as apagadas nem as escondidas; a resposta, se não foi apagada | — |
| `store/OpenVsxSource.java` | `fetchReviews`; o autor como ID | — |
| `store/ChromeWebStoreSource.java` | `fetchReviews` devolve sempre uma lista vazia, com a explicação | — |
| `collection/ResilientStoreCalls.java` | `fetchReviews`, com o mesmo ritmo e os mesmos retries | Os dois pedidos passam pelo mesmo `call(...)` genérico |
| `collection/CollectionService.java` | Depois da fotografia, lê e grava as avaliações; o erro diz `stats:` ou `reviews:` | Se as avaliações falharem, os números do dia já estão gravados |

### Testes

| Ficheiro | Novos testes |
| --- | --- |
| `store/MarketplaceSourceTests.java` | A avaliação com a tua resposta; uma avaliação sem texto mantém-se, e o `filterOptions` não é enviado; apagadas, escondidas e respostas apagadas ficam de fora; extensão desconhecida e ID inválido |
| `store/OpenVsxSourceTests.java` | 16 avaliações com o autor como ID e o texto vazio como `null`; sem avaliações; extensão desconhecida |
| `store/ChromeWebStoreSourceTests.java` | Ainda não lê avaliações |
| `collection/CollectionServiceTests.java` | Grava e depois atualiza (a resposta nova, o mesmo `first_seen_at`); editada volta a "por ler"; avaliações a falhar não perdem a fotografia |
| `reviews/ReviewsApiTests.java` | 6 testes: todas as lojas da mais recente para a mais antiga, com `replied`; filtros; ler e "por ler"; páginas; filtros inválidos (400); outra conta não vê nada (404 ao marcar) |
| `store/LiveStoreTests.java` | Também lê avaliações das lojas reais |
| `resources/wiremock/__files/` | 4 respostas novas, com os autores anonimizados (ver o README da pasta) |

### Documentação

[ADR 0009](../decisions/0009-reviews.md), README (endpoints) e este capítulo.

## 7.3 A tabela `reviews`

```sql
UNIQUE (listing_id, external_id)   -- uma linha por avaliação e por listing
rating smallint CHECK (rating BETWEEN 1 AND 5)
text text                          -- NULL: só estrelas
posted_at timestamptz              -- quando foi escrita ou editada (diz a loja)
reply text                         -- a tua resposta (Marketplace)
first_seen_at timestamptz DEFAULT now()  -- quando o ExtPulse a viu pela primeira vez
read_at timestamptz                -- quando a marcaste como lida
```

**Porquê `first_seen_at` e `posted_at`?** Uma avaliação escrita há um ano numa
extensão que juntaste hoje não é "nova". Os alertas do passo 8 vão usar o
`first_seen_at` para saber o que apareceu desde a última recolha.

### O upsert

```sql
ON CONFLICT (listing_id, external_id) DO UPDATE SET
    rating = EXCLUDED.rating, text = EXCLUDED.text, ..., reply = EXCLUDED.reply,
    read_at = CASE
        WHEN reviews.rating IS DISTINCT FROM EXCLUDED.rating
          OR reviews.text IS DISTINCT FROM EXCLUDED.text THEN NULL
        ELSE reviews.read_at
    END
```

- O `first_seen_at` não aparece no `SET`, por isso nunca muda.
- O `IS DISTINCT FROM` é um "diferente de" que trata `NULL` como valor. `NULL <> 'x'`
  dá `NULL`, ou seja, nem verdadeiro nem falso. `NULL IS DISTINCT FROM 'x'` dá
  verdadeiro. Sem isto, uma avaliação que passa de "só estrelas" para "com texto" não
  voltava a "por ler".
- **Confirmado:** sem o `CASE`, o teste `anEditedReviewIsUnreadAgain` falha.

## 7.4 A caixa de entrada

```
GET /api/reviews?unread=true&maxRating=2
```

```json
{"total": 1, "limit": 50, "offset": 0, "items": [
  {"id": 1, "extensionName": "Diff Checker", "store": "VS_MARKETPLACE", "rating": 1,
   "text": "Does not work for me...", "productVersion": "0.1.1",
   "postedAt": "2026-01-29T15:11:13.570Z", "reply": "...", "replied": true, "read": false, ...}]}
```

- **Filtros opcionais em SQL:** cada filtro é `(CAST(:x AS tipo) IS NULL OR coluna = :x)`.
  Sem valor, a condição é sempre verdadeira. O `CAST` é preciso porque o PostgreSQL não
  sabe o tipo de um parâmetro que chega como `NULL`.
- **O total na mesma consulta:** `count(*) OVER ()` conta as linhas antes do `LIMIT`.
  Assim, uma consulta dá a página e o total.
- **Validação dos parâmetros:** `@Min(1) @Max(5)` no `maxRating` e limites no
  `limit`/`offset`. No Spring 7 é o próprio MVC que valida e devolve 400 em Problem
  Details. Por isso **não** se põe `@Validated` na classe: o Spring validava duas vezes,
  e a segunda dava 500.
- **Dono:** o `PATCH` só altera se a avaliação for de uma listing tua. Senão, 404.

## 7.5 Experimentar

Com a app a correr e um token de login:

```bash
curl "localhost:8080/api/reviews" -H "Authorization: Bearer $TOKEN"
curl "localhost:8080/api/reviews?unread=true&maxRating=2" -H "Authorization: Bearer $TOKEN"
curl -X PATCH localhost:8080/api/reviews/1 -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"read": true}'
```

**Confirmado na base de dados de desenvolvimento** (conta temporária, como no passo 6):

- A recolha recolheu 12 de 12 e trouxe a tua única avaliação: o 1★ do Diff Checker
  (versão 0.1.1, de 29 de janeiro), marcado `replied: true`.
- Marcar como lida deu 204, e as "por ler" passaram a 0.
- Depois foi reposta como não lida, e a conta temporária apagada.

## 7.6 Confirmar que funciona

```bash
./mvnw verify      # 97 testes e a barreira de cobertura
./mvnw test -Dgroups=live -DexcludedGroups=   # também lê avaliações reais
```

## 7.7 Perguntas para saberes responder

- Como identificas uma avaliação no Open VSX, que não tem ID?
- Porque é que guardas `first_seen_at` além de `posted_at`?
- O que é `IS DISTINCT FROM`, e porque é que aqui não chegava `<>`?
- Como fazes filtros opcionais numa só consulta SQL? E o total com paginação?
- Porque é que "respondida" é `null` no Open VSX e não `false`?
- O que mede o JaCoCo? Cobertura alta quer dizer testes bons?
- Porque é que o `filterOptions=1` seria um erro?
