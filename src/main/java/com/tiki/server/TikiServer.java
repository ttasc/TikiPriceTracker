package com.tiki.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

			System.out.println("[INFO] --- TIKI TRACKER SERVER STARTED ---");

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
}
