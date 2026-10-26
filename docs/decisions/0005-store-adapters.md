# 0005. Store adapters, and reading the Chrome Web Store page

**Date:** 2026-10-26 · **Status:** accepted

## Context

ExtPulse reads three stores. The VS Code Marketplace has a public but undocumented
query API. Open VSX has a documented REST API. The Chrome Web Store has no public API
for install counts or ratings; its publishing API only covers the developer's own
uploads and needs OAuth.

## Decision

- One `StoreSource` implementation per store, in the `store` package. They are the
  only code that talks to the stores, and they return plain records (`ListingStats`,
  `DiscoveredListing`), so nothing else depends on how a store answers.
- The Chrome Web Store is read from its public item page with jsoup, matching the
  visible English text ("37 users", "5 out of 5", "2 ratings"), never the generated
  CSS class names. If the text isn't found, the adapter throws instead of saving a zero.
- Tests use WireMock with responses recorded from the real stores. A separate test
  class tagged `live` calls the real stores and runs weekly in GitHub Actions.
- Retries and request pacing belong to the daily collection (step 4), not to the
  adapters: an adapter makes one attempt with a timeout and reports what happened.

## Consequences

- Adding a store (for example, JetBrains Marketplace) is one new class.
- The Chrome numbers depend on Google's page. A change breaks the parsing loudly: the
  weekly live run fails, and the collection records the error instead of bad data.
- The Chrome Web Store can't be searched by publisher, so Chrome listings are added by
  their item ID.
- The live tests found what recorded responses couldn't: the store redirects to a URL
  with raw UTF-8 in it, which the JDK HTTP client can't follow, so the Chrome adapter
  follows redirects itself.

## Alternatives considered

- **A headless browser (Playwright, Selenium):** sees the page as a person does, but
  adds a browser to the deployment for three numbers that are already in the HTML.
- **Third-party stats sites:** another dependency that scrapes the same page, with its
  own delays and terms.
- **Matching CSS selectors:** shorter code, but the class names are generated and change
  without notice.
