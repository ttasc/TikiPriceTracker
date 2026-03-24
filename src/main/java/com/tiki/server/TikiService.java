package com.tiki.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiki.common.Category;
import com.tiki.common.Product;

public class TikiService {
	private final HttpClient client;
	private final Gson gson;

	public TikiService() {
		this.client = HttpClient.newHttpClient();
		this.gson = new Gson();
	}

	private String sendGet(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
				.header("Accept", "application/json").GET().build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			System.err
					.println("[ERROR] API request failed for URL: " + url + " | Status Code: " + response.statusCode());
			throw new RuntimeException("API request failed with status code: " + response.statusCode());
		}
		return response.body();
	}

	public List<Category> getCategories() throws Exception {
		System.out.println("[LOG] Fetching categories from Tiki...");
		String json = sendGet("https://api.tiki.vn/raiden/v2/menu-config");
		JsonObject root = gson.fromJson(json, JsonObject.class);
		JsonArray items = root.getAsJsonObject("menu_block").getAsJsonArray("items");

		List<Category> categories = new ArrayList<>();
		Pattern pattern = Pattern.compile("/c(\\d+)");

		for (JsonElement el : items) {
			Category cat = gson.fromJson(el, Category.class);
			Matcher matcher = pattern.matcher(cat.getLink());
			if (matcher.find()) {
				cat.setId(matcher.group(1));
				categories.add(cat);
			}
		}
		System.out.println("[INFO] Successfully parsed " + categories.size() + " categories.");
		return categories;
	}

	public List<Product> getProductList(String url) throws Exception {
		System.out.println("[LOG] Fetching product list from: " + url);
		String json = sendGet(url);
		List<Product> products = new ArrayList<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonArray data = root.getAsJsonArray("data");

		for (JsonElement el : data) {
			JsonObject item = el.getAsJsonObject();
			products.add(new Product(item.get("id").getAsString(), item.get("name").getAsString(),
					item.get("price").getAsLong(), item.get("thumbnail_url").getAsString(), false));
		}
		System.out.println("[INFO] Successfully retrieved " + products.size() + " products.");
		return products;
	}

	public Product getProductDetail(String productId) throws Exception {
		String url = "https://tiki.vn/api/v2/products/" + productId;
		System.out.println("[LOG] Fetching product details for ID: " + productId);
		String json = sendGet(url);
		JsonObject item = JsonParser.parseString(json).getAsJsonObject();

		return new Product(item.get("id").getAsString(), item.get("name").getAsString(), item.get("price").getAsLong(),
				item.get("thumbnail_url").getAsString(), false);
	}

	public List<String> getProductReviews(String productId) throws Exception {
		String url = "https://tiki.vn/api/v2/reviews?product_id=" + productId;
		System.out.println("[LOG] Fetching reviews for product ID: " + productId);
		String json = sendGet(url);
		List<String> reviews = new ArrayList<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonArray dataArray = root.getAsJsonArray("data");

		for (JsonElement el : dataArray) {
			JsonObject reviewObj = el.getAsJsonObject();
			String content = reviewObj.get("content").getAsString();
			if (!content.isEmpty()) {
				reviews.add(content);
			}
		}
		System.out.println("[INFO] Successfully retrieved " + reviews.size() + " reviews.");
		return reviews;
	}
}
