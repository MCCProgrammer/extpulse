# Recorded store responses

What the real stores answered on 2026-10-26, served by WireMock in the store tests.

| Folder | Recorded from |
| --- | --- |
| `__files/marketplace/` | `POST https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery` (flags 769); `reviews-*.json`: `GET .../publishers/{publisher}/extensions/{name}/reviews?count=...` |
| `__files/open-vsx/` | `GET https://open-vsx.org/api/{namespace}` and `/api/{namespace}/{name}`; `reviews-*.json`: `/api/{namespace}/{name}/reviews` |
| `__files/chrome-web-store/` | `GET https://chromewebstore.google.com/detail/{id}?hl=en` |

The Chrome Web Store pages had their `<script>`, `<style>` and `<link>` tags removed
(from about 650 KB to under 130 KB each). The visible text, which is all
`ChromeWebStoreSource` reads, is unchanged.

Review authors are anonymized: Marketplace display names and user IDs became
"Reviewer 1", "Reviewer 2"... and Open VSX logins became "reviewer1"... (avatars and
homepages removed); the reviewer's name in the reply was removed too. Ratings, text,
dates and versions are as recorded.

When a store changes and the live-stores workflow fails, record the new response
here, update the source, and the tests show what changed.
