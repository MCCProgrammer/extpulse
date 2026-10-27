package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The Chrome Web Store has no public API for stats, so this reads the item's page. It
 * matches the visible English text ("37 users", "5 out of 5", "2 ratings") rather than
 * CSS classes, which are generated and change with every release of the site. When the
 * page changes anyway, this throws a {@link StoreException} instead of guessing; the
 * weekly live-stores workflow is there to notice.
 */
@Component
class ChromeWebStoreSource implements StoreSource {

	private static final Pattern ITEM_ID = Pattern.compile("[a-p]{32}");

	private static final Pattern USERS = Pattern.compile("(\\d[\\d,]*)\\+? users?\\b");

	private static final Pattern RATING = Pattern
		.compile("(\\d(?:\\.\\d+)?) out of 5 (No ratings|(\\d[\\d,]*) ratings?)");

	private static final Pattern VERSION = Pattern.compile("Version (\\S+) Updated");

	// From the EU there are three: cookie consent, back to the store, then the item's
	// name.
	private static final int MAX_REDIRECTS = 5;

	private final RestClient http;

	private final String baseUrl;

	ChromeWebStoreSource(StoreProperties properties) {
		this.http = StoreHttp.client(properties.chromeWebStoreUrl(), properties);
		this.baseUrl = properties.chromeWebStoreUrl().toString();
	}

	@Override
	public Store store() {
		return Store.CHROME_WEB_STORE;
	}

	/**
	 * Always empty: the store can't be searched by publisher, so Chrome listings are
	 * added by their item ID.
	 */
	@Override
	public List<DiscoveredListing> discover(String publisher) {
		return List.of();
	}

	/**
	 * Always empty for now. The reviews are on a separate page (/detail/{id}/reviews)
	 * with no stable ID per review; reading them is left for later.
	 */
	@Override
	public List<StoreReview> fetchReviews(String externalId) {
		return List.of();
	}

	@Override
	public ListingStats fetchStats(String externalId) {
		if (!ITEM_ID.matcher(externalId).matches()) {
			throw new IllegalArgumentException("Not a Chrome Web Store item ID: " + externalId);
		}
		return parse(externalId, page(externalId));
	}

	private String page(String externalId) {
		URI uri = URI.create(this.baseUrl + "/detail/" + externalId + "?hl=en");
		try {
			for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
				URI current = uri;
				Response response = this.http.get().uri(current).exchange((request, answer) -> {
					if (answer.getStatusCode().is3xxRedirection()) {
						return new Response(null, redirectTarget(current, answer.getHeaders().getFirst("Location")));
					}
					if (answer.getStatusCode().isError()) {
						throw new StoreException(store(),
								"page of " + externalId + " answered " + answer.getStatusCode());
					}
					return new Response(new String(answer.getBody().readAllBytes(), StandardCharsets.UTF_8), null);
				}, true);
				if (response.html() != null) {
					return response.html();
				}
				uri = response.redirect();
			}
		}
		catch (RestClientException ex) {
			throw new StoreException(store(), "page of " + externalId + " failed", ex);
		}
		throw new StoreException(store(),
				"page of " + externalId + " redirected more than " + MAX_REDIRECTS + " times");
	}

	/**
	 * Where a redirect points. The store answers /detail/{id} with a 301 to
	 * /detail/{item-name}/{id}, and the name goes out as raw UTF-8 ("director’s-cut").
	 * The JDK reads header bytes as ISO-8859-1, which turns that into characters a URI
	 * can't hold; this puts the bytes back together and percent-encodes them.
	 */
	static URI redirectTarget(URI from, String location) {
		if (location == null) {
			throw new IllegalStateException("Redirect from " + from + " without a Location header");
		}
		boolean readAsLatin1 = location.chars().allMatch(c -> c <= 0xFF);
		String decoded = readAsLatin1
				? new String(location.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8) : location;
		StringBuilder encoded = new StringBuilder();
		for (byte b : decoded.getBytes(StandardCharsets.UTF_8)) {
			if (b > 0x20 && b < 0x7F) {
				encoded.append((char) b);
			}
			else {
				encoded.append('%').append(HexFormat.of().withUpperCase().toHexDigits(b));
			}
		}
		return from.resolve(encoded.toString());
	}

	private ListingStats parse(String externalId, String html) {
		Document page = Jsoup.parse(html);
		// An unknown ID still answers 200, with a generic page that has no item title.
		if (page.selectFirst("h1") == null) {
			throw new ListingNotFoundException(store(), externalId);
		}
		String text = page.text();
		Matcher users = find(USERS, text, externalId, "user count");
		Matcher rating = find(RATING, text, externalId, "rating");
		boolean rated = rating.group(3) != null;
		Matcher version = VERSION.matcher(text);
		return new ListingStats(number(users.group(1)), rated ? new BigDecimal(rating.group(1)) : null,
				rated ? (int) number(rating.group(3)) : 0, version.find() ? version.group(1) : null);
	}

	private Matcher find(Pattern pattern, String text, String externalId, String what) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			throw new StoreException(store(),
					"no " + what + " on the page of " + externalId + "; has the page changed?");
		}
		return matcher;
	}

	private static long number(String digits) {
		return Long.parseLong(digits.replace(",", ""));
	}

	private record Response(String html, URI redirect) {
	}

}
