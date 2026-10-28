# 0015. The website: Thymeleaf pages in the same app, signed in with a session

**Date:** 2026-10-27 · **Status:** accepted

## Context

Everything worked through the REST API, which needs curl or Swagger. The plan has a
public portfolio page and README badges, and the owner needs a dashboard to read
stats and reviews and to manage alerts without crafting requests.

## Decision

- **Thymeleaf, rendered by the app itself:** the pages are in the same jar and image,
  served by Caddy under the same domain. No separate frontend build or hosting.
- **Each feature owns its pages,** next to its API: `CatalogPages`, `DashboardPages`,
  `ReviewPages`, `AlertPages`, `AdminPages`, `AccountPages` and `publicpage`. They call
  the same services, which stay package-private; `StatsService.overview` and
  `PublicProfiles` became public for the public pages.
- **Two security filter chains:** `/api/**` keeps stateless JWT (no session, no CSRF);
  everything else signs in with a form and a session cookie (HttpOnly, SameSite=Lax,
  Secure in production), with CSRF tokens in every form and a strict Content Security
  Policy (only this site's files, no inline scripts or styles, no framing). Swagger UI
  is left out of the policy, since it builds its page with inline code.
- **Charts are SVG computed in Java** (`StatsCharts`): a line for installs, columns
  from a zero baseline for daily gains, tooltips with SVG `<title>`, the numbers also as
  a table. No JavaScript, so nothing conflicts with the policy, and the shapes are
  unit-tested.
- **Public by choice:** an account has a public page and badges only after choosing an
  address (`users.public_slug`, V6). Pages show installs only; reviews, alerts and the
  email stay private. Badges are SVG, cached for an hour.
- **Errors as pages:** the Problem Details handler now applies only to REST
  controllers; pages get `error.html`.
- **"Keep me signed in"** (added 2026-10-28): a box on the sign-in form keeps the
  browser signed in for 30 days with Spring Security's persistent remember-me tokens
  (`persistent_logins`, V7). Each use replaces the token; an old token coming back
  means a copy exists, so every remembered login of the account ends and the visit
  carries on signed out (`RememberedLogins`). Signing out deletes them.

## Consequences

- One deployable for API and website; a change to both ships in one PR.
- Sessions live in memory: a deploy signs everyone out, except browsers that ticked
  "Keep me signed in", which sign back in on their next visit. Spring Session (JDBC)
  would keep every session across restarts.
- Pages are tested with MockMvc like the API, including CSRF and redirects.

## Alternatives considered

- **A single-page app (React or Angular) on the API:** what many teams build, but a
  second project, build and deploy, and the token would live in the browser. Kept as
  an option for later; the API is ready for it.
- **JWT in a cookie for the website:** one mechanism instead of two, but then the API
  would need CSRF protection too, and signing out can't revoke a token.
- **A chart library (Chart.js):** more interactive, but a script to load and allow in
  the policy, for two simple charts.
