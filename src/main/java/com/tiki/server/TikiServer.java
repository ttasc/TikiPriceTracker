package com.tiki.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
	private static final int PORT = 30000;
	private static final int BPORT= 30255;
	private static final int MAX_POOL = 10;

	public static void main(String[] args) {
		boolean disableAutoUpdate = false;
		boolean enableConsole = false;

		for (String arg : args) {
			if (arg.equalsIgnoreCase("--disable-auto-update")) disableAutoUpdate = true;
			else if (arg.equalsIgnoreCase("--console")) enableConsole = true;
		}

		try {
			System.out.println("[SYSTEM] Initializing core components...");

			DatabaseManager dbManager = new DatabaseManager();
			TikiService tikiService = new TikiService();
			KeyPair serverKeys = CryptoUtils.generateRSAKeyPair();

			System.out.println("[SYSTEM] Security keys generated.");
			System.out.println("[SYSTEM] Database connection established.");
			System.out.println("[SYSTEM] --- TIKI TRACKER SERVER IS READY ---");

			// Start the background processes
			if (!disableAutoUpdate)
				startAutoPriceUpdater(dbManager, tikiService); // auto update product's price

		    if (enableConsole)
		    	startAdminConsole(dbManager, tikiService); // Stdin/out console for admin

		    startDiscoveryBeacon(); // Broadcast server ip

			// Start Socket Server
			ExecutorService clientPool = Executors.newFixedThreadPool(MAX_POOL);
			try (ServerSocket serverSocket = new ServerSocket(PORT)) {
				System.out.println("[NETWORK] Server is listening on port: " + PORT);

				while (true) {
					Socket client = serverSocket.accept();
					System.out.println("[NETWORK] New client connected: " + client.getRemoteSocketAddress());

					clientPool.execute(new ClientHandler(client, serverKeys, dbManager, tikiService));
				}
			}
		} catch (Exception e) {
			System.err.println("[CRITICAL] Server failed to start: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private static void startDiscoveryBeacon() {
	    Thread beaconThread = new Thread(() -> {
	        try (DatagramSocket socket = new DatagramSocket()) {
	            socket.setBroadcast(true);
	            String message = "TIKI_SERVER_DISCOVERY:"+PORT;
	            byte[] buffer = message.getBytes();

	            System.out.println("[SYSTEM] Discovery Beacon started (UDP Broadcast).");
	            
	            while (true) {
	                DatagramPacket packet = new DatagramPacket(
	                    buffer, buffer.length, 
	                    InetAddress.getByName("255.255.255.255"), BPORT
	                );
	                socket.send(packet);
	                Thread.sleep(3000);
	            }
	        } catch (Exception e) {
	            System.err.println("[ERROR] Discovery Beacon failed: " + e.getMessage());
	        }
	    });
	    beaconThread.setDaemon(true);
	    beaconThread.start();
	}

	private static void startAdminConsole(DatabaseManager dbManager, TikiService tikiService) {
		Thread consoleThread = new Thread(() -> {
			try (Scanner scanner = new Scanner(System.in)) {
				System.out.println("[ADMIN] Console ready. Commands: 'update' (manual sync), 'exit' (stop)");

				while (true) {
					System.out.print("> ");
					String command = scanner.nextLine().trim().toLowerCase();

					if (command.equals("update")) {
						System.out.println("[ADMIN] Manual price update triggered.");
						List<String> trackedIds = dbManager.getTrackedProductIds();

						if (trackedIds.isEmpty()) {
							System.out.println("[ADMIN] Warning: No products are currently being tracked.");
							continue;
						}

						int updateCount = 0;
						for (String id : trackedIds) {
							try {
								Product p = tikiService.getProductDetail(id);
								dbManager.addPriceHistory(id, p.getPrice());
								System.out.println("[ADMIN] [LOG] Updated ID: " + id + " | Price: " + p.getPrice());
								updateCount++;
								Thread.sleep(1000); // Rate limit protection
							} catch (Exception e) {
								System.err.println("[ADMIN] [ERROR] Failed to update ID " + id + ": " + e.getMessage());
							}
						}
						System.out.println("[ADMIN] Manual update finished. Total updated: " + updateCount);

					} else if (command.equals("exit")) {
						System.out.println("[SYSTEM] Shutting down server...");
						System.exit(0);
					} else {
						System.out.println("[ADMIN] Warning: Unknown command. Available: 'update', 'exit'");
					}
				}
			}
		});
		consoleThread.setDaemon(true);
		consoleThread.start();
	}

	@SuppressWarnings("unused")
	private static void startAutoPriceUpdater(DatabaseManager dbManager, TikiService tikiService) {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

		Runnable updateTask = () -> {
			System.out.println("\n[SCHEDULER] Auto-update task started at: " + new java.util.Date());
			List<String> trackedIds = dbManager.getTrackedProductIds();

			if (trackedIds.isEmpty()) {
				System.out.println("[SCHEDULER] Skip: No products found in tracking list.");
				return;
			}

			int changedCount = 0;
			for (String id : trackedIds) {
				try {
					Product currentProduct = tikiService.getProductDetail(id);
					long newPrice = currentProduct.getPrice();
					long lastPrice = dbManager.getLastPrice(id);

					if (newPrice != lastPrice) {
						dbManager.addPriceHistory(id, newPrice);
						System.out.println(
								"[SCHEDULER] Change detected for ID " + id + ": " + lastPrice + " -> " + newPrice);
						changedCount++;
					} else {
						System.out.println("[SCHEDULER] ID " + id + ": Price unchanged (" + lastPrice + ").");
					}

					Thread.sleep(2000);
				} catch (Exception e) {
					System.err.println("[SCHEDULER] [ERROR] Failed to process ID " + id + ": " + e.getMessage());
				}
			}
			System.out.println("[SCHEDULER] Task finished. Changes recorded: " + changedCount);
		};

		// Run immediately, then repeat every 3 hours
		scheduler.scheduleAtFixedRate(updateTask, 0, 3, TimeUnit.HOURS);
		System.out.println("[SYSTEM] Scheduler initialized: Auto-update set to 3-hour intervals.");
	}
}
