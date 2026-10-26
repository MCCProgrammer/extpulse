# 5. Contas e login

**Resultado:** qualquer pessoa cria conta e faz login. O login devolve um token (JWT)
que dá acesso aos endpoints de `/api` durante uma hora. A password é guardada só como
hash BCrypt, e os erros vêm no formato Problem Details. 59 testes.

**Porque é que este passo veio antes das estatísticas?** No plano inicial era o
passo 7. Mas tudo em `/api` pertence a uma conta (um publisher tem `user_id`), e nada
deve ir para um servidor sem login. Assim, o passo 6 (catálogo e estatísticas) já
escreve "as extensões *do utilizador com sessão iniciada*", em vez de ter de ser
refeito depois.

## 5.1 Ficheiros deste passo

### Código novo (`auth/`, salvo indicação)

| Ficheiro | O que é | Porquê |
| --- | --- | --- |
| `Role.java` | `USER`, `ADMIN` | O que uma conta pode fazer |
| `UserAccount.java` | Entidade JPA da tabela `users` | Chama-se `UserAccount` para não chocar com a classe `User` do Spring Security |
| `UserAccountRepository.java` | Repositório, com `findByEmail` em JPQL com `lower()` | Usa o índice único sobre `lower(email)` do passo 2. O `findByEmailIgnoreCase` gerado pelo Spring compara com `upper()` e não usaria o índice |
| `AuthProperties.java` | `record` com `jwt-secret` e `token-validity` | A chave e a validade dos tokens no `application.yaml` |
| `SecurityConfiguration.java` | As regras de acesso, o encoder de passwords e o assinador e verificador de JWT | Toda a segurança num só sítio |
| `TokenService.java` | Cria o token depois do login | Separado do login, para os testes poderem criar tokens (expirados, de admin) |
| `AccountService.java` | Registo e login | As regras: email normalizado, password ≤ 72 bytes, mesma resposta para email desconhecido e password errada |
| `AuthController.java` | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/me` | A API, com os pedidos e respostas como `record`s |
| `ApiExceptionHandler.java` (pacote raiz) | Erros no formato Problem Details, com a lista dos campos inválidos | Todas as respostas de erro da API ficam iguais |

### Base de dados, configuração e build

| Ficheiro | Mudança | Porquê |
| --- | --- | --- |
| `db/migration/V3__add_user_role.sql` | Coluna `role` em `users` (`USER` por omissão, `CHECK`) | Para haver administradores |
| `application.yaml` | Bloco `extpulse.auth` (`jwt-secret`, `token-validity: 1h`) | A chave vem do ambiente em produção |
| `pom.xml` | `spring-boot-starter-security-oauth2-resource-server` (e o `-test`) | Spring Security e verificação de JWT. No Boot 4 é este o nome; `spring-boot-starter-oauth2-resource-server` ficou obsoleto |

### Testes

`auth/AuthApiTests.java`: 12 testes com o MockMvc contra a app inteira e um PostgreSQL real.

| Teste | O que prova |
| --- | --- |
| `registersAnAccountWithALowercaseEmail` | 201, email em minúsculas, papel `USER`, a password nunca aparece na resposta |
| `storesABcryptHashNeverThePassword` | A base de dados tem `{bcrypt}$2...` e não a password |
| `theSameEmailWithDifferentCaseIsAConflict` | 409 em Problem Details |
| `saysWhichFieldsAreInvalid` | 400 com `errors.email` e `errors.password` |
| `rejectsAPasswordBcryptWouldCut` | 30 caracteres `€` = 90 bytes → 400 |
| `logsInAndUsesTheTokenOnProtectedEndpoints` | Login → token → `GET /api/me` funciona |
| `aWrongPasswordAndAnUnknownEmailGetTheSameAnswer` | As duas respostas 401 são iguais, letra a letra |
| `protectedEndpointsNeedAToken` | Sem token: 401 com `WWW-Authenticate: Bearer` |
| `rejectsATamperedToken` | Um token alterado: 401 |
| `rejectsAnExpiredToken` | Um token de há duas horas: 401 |
| `rejectsATokenSignedWithAnotherKey` | Um token assinado com outra chave: 401 |
| `adminPathsNeedTheAdminRole` | Em `/api/admin/**`: 403 para `USER`; para `ADMIN` passa a segurança |

### Documentação

[ADR 0007](../decisions/0007-accounts-and-jwt.md), README (secção "Try the API") e este capítulo.

## 5.2 Como funciona o login

```
1. POST /api/auth/login {email, password}
2. AccountService: procura a conta, compara a password com o hash BCrypt
3. TokenService: cria um JWT assinado (HS256) com sub, email, scope, exp
4. Resposta: {"token": "eyJ...", "tokenType": "Bearer", "expiresAt": "..."}

Daí em diante, em cada pedido:
   Authorization: Bearer eyJ...
   → o Spring Security verifica assinatura, validade e emissor
   → o controller recebe o token já verificado (@AuthenticationPrincipal Jwt)
```

### O que é um JWT

Três partes em base64, separadas por pontos: `cabeçalho.dados.assinatura`. Os dados
de um token real deste projeto:

```json
{"iss": "extpulse", "sub": "2", "email": "teste@example.com", "scope": "user",
 "iat": 1793054211, "exp": 1793057811}
```

- **Os dados não são secretos:** qualquer pessoa os lê (é só base64). Nunca se põe lá
  uma password.
- **A assinatura impede alterações:** é um HMAC-SHA256 dos dados com a chave secreta.
  Quem mudar `"scope": "user"` para `"user admin"` não consegue refazer a assinatura
  sem a chave. Há um teste para um token alterado e outro para um token assinado com
  outra chave.
- **Stateless:** a app não guarda sessões. Qualquer instância verifica qualquer token
  só com a chave, o que dá jeito quando houver mais do que um servidor.

### As decisões, uma a uma

- **O Spring Security como "resource server".** É a mesma configuração que verificaria
  tokens do Keycloak ou do Cognito. Aqui é a própria app que os emite (`NimbusJwtEncoder`)
  e verifica (`NimbusJwtDecoder`).
- **HS256 com uma chave de 32+ bytes.** Um só segredo para assinar e verificar, porque
  quem emite e quem verifica é a mesma app. Com outros serviços a verificar, passava-se
  a RS256 (par de chaves).
- **Chave vazia em desenvolvimento = chave aleatória.** Não há segredo nenhum no
  repositório. Em desenvolvimento os tokens morrem quando a app reinicia, e o log avisa.
  Em produção define-se `EXTPULSE_AUTH_JWT_SECRET`. Uma chave com menos de 32 bytes
  impede a app de arrancar.
- **Validade de 1 hora e sem refresh tokens.** Um JWT não se pode revogar, por isso a
  validade curta limita o estrago de um token roubado. Refresh tokens podem vir depois.
- **`scope` → autoridades.** O Spring Security transforma `"scope": "user admin"` em
  `SCOPE_user` e `SCOPE_admin`. A regra `/api/admin/**` pede `SCOPE_admin`.
- **CSRF desligado.** O CSRF aproveita credenciais que o browser envia sozinho
  (cookies). O token vai num cabeçalho que só o nosso código põe, por isso não há nada
  a aproveitar.

### Passwords

- **BCrypt através do `DelegatingPasswordEncoder`.** O hash fica `{bcrypt}$2a$10$...`.
  O prefixo diz o algoritmo, por isso um dia pode trocar-se para Argon2 sem invalidar
  as passwords antigas.
- **Mínimo de 10 caracteres e máximo de 72 bytes.** O BCrypt só lê os primeiros 72
  bytes, e o resto seria cortado sem aviso. O limite é em bytes e não em caracteres,
  porque um `€` ocupa 3 bytes em UTF-8.
- **Email em minúsculas** ao registar, e procura com `lower()` no login.

### O login não revela que emails têm conta

- A mesma resposta para "email desconhecido" e "password errada": 401, `Wrong email
  or password`. O teste compara as duas respostas.
- **O mesmo tempo de resposta.** Comparar uma password com BCrypt demora dezenas de
  milissegundos de propósito. Se o login respondesse logo quando o email não existe,
  bastava medir o tempo. Por isso, sem conta, compara-se com um hash falso.
- **O registo diz "já existe" (409).** É uma troca consciente: quem se regista precisa
  de saber. Para esconder isso era preciso confirmação por email, que fica para depois.

### Problem Details (RFC 9457)

Todas as respostas de erro têm o mesmo formato, com `Content-Type: application/problem+json`:

```json
{"type": "about:blank", "title": "Bad Request", "status": 400,
 "detail": "Some fields are not valid", "instance": "/api/auth/register",
 "errors": {"email": "must be a well-formed email address",
            "password": "size must be between 10 and 72"}}
```

O Spring MVC já cria estas respostas. O `ApiExceptionHandler` só junta o `errors` com
os campos inválidos. As exceções próprias (`EmailAlreadyRegisteredException`,
`InvalidCredentialsException`) estendem `ErrorResponseException` e já trazem o código
HTTP e a mensagem.

## 5.3 Experimentar

Com a app a correr (`./mvnw spring-boot:run`):

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email": "tu@example.com", "password": "pelo menos 10 caracteres"}'

curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email": "tu@example.com", "password": "pelo menos 10 caracteres"}'

# copia o "token" da resposta:
curl localhost:8080/api/me -H 'Authorization: Bearer COLA_AQUI_O_TOKEN'
```

Para veres o que vai dentro de um token, cola-o em [jwt.io](https://jwt.io). Só tokens
de desenvolvimento: um token de produção não se cola em sites de terceiros.

Para tornar uma conta administradora (por agora, só por SQL), e depois fazer login de
novo para o token trazer o papel novo:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'tu@example.com';
```

## 5.4 Confirmar que funciona

```bash
./mvnw verify      # 59 testes
```

**Confirmado também:** com a regra de `/api/admin/**` trocada por "basta ter sessão
iniciada", falha o teste `adminPathsNeedTheAdminRole`. Contra a base de dados de
desenvolvimento, com `curl`: registo 201, repetido 409, inválido 400 com os campos,
password errada 401, `/api/me` com token 200 e sem token 401.

## 5.5 Perguntas para saberes responder

- Onde e como guardas as passwords? Porquê BCrypt e não SHA-256?
- O que tem um JWT? Alguém pode ler os dados? E alterá-los?
- Como revogas um JWT antes de expirar? (Não se revoga: validade curta, ou uma lista de tokens revogados.)
- Porque é que desligaste o CSRF? Quando é que não o podias desligar?
- Porque é que o login responde igual (e no mesmo tempo) para email desconhecido e password errada?
- Porquê HS256 e não RS256?
- Onde está a chave de assinatura em produção? O que acontece se mudar?
- Porque é que a password tem um máximo de 72 bytes?
