package com.tiki.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.tiki.common.CryptoUtils;

public class TikiServer {
	private static final int PORT = 12345;
	private static final int MAX_CLIENTS = 10;

	public static void main(String[] args) {
		KeyPair serverKeys;
		try {
            DatabaseManager dbManager = new DatabaseManager();
			serverKeys = CryptoUtils.generateRSAKeyPair();

			ExecutorService executor = Executors.newFixedThreadPool(MAX_CLIENTS);

			try (ServerSocket serverSocket = new ServerSocket(PORT)) {
				System.out.println("Tiki Price Tracker Server đang chạy trên port " + PORT + "...");
				System.out.println("Đang chờ kết nối từ Client...");

				while (true) {
					Socket clientSocket = serverSocket.accept();
					System.out.println("Có kết nối mới từ: " + clientSocket.getRemoteSocketAddress());

					// Giao cho một luồng trong pool xử lý
					executor.execute(new ClientHandler(clientSocket, serverKeys, dbManager));
				}
			} catch (IOException e) {
				System.err.println("Lỗi Server: " + e.getMessage());
			} finally {
				executor.shutdown();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}