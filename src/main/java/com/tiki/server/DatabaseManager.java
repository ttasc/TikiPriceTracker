package com.tiki.server;

import com.tiki.common.Pair;
import com.tiki.common.Product;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:tiki_tracker.db";

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            
            // Tạo bảng sản phẩm
            stmt.execute("CREATE TABLE IF NOT EXISTS products (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT, " +
                    "thumbnail TEXT, " +
                    "is_tracked INTEGER DEFAULT 0)");

            // Tạo bảng lịch sử giá
            stmt.execute("CREATE TABLE IF NOT EXISTS price_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "product_id TEXT, " +
                    "price INTEGER, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(product_id) REFERENCES products(id))");
            
            System.out.println("Database initialized.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lưu hoặc cập nhật thông tin sản phẩm
    public void saveProduct(Product p) {
        String sql = "INSERT INTO products(id, name, thumbnail) VALUES(?,?,?) " +
                     "ON CONFLICT(id) DO UPDATE SET name=excluded.name, thumbnail=excluded.thumbnail";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p.getId());
            pstmt.setString(2, p.getName());
            pstmt.setString(3, p.getThumbnail());
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Bật/Tắt theo dõi sản phẩm
    public void updateTracking(String productId, boolean isTracked) {
        String sql = "UPDATE products SET is_tracked = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, isTracked ? 1 : 0);
            pstmt.setString(2, productId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Thêm bản ghi giá mới (dùng cho tác vụ quét lúc 06:00)
    public void addPriceHistory(String productId, long price) {
        String sql = "INSERT INTO price_history(product_id, price) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, productId);
            pstmt.setLong(2, price);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Lấy danh sách ID các sản phẩm đang được theo dõi để quét giá
    public List<String> getTrackedProductIds() {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT id FROM products WHERE is_tracked = 1";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return ids;
    }
    
    // Lấy lịch sử giá để vẽ biểu đồ
    public List<Pair<String, Long>> getPriceHistory(String productId) {
        List<Pair<String, Long>> history = new ArrayList<>();
        String sql = "SELECT timestamp, price FROM price_history WHERE product_id = ? ORDER BY timestamp ASC";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, productId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(new Pair<>(rs.getString("timestamp"), rs.getLong("price")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return history;
    }

    public boolean isProductTracked(String productId) {
        String sql = "SELECT is_tracked FROM products WHERE id = ? AND is_tracked = 1";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Trả về true nếu có bản ghi
        } catch (SQLException e) { return false; }
    }
}
