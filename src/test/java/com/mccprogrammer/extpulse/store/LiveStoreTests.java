package com.mccprogrammer.extpulse.store;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real stores, with the URLs and timeouts from application.yaml. Left out
 * of the normal build (the "live" tag); the live-stores workflow runs it weekly, so a
 * change on the stores' side fails loudly instead of leaving gaps in the data.
 */
@Tag("live")
@SpringBootTest(classes = { StoreConfiguration.class, MarketplaceSource.class, OpenVsxSource.class,
		ChromeWebStoreSource.class })
class LiveStoreTests {

	@Autowired
	private MarketplaceSource marketplace;

	@Autowired
	private OpenVsxSource openVsx;

	@Autowired
	private ChromeWebStoreSource chromeWebStore;

	@Test
	void marketplace() {
		List<DiscoveredListing> listings = this.marketplace.discover("MCCProgrammer");

		assertThat(listings).extracting(DiscoveredListing::externalId).contains("MCCProgrammer.diff-checker");
		assertThat(this.marketplace.fetchStats("MCCProgrammer.diff-checker").installs()).isPositive();
	}

	@Test
	void openVsx() {
		List<DiscoveredListing> listings = this.openVsx.discover("MCCProgrammer");

		assertThat(listings).extracting(DiscoveredListing::externalId).contains("MCCProgrammer/diff-checker");
		assertThat(this.openVsx.fetchStats("MCCProgrammer/diff-checker").installs()).isPositive();
	}

	@Test
	void chromeWebStore() {
		ListingStats stats = this.chromeWebStore.fetchStats("kpjlaobgikkhpngdkladodopdkigmdei");

		assertThat(stats.installs()).isPositive();
		assertThat(stats.ratingCount()).isPositive();
		assertThat(stats.version()).isNotBlank();
	}

}
