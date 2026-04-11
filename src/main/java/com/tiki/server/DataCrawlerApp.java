package com.tiki.server;

import java.util.ArrayList;
import java.util.List;

import com.tiki.common.Category;
import com.tiki.common.Product;

public class DataCrawlerApp {

	public static void main(String[] args) {
		DatabaseManager dbManager = new DatabaseManager();
		TikiService tikiService = new TikiService();

		System.out.println("[SYSTEM] --- TIKI DATA COLLECTOR START ---");

		try {
			List<String> trackedIds = dbManager.getTrackedProductIds();

			if (trackedIds.size() < 4000) {
				System.out.println("[CRAWLER] Mode: INITIAL POPULATION (Targeting 4000+ items)");
				runInitialPopulation(dbManager, tikiService);
			} else {
				System.out.println("[CRAWLER] Mode: PERIODIC UPDATE (Total items to check: " + trackedIds.size() + ")");
				updateWithRetry(trackedIds, dbManager, tikiService);
			}

		} catch (Exception e) {
			System.err.println("[CRITICAL] System failure: " + e.getMessage());
			e.printStackTrace();
		}

		System.out.println("[SYSTEM] --- ALL TASKS COMPLETED ---");
	}

	private static void updateWithRetry(List<String> idsToUpdate, DatabaseManager dbManager, TikiService tikiService) {
		List<String> currentBatch = new ArrayList<>(idsToUpdate);
		int round = 1;

		while (!currentBatch.isEmpty()) {
			List<String> failedIds = new ArrayList<>();
			int totalInRound = currentBatch.size();
			int processedCount = 0;

			System.out.println("\n[RETRY] Starting Round " + round + " for " + totalInRound + " items.");

			for (String id : currentBatch) {
				processedCount++;
				try {
					Product currentData = tikiService.getProductDetail(id);
					long newPrice = currentData.getPrice();
					long lastPrice = dbManager.getLastPrice(id);

					String statusText;
					if (newPrice != lastPrice) {
						dbManager.addPriceHistory(id, newPrice);
						statusText = "CHANGED (" + lastPrice + " -> " + newPrice + ")";
					} else {
						statusText = "UNCHANGED (" + lastPrice + ")";
					}

					System.out.println(String.format("[CRAWLER] [Round %d] Progress: %d/%d | Errors: %d | ID: %s -> %s",
							round, processedCount, totalInRound, failedIds.size(), id, statusText));

//					Thread.sleep(1500); // Normal throttle

				} catch (Exception e) {
					failedIds.add(id);
					System.err.println(String.format(
							"[ERROR] [Round %d] Progress: %d/%d | Errors: %d | ID: %s -> FETCH FAILED (Retry later)",
							round, processedCount, totalInRound, failedIds.size(), id));

					try {
						Thread.sleep(5000); // Throttle on error
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}
			}

			if (!failedIds.isEmpty()) {
				System.out.println(String.format("\n[RETRY] Round %d finished. %d items failed out of %d.", round,
						failedIds.size(), totalInRound));
//				System.out.println("[RETRY] Waiting 30 seconds for IP to cool down before next round...");
//
//				try {
//					Thread.sleep(30000);
//				} catch (InterruptedException e) {
//					Thread.currentThread().interrupt();
//				}

				currentBatch = new ArrayList<>(failedIds);
				round++;
			} else {
				System.out.println("[INFO] Success: All products updated after " + round + " rounds.");
				currentBatch.clear();
			}
		}
	}

	private static void runInitialPopulation(DatabaseManager dbManager, TikiService tikiService) throws Exception {
		List<Category> categories = tikiService.getCategories();
		int totalSaved = 0;
		int populationErrorCount = 0;

		for (Category cat : categories) {
			System.out.println("\n[CRAWLER] Category: " + cat.getText());

			for (int page = 1; page <= 4; page++) {
				try {
					String url = "https://tiki.vn/api/personalish/v1/blocks/listings?category=" + cat.getId() + "&page="
							+ page + "&limit=40";

					List<Product> products = tikiService.getProductList(url);

					for (Product p : products) {
						dbManager.saveProduct(p);
						dbManager.updateTracking(p.getId(), true);
						dbManager.addPriceHistory(p.getId(), p.getPrice());
						totalSaved++;
					}

					System.out.println(
							String.format("[CRAWLER] [INITIAL] Progress: %d/4000+ | Errors: %d | Cat: %s (Page %d)",
									totalSaved, populationErrorCount, cat.getText(), page));

//					Thread.sleep(2000);

				} catch (Exception e) {
					populationErrorCount++;
					System.err.println("[ERROR] Initial population failed at " + cat.getText() + " page " + page);
					Thread.sleep(5000);
				}
			}
			if (totalSaved >= 4500) {
				System.out.println("[INFO] Reached data collection goal.");
				break;
			}
		}
	}
}
