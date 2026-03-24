// File: src/main/java/com/tiki/client/TikiClientApp.java
package com.tiki.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiki.common.Category;

public class TikiClientApp extends JFrame {
	private static final long serialVersionUID = 1L;
	private ClientConnection connection;
	private JPanel productPanel; // Nơi hiển thị danh sách SP
	private JList<Category> categoryList;
	private JTextField searchField;

	public TikiClientApp() {
		setTitle("Tiki Price Tracker - Bảo mật Hybrid");
		setSize(1200, 800);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		initConnection();
		setupUI();
		loadCategories();
	}

	private void initConnection() {
		connection = new ClientConnection();
		try {
			connection.connect("localhost", 12345);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Không thể kết nối Server!");
			System.exit(0);
		}
	}

	private void setupUI() {
		setLayout(new BorderLayout());

		// 1. Thanh tìm kiếm (Top)
		JPanel topPanel = new JPanel(new BorderLayout(10, 10));
		topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		searchField = new JTextField();
		JButton btnSearch = new JButton("Tìm kiếm");
		topPanel.add(new JLabel("Tìm sản phẩm: "), BorderLayout.WEST);
		topPanel.add(searchField, BorderLayout.CENTER);
		topPanel.add(btnSearch, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		// 2. Sidebar Danh mục (Left)
		categoryList = new JList<>();
		categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane sidebarScroll = new JScrollPane(categoryList);
		sidebarScroll.setPreferredSize(new Dimension(250, 0));

		// 3. Vùng hiển thị nội dung (Center)
		productPanel = new JPanel(new GridLayout(0, 3, 10, 10));
		JScrollPane contentScroll = new JScrollPane(productPanel);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarScroll, contentScroll);
		add(splitPane, BorderLayout.CENTER);

		// Events
		btnSearch.addActionListener(e -> performSearch());
		categoryList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
				loadProductsByCategory();
		});
	}

	// --- LOGIC XỬ LÝ DỮ LIỆU ---

	private void loadCategories() {
		try {
			@SuppressWarnings("unused")
			String resp = connection.sendRequest("{\"action\":\"GET_CATEGORIES\"}");
			// Parse JSON và đưa vào JList (Bạn cần cập nhật ClientHandler phía Server để
			// trả về data này)
			// Tạm thời tạo dummy để bạn thấy giao diện
			DefaultListModel<Category> model = new DefaultListModel<>();
			model.addElement(new Category(String.valueOf(1789), "Điện Thoại - Máy Tính Bảng"));
			model.addElement(new Category(String.valueOf(1815), "Thiết Bị Số - Phụ Kiện"));
			categoryList.setModel(model);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void performSearch() {
		String query = searchField.getText();
		renderProductList("https://tiki.vn/api/v2/products?limit=40&q=" + query.replace(" ", "+"));
	}

	private void loadProductsByCategory() {
		Category selected = categoryList.getSelectedValue();
		if (selected != null) {
			renderProductList(
					"https://tiki.vn/api/personalish/v1/blocks/listings?category=" + selected.getId() + "&page=1");
		}
	}

	private void renderProductList(String apiUrl) {
		productPanel.removeAll();
		try {
			// Gửi request cho Server, Server sẽ gọi Tiki và trả về List<Product>
			String jsonResp = connection.sendRequest("{\"action\":\"GET_PRODUCTS\", \"url\":\"" + apiUrl + "\"}");
			JsonArray array = JsonParser.parseString(jsonResp).getAsJsonArray();

			for (JsonElement el : array) {
				JsonObject obj = el.getAsJsonObject();
				productPanel.add(createProductCard(obj));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		productPanel.revalidate();
		productPanel.repaint();
	}

	private JPanel createProductCard(JsonObject product) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		// Thumbnail
		try {
			@SuppressWarnings("deprecation")
			URL url = new URL(product.get("thumbnail_url").getAsString());
			ImageIcon icon = new ImageIcon(
					new ImageIcon(url).getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH));
			card.add(new JLabel(icon), BorderLayout.CENTER);
		} catch (Exception e) {
			card.add(new JLabel("No Image"), BorderLayout.CENTER);
		}

		// Info
		JPanel info = new JPanel(new GridLayout(2, 1));
		JLabel nameLabel = new JLabel(
				"<html><body style='width: 120px'>" + product.get("name").getAsString() + "</body></html>");
		JLabel priceLabel = new JLabel(product.get("price").getAsLong() + " VNĐ");
		priceLabel.setForeground(Color.RED);

		info.add(nameLabel);
		info.add(priceLabel);
		card.add(info, BorderLayout.SOUTH);

		// Click để xem chi tiết
		card.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				showProductDetail(product.get("id").getAsString());
			}
		});

		return card;
	}

	private void showProductDetail(String productId) {
		// Mở một JDialog mới hiện Biểu đồ và Review
		JDialog detailDlg = new JDialog(this, "Chi tiết sản phẩm", true);
		detailDlg.setSize(800, 600);
		detailDlg.setLayout(new BorderLayout());

		// 1. Biểu đồ giá (Sử dụng JFreeChart)
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		// Giả sử lấy từ Server history: dbManager.getPriceHistory(productId)
		dataset.addValue(5900000, "Giá", "20/03");
		dataset.addValue(5850000, "Giá", "21/03");
		dataset.addValue(5950000, "Giá", "22/03");

		JFreeChart chart = ChartFactory.createLineChart("Lịch sử giá", "Ngày", "VNĐ", dataset);
		detailDlg.add(new ChartPanel(chart), BorderLayout.CENTER);

		// 2. Reviews (Bottom)
		JTextArea txtReviews = new JTextArea(10, 0);
		txtReviews.setEditable(false);
		txtReviews.setText("Đang tải đánh giá...");
		detailDlg.add(new JScrollPane(txtReviews), BorderLayout.SOUTH);

		// 3. Nút Theo dõi
		JCheckBox chkTrack = new JCheckBox("Theo dõi giá sản phẩm này");
		detailDlg.add(chkTrack, BorderLayout.NORTH);
		chkTrack.addActionListener(e -> {
			try {
				connection.sendRequest("{\"action\":\"TRACK\", \"productId\":\"" + productId + "\"}");
				JOptionPane.showMessageDialog(detailDlg, "Đã lưu vào danh sách theo dõi!");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});

		detailDlg.setVisible(true);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new TikiClientApp().setVisible(true));
	}
}
