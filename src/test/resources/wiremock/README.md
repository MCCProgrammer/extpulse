# Recorded store responses

What the real stores answered on 2026-10-26, served by WireMock in the store tests.

| Folder | Recorded from |
| --- | --- |
| `__files/marketplace/` | `POST https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery` (flags 769) |
| `__files/open-vsx/` | `GET https://open-vsx.org/api/{namespace}` and `/api/{namespace}/{name}` |
| `__files/chrome-web-store/` | `GET https://chromewebstore.google.com/detail/{id}?hl=en` |

The Chrome Web Store pages had their `<script>`, `<style>` and `<link>` tags removed
(from about 650 KB to under 130 KB each). The visible text, which is all
`ChromeWebStoreSource` reads, is unchanged.

When a store changes and the live-stores workflow fails, record the new response
here, update the source, and the tests show what changed.
