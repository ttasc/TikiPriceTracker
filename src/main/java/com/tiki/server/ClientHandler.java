// File: src/main/java/com/tiki/server/ClientHandler.java
package com.tiki.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiki.common.Category;
import com.tiki.common.CryptoUtils;
import com.tiki.common.Pair;
import com.tiki.common.Product;

public class ClientHandler implements Runnable {
	private Socket socket;
	private KeyPair serverKeyPair;
	private SecretKey sessionKey;
	private DatabaseManager dbManager;
	private TikiService tikiService;
	private Gson gson = new Gson();

	public ClientHandler(Socket socket, KeyPair keyPair, DatabaseManager dbManager, TikiService tikiService) {
		this.socket = socket;
		this.serverKeyPair = keyPair;
		this.dbManager = dbManager;
		this.tikiService = tikiService;
	}

	@Override
	public void run() {
		try (DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

			// --- BƯỚC 1: Handshake RSA/AES (Giữ nguyên như trước) ---
			byte[] pubKeyBytes = serverKeyPair.getPublic().getEncoded();
			out.writeInt(pubKeyBytes.length);
			out.write(pubKeyBytes);
			out.flush();

			int encKeyLen = in.readInt();
			byte[] encKeyBytes = new byte[encKeyLen];
			in.readFully(encKeyBytes);
			byte[] aesKeyBytes = CryptoUtils.decryptRSA(encKeyBytes, serverKeyPair.getPrivate());
			this.sessionKey = new SecretKeySpec(aesKeyBytes, "AES");

			// --- BƯỚC 2: Vòng lặp xử lý Request ---
			while (true) {
				String encryptedReq = in.readUTF();
				String jsonReq = CryptoUtils.decryptAES(encryptedReq, sessionKey);
				JsonObject request = JsonParser.parseString(jsonReq).getAsJsonObject();
				System.out.println(request);
				String action = request.get("action").getAsString();

				String responseJson = "";

				switch (action) {
				case "GET_CATEGORIES":
					List<Category> cats = tikiService.getCategories();
					responseJson = gson.toJson(cats);
					break;

				case "GET_PRODUCTS":
					String url = request.get("url").getAsString();
					List<Product> products = tikiService.getProductList(url);
					for (Product p : products) {
						p.setTracked(dbManager.isProductTracked(p.getId()));
					}
					responseJson = gson.toJson(products);
					break;

				case "GET_TRACKED_LIST":
					List<Product> trackedList = dbManager.getTrackedProducts();
					responseJson = gson.toJson(trackedList);
					break;

				case "TOGGLE_TRACK":
					String pId = request.get("productId").getAsString();
					boolean shouldTrack = request.get("isTracked").getAsBoolean();

					if (shouldTrack) {
						// Nếu bật theo dõi: Lấy thông tin thật để lưu vào DB (nếu chưa có)
						Product pDetail = tikiService.getProductDetail(pId);
						dbManager.saveProduct(pDetail);
						dbManager.updateTracking(pId, true);
						// Lưu mốc giá đầu tiên
						dbManager.addPriceHistory(pId, pDetail.getPrice());
						System.out.println("[Server] Bắt đầu theo dõi: " + pId);
					} else {
						// Nếu tắt: Chỉ cập nhật flag trong DB về 0
						dbManager.updateTracking(pId, false);
						System.out.println("[Server] Hủy theo dõi: " + pId);
					}
					responseJson = "{\"status\":\"success\"}";
					break;

				case "GET_DETAIL":
					String id = request.get("productId").getAsString();

					// 1. Kiểm tra xem sản phẩm có đang được theo dõi không
					boolean tracked = dbManager.isProductTracked(id);

					// 2. Chỉ lấy lịch sử nếu đang theo dõi
					List<Pair<String, Long>> history = tracked ? dbManager.getPriceHistory(id)
							: new java.util.ArrayList<>();

					// 3. Luôn lấy reviews text
					List<String> reviews = tikiService.getProductReviews(id);

					JsonObject detail = new JsonObject();
					detail.addProperty("isTracked", tracked); // Gửi thêm flag này về
					detail.add("history", gson.toJsonTree(history));
					detail.add("reviews", gson.toJsonTree(reviews));

					responseJson = gson.toJson(detail);
					break;
				}

				// Gửi phản hồi bảo mật
				out.writeUTF(CryptoUtils.encryptAES(responseJson, sessionKey));
				out.flush();
			}
		} catch (Exception e) {
			System.out.println("Client ngắt kết nối.");
		}
	}
}
