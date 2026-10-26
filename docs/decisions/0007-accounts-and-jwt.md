# 0007. Accounts and stateless login with self-issued JWTs

**Date:** 2026-10-26 · **Status:** accepted

## Context

Every resource under `/api` belongs to an account (publishers have a `user_id`), and
nothing should be deployed without login. The clients are scripts, a future web page
and READMEs (the badge, which stays public). There is one application and no
existing identity provider.

## Decision

- Accounts live in `users`, with the password stored as a BCrypt hash through
  Spring Security's `DelegatingPasswordEncoder` (`{bcrypt}...`).
- `POST /api/auth/login` returns a JWT signed by the app with HS256. Every other
  `/api` request sends it as `Authorization: Bearer <token>` and is checked by Spring
  Security's OAuth2 resource server support (signature, expiry, issuer). No session.
- The token's `sub` is the account ID and `scope` is `user` or `user admin`;
  `/api/admin/**` needs `SCOPE_admin`. Tokens last one hour.
- The signing key comes from `EXTPULSE_AUTH_JWT_SECRET` (at least 32 bytes). When it
  isn't set, a random key is made at startup and a warning is logged.
- Errors are Problem Details (RFC 9457). Login answers the same 401 for a wrong
  password and an unknown email, and takes the same time for both.

## Consequences

- Any instance can check any token without shared state, which suits scaling out.
- A token can't be revoked before it expires; the one-hour validity limits that.
  A role change applies at the next login.
- No refresh tokens yet: the client logs in again after an hour.
- Changing the key logs everyone out.

## Alternatives considered

- **Server sessions with a cookie:** simple for a browser, but needs CSRF protection
  and shared session storage across instances, and fits scripts less well.
- **An identity provider (Keycloak, Auth0, AWS Cognito):** the right call for many
  apps and teams, but a second system to run for a single application. The resource
  server setup used here is the same one that would validate their tokens.
- **RS256 (key pair):** needed when other services must verify tokens without being
  able to issue them. Here the issuer and the verifier are the same app.
