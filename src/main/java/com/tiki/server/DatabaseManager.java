package com.tiki.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.tiki.common.Pair;
import com.tiki.common.Product;

public class DatabaseManager {
	private static final String URL = "jdbc:sqlite:tiki_tracker.db";

	public DatabaseManager() {
		initDatabase();
	}

	private void initDatabase() {
		try (Connection conn = DriverManager.getConnection(URL); Statement stmt = conn.createStatement()) {

			// Create products table
			stmt.execute("CREATE TABLE IF NOT EXISTS products (" + "id TEXT PRIMARY KEY, " + "name TEXT, "
					+ "thumbnail TEXT, " + "is_tracked INTEGER DEFAULT 0)");

			// Create price history table
			stmt.execute("CREATE TABLE IF NOT EXISTS price_history (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ "product_id TEXT, " + "price INTEGER, " + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, "
					+ "FOREIGN KEY(product_id) REFERENCES products(id))");

			System.out.println("[SYSTEM] Database tables initialized successfully.");
		} catch (SQLException e) {
			System.err.println("[ERROR] Database initialization failed: " + e.getMessage());
		}
	}

	public void saveProduct(Product p) {
		String sql = "INSERT INTO products(id, name, thumbnail) VALUES(?,?,?) "
				+ "ON CONFLICT(id) DO UPDATE SET name=excluded.name, thumbnail=excluded.thumbnail";
		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, p.getId());
			pstmt.setString(2, p.getName());
			pstmt.setString(3, p.getThumbnail());
			pstmt.executeUpdate();
			System.out.println("[DATABASE] Product meta-data synchronized: " + p.getId());
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to save product " + p.getId() + ": " + e.getMessage());
		}
	}

	public void updateTracking(String productId, boolean isTracked) {
		String sql = "UPDATE products SET is_tracked = ? WHERE id = ?";
		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, isTracked ? 1 : 0);
			pstmt.setString(2, productId);
			pstmt.executeUpdate();
			System.out.println("[DATABASE] Tracking status updated for ID " + productId + " -> " + isTracked);
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to update tracking for " + productId + ": " + e.getMessage());
		}
	}

	public void addPriceHistory(String productId, long price) {
		String sql = "INSERT INTO price_history(product_id, price) VALUES(?, ?)";
		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, productId);
			pstmt.setLong(2, price);
			pstmt.executeUpdate();
			System.out.println("[DATABASE] Price record added for Product ID: " + productId + " (" + price + " VND)");
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to add price history for " + productId + ": " + e.getMessage());
		}
	}

	public List<String> getTrackedProductIds() {
		List<String> ids = new ArrayList<>();
		String sql = "SELECT id FROM products WHERE is_tracked = 1";
		try (Connection conn = DriverManager.getConnection(URL);
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				ids.add(rs.getString("id"));
			}
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to fetch tracked IDs: " + e.getMessage());
		}
		return ids;
	}

	public List<Product> getTrackedProducts(int page, int limit) {
		List<Product> list = new ArrayList<>();
		int offset = (page - 1) * limit;
		String sql = "SELECT p.*, "
				+ "(SELECT price FROM price_history WHERE product_id = p.id ORDER BY timestamp DESC LIMIT 1) as current_price "
				+ "FROM products p WHERE p.is_tracked = 1 " + "LIMIT ? OFFSET ?";

		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, limit);
			pstmt.setInt(2, offset);
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				list.add(new Product(rs.getString("id"), rs.getString("name"), rs.getLong("current_price"),
						rs.getString("thumbnail"), true));
			}
		} catch (SQLException e) {
			System.err.println("[ERROR] Database query failed (getTrackedProducts): " + e.getMessage());
		}
		return list;
	}

	public List<Pair<String, Long>> getPriceHistory(String productId) {
		List<Pair<String, Long>> history = new ArrayList<>();
		String sql = "SELECT timestamp, price FROM price_history WHERE product_id = ? ORDER BY timestamp ASC";
		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, productId);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				history.add(new Pair<>(rs.getString("timestamp"), rs.getLong("price")));
			}
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to fetch price history for ID " + productId + ": " + e.getMessage());
		}
		return history;
	}

	public long getLastPrice(String productId) {
		String sql = "SELECT price FROM price_history WHERE product_id = ? ORDER BY timestamp DESC LIMIT 1";
		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, productId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				return rs.getLong("price");
			}
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to fetch last price for " + productId + ": " + e.getMessage());
		}
		return -1;
	}

	public boolean isProductTracked(String productId) {
		String sql = "SELECT is_tracked FROM products WHERE id = ? AND is_tracked = 1";
		try (Connection conn = DriverManager.getConnection(URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, productId);
			ResultSet rs = pstmt.executeQuery();
			return rs.next();
		} catch (SQLException e) {
			System.err.println("[ERROR] Failed to check tracking status for " + productId + ": " + e.getMessage());
			return false;
		}
	}
}
