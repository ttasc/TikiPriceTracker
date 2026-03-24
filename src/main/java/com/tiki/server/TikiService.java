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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Lỗi gọi API: " + response.statusCode());
        }
        return response.body();
    }

    // 1. Lấy danh mục sản phẩm (Parse từ menu-config)
    public List<Category> getCategories() throws Exception {
        String json = sendGet("https://api.tiki.vn/raiden/v2/menu-config");
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray items = root.getAsJsonObject("menu_block").getAsJsonArray("items");
        
        List<Category> categories = new ArrayList<>();
        // Regex tìm số đứng sau chữ 'c' ở cuối link
        Pattern pattern = Pattern.compile("/c(\\d+)"); 

        for (JsonElement el : items) {
            Category cat = gson.fromJson(el, Category.class);
            Matcher matcher = pattern.matcher(cat.getLink());
            if (matcher.find()) {
                cat.setId(matcher.group(1));
                categories.add(cat);
            }
        }
        return categories;
    }

    // 2 & 3. Lấy danh sách sản phẩm (Dùng chung cho cả Listing theo Category và Search)
    public List<Product> getProductList(String url) throws Exception {
        String json = sendGet(url);
        List<Product> products = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");

        for (JsonElement el : data) {
            JsonObject item = el.getAsJsonObject();
            products.add(new Product(
                    item.get("id").getAsString(),
                    item.get("name").getAsString(),
                    item.get("price").getAsLong(),
                    item.get("thumbnail_url").getAsString(),
                    false
            ));
        }
        return products;
    }

    // 4. Lấy chi tiết sản phẩm (Để lấy giá mới nhất)
    public Product getProductDetail(String productId) throws Exception {
        String url = "https://tiki.vn/api/v2/products/" + productId;
        String json = sendGet(url);
        JsonObject item = JsonParser.parseString(json).getAsJsonObject();

        return new Product(
                item.get("id").getAsString(),
                item.get("name").getAsString(),
                item.get("price").getAsLong(),
                item.get("thumbnail_url").getAsString(),
                false
        );
    }

    // 5. Lấy reviews (Chỉ lấy text)
    public List<String> getProductReviews(String productId) throws Exception {
        String url = "https://tiki.vn/api/v2/reviews?product_id=" + productId;
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
        return reviews;
    }
}
