package com.tiki.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

			// --- SECURITY HANDSHAKE ---
			byte[] pubKeyBytes = serverKeyPair.getPublic().getEncoded();
			out.writeInt(pubKeyBytes.length);
			out.write(pubKeyBytes);
			out.flush();

			int encKeyLen = in.readInt();
			byte[] encKeyBytes = new byte[encKeyLen];
			in.readFully(encKeyBytes);
			byte[] aesKeyBytes = CryptoUtils.decryptRSA(encKeyBytes, serverKeyPair.getPrivate());
			this.sessionKey = new SecretKeySpec(aesKeyBytes, "AES");

			System.out.println("[SECURITY] Hybrid handshake successful with: " + socket.getRemoteSocketAddress());

			// --- REQUEST PROCESSING LOOP ---
			while (true) {
				String encryptedReq = in.readUTF();
				String jsonReq = CryptoUtils.decryptAES(encryptedReq, sessionKey);
				JsonObject request = JsonParser.parseString(jsonReq).getAsJsonObject();

				String action = request.get("action").getAsString();
				System.out.println("[REQUEST] Action: " + action + " | From: " + socket.getRemoteSocketAddress());

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
					int trackedPage = request.has("page") ? request.get("page").getAsInt() : 1;
					int limitPerRequest = request.has("limit") ? request.get("limit").getAsInt() : 50;
					System.out.println("[REQUEST] [LOG] Fetching tracked products | Page: " + trackedPage + " | Limit: "
							+ limitPerRequest);

					List<Product> trackedList = dbManager.getTrackedProducts(trackedPage, limitPerRequest);
					responseJson = gson.toJson(trackedList);
					break;

				case "GET_BY_IDS":
				    JsonArray idArray = request.getAsJsonArray("ids");
				    List<String> idList = new ArrayList<>();
				    for (JsonElement e : idArray) idList.add(e.getAsString());
				    
				    List<Product> result = dbManager.getProductsByIds(idList);
				    responseJson = gson.toJson(result);
				    break;

				case "TOGGLE_TRACK":
					String pId = request.get("productId").getAsString();
					boolean shouldTrack = request.get("isTracked").getAsBoolean();

					if (shouldTrack) {
						// Sync from Tiki and save to local DB
						Product pDetail = tikiService.getProductDetail(pId);
						dbManager.saveProduct(pDetail);
						dbManager.updateTracking(pId, true);
						dbManager.addPriceHistory(pId, pDetail.getPrice());
						System.out.println("[DATABASE] Tracking ENABLED for ID: " + pId);
					} else {
						dbManager.updateTracking(pId, false);
						System.out.println("[DATABASE] Tracking DISABLED for ID: " + pId);
					}
					responseJson = "{\"status\":\"success\"}";
					break;

				case "GET_DETAIL":
					String id = request.get("productId").getAsString();
					boolean tracked = dbManager.isProductTracked(id);

					List<Pair<String, Long>> history = tracked ? dbManager.getPriceHistory(id)
							: new java.util.ArrayList<>();

					List<String> reviews = tikiService.getProductReviews(id);

					JsonObject detail = new JsonObject();
					detail.addProperty("isTracked", tracked);
					detail.add("history", gson.toJsonTree(history));
					detail.add("reviews", gson.toJsonTree(reviews));

					responseJson = gson.toJson(detail);
					break;
				}

				// Encrypt and send back the response
				out.writeUTF(CryptoUtils.encryptAES(responseJson, sessionKey));
				out.flush();
			}
		} catch (Exception e) {
			System.out.println("[NETWORK] Client disconnected: " + socket.getRemoteSocketAddress());
		}
	}
}
