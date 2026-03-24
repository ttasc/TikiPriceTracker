package com.tiki.server;

import com.tiki.common.CryptoUtils;
import com.tiki.common.Product;
import java.security.KeyPair;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.ServerSocket;
import java.net.Socket;

public class TikiServer {
    private static final int PORT = 12345;
    private static final int MAX_POOL = 10;

    public static void main(String[] args) {
        try {
            // 1. Khởi tạo các thành phần cốt lõi
            DatabaseManager dbManager = new DatabaseManager();
            TikiService tikiService = new TikiService();
            KeyPair serverKeys = CryptoUtils.generateRSAKeyPair();
            
            System.out.println("--- TIKI TRACKER SERVER STARTED ---");

            // 2. Chạy luồng lắng nghe lệnh Console (Cập nhật thủ công)
            startAdminConsole(dbManager, tikiService);

            // 3. Chạy Server Socket để phục vụ Client
            ExecutorService clientPool = Executors.newFixedThreadPool(MAX_POOL);
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[Socket] Đang lắng nghe tại port " + PORT + "...");
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("[Socket] Kết nối mới: " + client.getRemoteSocketAddress());
                    clientPool.execute(new ClientHandler(client, serverKeys, dbManager, tikiService));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Luồng xử lý các lệnh từ admin server qua console
     */
    private static void startAdminConsole(DatabaseManager dbManager, TikiService tikiService) {
        Thread consoleThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
				System.out.println("[Admin] Nhập 'update' để cập nhật giá toàn bộ sản phẩm đang theo dõi.");
				System.out.println("[Admin] Nhập 'exit' để đóng server.");

				while (true) {
				    String command = scanner.nextLine().trim().toLowerCase();

				    if (command.equals("update")) {
				        System.out.println("[Admin] --- BẮT ĐẦU CẬP NHẬT GIÁ THỦ CÔNG ---");
				        List<String> trackedIds = dbManager.getTrackedProductIds();
				        
				        if (trackedIds.isEmpty()) {
				            System.out.println("[Admin] Không có sản phẩm nào đang được theo dõi.");
				            continue;
				        }

				        for (String id : trackedIds) {
				            try {
				                // Gọi API lấy thông tin mới nhất
				                Product p = tikiService.getProductDetail(id);
				                // Lưu vào lịch sử giá trong SQLite
				                dbManager.addPriceHistory(id, p.getPrice());
				                System.out.println("[Admin] Đã cập nhật SP ID: " + id + " | Giá: " + p.getPrice() + " VNĐ");
				                
				                // Nghỉ 1s giữa các lần gọi để tránh bị Tiki chặn
				                Thread.sleep(1000); 
				            } catch (Exception e) {
				                System.err.println("[Admin] Lỗi cập nhật SP " + id + ": " + e.getMessage());
				            }
				        }
				        System.out.println("[Admin] --- HOÀN TẤT CẬP NHẬT ---");

				    } else if (command.equals("exit")) {
				        System.out.println("[Admin] Đang đóng Server...");
				        System.exit(0);
				    } else {
				        System.out.println("[Admin] Lệnh không hợp lệ. Hãy dùng 'update' hoặc 'exit'.");
				    }
				}
			}
        });
        consoleThread.setDaemon(true); // Đảm bảo thread này tắt khi server chính tắt
        consoleThread.start();
    }
}
