package com.tiki.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tiki.common.CryptoUtils;
import com.tiki.common.Product;

public class TikiServer {
	private static final int PORT = 12345;
	private static final int MAX_POOL = 10;

	public static void main(String[] args) {
		try {
			DatabaseManager dbManager = new DatabaseManager();
			TikiService tikiService = new TikiService();
			KeyPair serverKeys = CryptoUtils.generateRSAKeyPair();

			System.out.println("[System] Initialize: Security keys generated.");
			System.out.println("[System] Initialize: Database connection established.");
			System.out.println("[INFO] --- TIKI TRACKER SERVER STARTED ---");

			startAutoPriceUpdater(dbManager, tikiService);

			startAdminConsole(dbManager, tikiService);

			ExecutorService clientPool = Executors.newFixedThreadPool(MAX_POOL);
			try (ServerSocket serverSocket = new ServerSocket(PORT)) {
				System.out.println("[INFO] Socket server is listening on port: " + PORT);

				while (true) {
					Socket client = serverSocket.accept();
					System.out.println("[INFO] New client connection established: " + client.getRemoteSocketAddress());
					clientPool.execute(new ClientHandler(client, serverKeys, dbManager, tikiService));
				}
			}
		} catch (Exception e) {
			System.err.println("[CRITICAL] Server failed to start: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static void startAdminConsole(DatabaseManager dbManager, TikiService tikiService) {
		Thread consoleThread = new Thread(() -> {
			try (Scanner scanner = new Scanner(System.in)) {
				System.out.println("[ADMIN] Available commands: 'update' (refresh prices), 'exit' (stop server)");

				while (true) {
					System.out.print("> ");
					String command = scanner.nextLine().trim().toLowerCase();

					if (command.equals("update")) {
						System.out.println("[ADMIN] Starting manual price update process...");
						List<String> trackedIds = dbManager.getTrackedProductIds();

						if (trackedIds.isEmpty()) {
							System.out.println("[WARN] No products are currently being tracked.");
							continue;
						}

						for (String id : trackedIds) {
							try {
								Product p = tikiService.getProductDetail(id);
								dbManager.addPriceHistory(id, p.getPrice());
								System.out.println("[LOG] Updated Product ID: " + id + " | New Price: " + p.getPrice());

								Thread.sleep(1000);
							} catch (Exception e) {
								System.err.println("[ERROR] Failed to update product ID " + id + ": " + e.getMessage());
							}
						}
						System.out.println("[ADMIN] Manual price update completed.");

					} else if (command.equals("exit")) {
						System.out.println("[INFO] Shutting down server...");
						System.exit(0);
					} else {
						System.out.println("[WARN] Unknown command. Use 'update' or 'exit'.");
					}
				}
			}
		});
		consoleThread.setDaemon(true);
		consoleThread.start();
	}

	private static void startAutoPriceUpdater(DatabaseManager dbManager, TikiService tikiService) {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

		Runnable updateTask = () -> {
			System.out.println("\n[Auto-Update] Task started at: " + new java.util.Date());
			List<String> trackedIds = dbManager.getTrackedProductIds();

			if (trackedIds.isEmpty()) {
				System.out.println("[Auto-Update] Skip: No products are currently being tracked.");
				return;
			}

			int changedCount = 0;
			for (String id : trackedIds) {
				try {
					// 1. Lấy giá hiện tại từ Tiki API
					Product currentProduct = tikiService.getProductDetail(id);
					long newPrice = currentProduct.getPrice();

					// 2. Lấy giá cuối cùng trong Database
					long lastPrice = dbManager.getLastPrice(id);

					// 3. So sánh: Chỉ lưu nếu giá thay đổi hoặc chưa có lịch sử
					if (newPrice != lastPrice) {
						dbManager.addPriceHistory(id, newPrice);
						System.out.println(
								"[Auto-Update] Change detected for ID " + id + ": " + lastPrice + " -> " + newPrice);
						changedCount++;
					} else
						System.out.println(
								"[Auto-Update] ID " + id + ": Price unchanged (" + lastPrice + "). Record skipped.");

//					Thread.sleep(2000);
				} catch (Exception e) {
                    System.err.println("[Auto-Update] Failed to process ID " + id + ": " + e.getMessage());
				}
			}
            System.out.println("[Auto-Update] Cycle finished. Total changes recorded: " + changedCount);
		};

		// Lập lịch: Chạy ngay lập tức lần đầu, sau đó lặp lại mỗi 3 tiếng
		// 0: Initial delay (chạy ngay)
		// 3: Period (khoảng cách giữa các lần chạy)
		// TimeUnit.HOURS: Đơn vị thời gian
		scheduler.scheduleAtFixedRate(updateTask, 0, 3, TimeUnit.HOURS);
        System.out.println("[System] Scheduler: Auto-update enabled (Interval: 3 Hours).");
	}
}
