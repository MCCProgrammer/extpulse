package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The Chrome Web Store has no public API for stats, so this reads the item's page. It
 * matches the visible English text ("37 users", "5 out of 5", "2 ratings") rather than
 * CSS classes, which are generated and change with every release of the site. When the
 * page changes anyway, this throws a {@link StoreException} instead of guessing; the
 * weekly live-stores workflow is there to notice.
 * <p>
 * Reviews are on another page, /detail/{id}/reviews, which carries them as data for its
 * scripts: an {@code AF_initDataCallback} block of JSON arrays, the same data the page
 * shows. It lists the 10 latest reviews in the page's language, so each configured
 * language is one request.
 */
@Component
class ChromeWebStoreSource implements StoreSource {

	private static final Pattern ITEM_ID = Pattern.compile("[a-p]{32}");

	private static final Pattern USERS = Pattern.compile("(\\d[\\d,]*)\\+? users?\\b");

	private static final Pattern RATING = Pattern
		.compile("(\\d(?:\\.\\d+)?) out of 5 (No ratings|(\\d[\\d,]*) ratings?)");

	private static final Pattern VERSION = Pattern.compile("Version (\\S+) Updated");

	// The page's data requests: the reviews are the one asking for the item and a page
	// size, as in 'ds:1' : {id:'x1DgCd',request:["{item}",[10],...]}.
	private static final Pattern REVIEWS_REQUEST = Pattern
		.compile("'(ds:\\d+)'\\s*:\\s*\\{id:'[^']+',request:\\[\"([a-p]{32})\",\\[\\d+]");

	// Where each field of a review is in its array.
	private static final int REVIEW_ID = 0;

	private static final int REVIEW_AUTHOR = 1;

	private static final int REVIEW_RATING = 2;

	private static final int REVIEW_TEXT = 3;

	private static final int REVIEW_UPDATED = 5;

	private static final int REVIEW_REPLY = 8;

	private static final int REVIEW_VERSION = 11;

	private static final int REPLY_TEXT = 2;

	private static final int MAX_REVIEWS = 100;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// From the EU there are three: cookie consent, back to the store, then the item's
	// name.
	private static final int MAX_REDIRECTS = 5;

	private final RestClient http;

	private final String baseUrl;

	private final List<String> reviewLanguages;

	ChromeWebStoreSource(StoreProperties properties) {
		this.http = StoreHttp.client(properties.chromeWebStoreUrl(), properties);
		this.baseUrl = properties.chromeWebStoreUrl().toString();
		this.reviewLanguages = properties.chromeWebStoreReviewLanguages();
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
	 * The latest reviews in each configured language, newest first. Ratings without a
	 * comment aren't listed by the store, so they aren't here either.
	 */
	@Override
	public List<StoreReview> fetchReviews(String externalId) {
		checkItemId(externalId);
		Map<String, StoreReview> reviews = new LinkedHashMap<>();
		for (String language : this.reviewLanguages) {
			for (StoreReview review : parseReviews(externalId, page(externalId, "/reviews?hl=" + language))) {
				reviews.putIfAbsent(review.externalId(), review);
			}
		}
		return reviews.values()
			.stream()
			.sorted(Comparator.comparing(StoreReview::postedAt).reversed())
			.limit(MAX_REVIEWS)
			.toList();
	}

	@Override
	public ListingStats fetchStats(String externalId) {
		checkItemId(externalId);
		return parse(externalId, page(externalId, "?hl=en"));
	}

	private static void checkItemId(String externalId) {
		if (!ITEM_ID.matcher(externalId).matches()) {
			throw new IllegalArgumentException("Not a Chrome Web Store item ID: " + externalId);
		}
	}

	private String page(String externalId, String rest) {
		URI uri = URI.create(this.baseUrl + "/detail/" + externalId + rest);
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

	private List<StoreReview> parseReviews(String externalId, String html) {
		Document page = Jsoup.parse(html);
		if (page.selectFirst("h1") == null) {
			throw new ListingNotFoundException(store(), externalId);
		}
		JsonNode data = reviewsData(page, externalId);
		JsonNode reviews = data.path(1);
		if (!reviews.isArray()) {
			// [[]]: the store has no review with a comment in this language.
			return List.of();
		}
		return reviews.valueStream().map((review) -> review(externalId, review)).toList();
	}

	/**
	 * The JSON the page holds for its reviews: [next page token, [review, ...], total].
	 */
	private JsonNode reviewsData(Document page, String externalId) {
		String key = null;
		for (Element script : page.select("script")) {
			Matcher request = REVIEWS_REQUEST.matcher(script.data());
			if (request.find() && request.group(2).equals(externalId)) {
				key = request.group(1);
			}
		}
		String callback = "AF_initDataCallback({key: '" + key + "'";
		for (Element script : page.select("script")) {
			String code = script.data();
			int start = code.indexOf("data:");
			int end = code.lastIndexOf(", sideChannel:");
			if (key != null && code.startsWith(callback) && start > 0 && end > start) {
				try {
					return JSON.readTree(code.substring(start + "data:".length(), end));
				}
				catch (JacksonException ex) {
					throw new StoreException(store(), "unreadable reviews on the page of " + externalId, ex);
				}
			}
		}
		throw new StoreException(store(), "no reviews data on the page of " + externalId + "; has the page changed?");
	}

	private StoreReview review(String externalId, JsonNode review) {
		String id = review.path(REVIEW_ID).asString("");
		int rating = review.path(REVIEW_RATING).asInt(0);
		JsonNode updated = review.path(REVIEW_UPDATED);
		if (id.isEmpty() || rating < 1 || rating > 5 || !updated.path(0).isNumber()) {
			throw new StoreException(store(),
					"a review on the page of " + externalId + " isn't as expected: " + review);
		}
		return new StoreReview(id, rating, textOrNull(review.path(REVIEW_TEXT)),
				textOrNull(review.path(REVIEW_AUTHOR).path(0)), textOrNull(review.path(REVIEW_VERSION)),
				Instant.ofEpochSecond(updated.path(0).asLong(), updated.path(1).asLong(0)),
				textOrNull(review.path(REVIEW_REPLY).path(REPLY_TEXT)));
	}

	private static String textOrNull(JsonNode node) {
		return (node.isString() && !node.asString().isBlank()) ? node.asString() : null;
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
