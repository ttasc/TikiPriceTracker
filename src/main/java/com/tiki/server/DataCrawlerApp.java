package com.tiki.server;

import java.util.List;

import com.tiki.common.Category;
import com.tiki.common.Product;

/**
 * Special Executable for High-Volume Data Collection (4000+ products) Logic: -
 * First run: Populate DB by crawling 26 categories x 4 pages. - Subsequent
 * runs: Update prices for all tracked products.
 */
public class DataCrawlerApp {

	public static void main(String[] args) {
		DatabaseManager dbManager = new DatabaseManager();
		TikiService tikiService = new TikiService();

		System.out.println("[CRAWLER] --- TIKI DATA COLLECTOR START ---");

		try {
			// Check current tracked count
			List<String> trackedIds = dbManager.getTrackedProductIds();

			if (trackedIds.size() < 4000) {
				System.out.println("[CRAWLER] Mode: INITIAL POPULATION (Targeting ~4000 items)");
				runInitialPopulation(dbManager, tikiService);
			} else {
				System.out.println("[CRAWLER] Mode: PERIODIC UPDATE (Updating " + trackedIds.size() + " items)");
				runPeriodicUpdate(trackedIds, dbManager, tikiService);
			}

		} catch (Exception e) {
			System.err.println("[CRAWLER] [CRITICAL] Operation failed: " + e.getMessage());
			e.printStackTrace();
		}

		System.out.println("[CRAWLER] --- TASK COMPLETED ---");
	}

	/**
	 * Mode 1: Fetch categories and crawl pages to fill the database
	 */
	private static void runInitialPopulation(DatabaseManager dbManager, TikiService tikiService) throws Exception {
		List<Category> categories = tikiService.getCategories();
		int totalSaved = 0;

		for (Category cat : categories) {
			System.out.println("[CRAWLER] Processing Category: " + cat.getText() + " (ID: " + cat.getId() + ")");

			for (int page = 1; page <= 4; page++) {
				try {
					String url = "https://tiki.vn/api/personalish/v1/blocks/listings?category=" + cat.getId() + "&page="
							+ page + "&limit=40";

					List<Product> products = tikiService.getProductList(url);

					for (Product p : products) {
						// Mark as tracked and save to DB
						p.setTracked(true);
						dbManager.saveProduct(p);
						dbManager.updateTracking(p.getId(), true);
						dbManager.addPriceHistory(p.getId(), p.getPrice());
						totalSaved++;
					}

					System.out.println("[INFO] Saved page " + page + " | Total so far: " + totalSaved);

					// Throttle to avoid 403 Forbidden
//					Thread.sleep(2000);

					if (totalSaved >= 4500) {
						System.out.println("[CRAWLER] Goal reached. Stopping population.");
						return;
					}
				} catch (Exception e) {
					System.err.println("[ERROR] Failed to crawl page " + page + " of category " + cat.getText());
					Thread.sleep(5000); // Wait longer on error
				}
			}
		}
	}

	/**
	 * Mode 2: Update prices for products already in the database
	 */
	private static void runPeriodicUpdate(List<String> trackedIds, DatabaseManager dbManager, TikiService tikiService) {
		int updatedCount = 0;
		int changeCount = 0;

		for (String id : trackedIds) {
			try {
				// Fetch latest data from Tiki
				Product currentData = tikiService.getProductDetail(id);
				long newPrice = currentData.getPrice();
				long lastPrice = dbManager.getLastPrice(id);

				// Only record if price has changed
				if (newPrice != lastPrice) {
					dbManager.addPriceHistory(id, newPrice);
					System.out.println("[DATABASE] Price CHANGE for ID " + id + ": " + lastPrice + " -> " + newPrice);
					changeCount++;
				}

				updatedCount++;
				if (updatedCount % 50 == 0) {
					System.out.println("[CRAWLER] Progress: " + updatedCount + "/" + trackedIds.size());
				}

				// Safety delay
//				Thread.sleep(1500);
			} catch (Exception e) {
				System.err.println("[ERROR] Could not update Product ID " + id + ": " + e.getMessage());
			}
		}
		System.out.println("[INFO] Update cycle finished. Changes detected: " + changeCount);
	}
}
