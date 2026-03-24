package com.tiki.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.security.KeyPair;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiki.common.CryptoUtils;
import com.tiki.common.Product;

public class ClientHandler implements Runnable {
	private Socket socket;
	private KeyPair serverKeyPair;
	private SecretKey sessionKey; // Khóa AES dùng chung
    private DatabaseManager dbManager;
    private TikiService tikiService;

    public ClientHandler(Socket socket, KeyPair keyPair, DatabaseManager dbManager, TikiService tikiService) {
        this.socket = socket;
        this.serverKeyPair = keyPair;
        this.dbManager = dbManager;
        this.setTikiService(tikiService);
    }

	@Override
	public void run() {
		try (DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
			// --- BƯỚC 1: Gửi RSA Public Key cho Client ---
			byte[] pubKeyBytes = serverKeyPair.getPublic().getEncoded();
			out.writeInt(pubKeyBytes.length);
			out.write(pubKeyBytes);
			out.flush();

			// --- BƯỚC 2: Nhận AES Key đã mã hóa từ Client ---
			int encryptedKeyLen = in.readInt();
			byte[] encryptedKeyBytes = new byte[encryptedKeyLen];
			in.readFully(encryptedKeyBytes);

			// --- BƯỚC 3: Giải mã lấy AES Key ---
			byte[] decryptedKeyBytes = CryptoUtils.decryptRSA(encryptedKeyBytes, serverKeyPair.getPrivate());
			this.sessionKey = new SecretKeySpec(decryptedKeyBytes, "AES");
			System.out.println("Handshake thành công với: " + socket.getInetAddress());

			// --- BƯỚC 4: Giao tiếp bảo mật bằng AES ---
			while (true) {
				String encryptedMsg = in.readUTF();
				String plainText = CryptoUtils.decryptAES(encryptedMsg, sessionKey);
				System.out.println("Client nói: " + plainText);

				// Giả sử Client gửi JSON: {"action":"TRACK", "productId":"277777809",
				// "name":"...", "price": 500000, "thumb":"..."}
				JsonObject json = JsonParser.parseString(plainText).getAsJsonObject();
				String action = json.get("action").getAsString();

				if (action.equals("TRACK")) {
					String id = json.get("productId").getAsString();
					String name = json.get("name").getAsString();
					String thumb = json.get("thumb").getAsString();
					long price = json.get("price").getAsLong();

					// 1. Lưu sản phẩm vào bảng products
					this.dbManager.saveProduct(new Product(id, name, price, thumb, true));
					// 2. Cập nhật trạng thái theo dõi
					this.dbManager.updateTracking(id, true);
					// 3. Lưu giá hiện tại làm mốc đầu tiên
					this.dbManager.addPriceHistory(id, price);

					out.writeUTF(CryptoUtils.encryptAES("Đã bắt đầu theo dõi giá!", sessionKey));
				}
				String response = "Server xử lý xong: " + plainText;
				out.writeUTF(CryptoUtils.encryptAES(response, sessionKey));
				out.flush();
			}

		} catch (Exception e) {
			System.err.println("Kết nối bị ngắt: " + e.getMessage());
		}
	}

	public TikiService getTikiService() {
		return tikiService;
	}

	public void setTikiService(TikiService tikiService) {
		this.tikiService = tikiService;
	}
}
