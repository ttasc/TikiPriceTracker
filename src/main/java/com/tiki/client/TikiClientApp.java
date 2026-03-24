// File: src/main/java/com/tiki/client/TikiClientApp.java
package com.tiki.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.util.List;

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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.tiki.common.Category;

public class TikiClientApp extends JFrame {
	private static final long serialVersionUID = 1L;
	private ClientConnection connection;
	private JPanel productPanel; // Nơi hiển thị danh sách SP
	private JList<Category> categoryList;
	private JTextField searchField;

	private int currentPage = 1;

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

		// Trong setupUI(), thêm một Panel ở phía Nam của content area
		JPanel paginationPanel = new JPanel();
		JButton btnPrev = new JButton("< Trang trước");
		JButton btnNext = new JButton("Trang sau >");
		JLabel lblPage = new JLabel("Trang: 1");

		paginationPanel.add(btnPrev);
		paginationPanel.add(lblPage);
		paginationPanel.add(btnNext);

		// Thêm sự kiện
		btnNext.addActionListener(e -> {
			currentPage++;
			lblPage.setText("Trang: " + currentPage);
			refreshList();
		});
		btnPrev.addActionListener(e -> {
			if (currentPage > 1) {
				currentPage--;
				lblPage.setText("Trang: " + currentPage);
				refreshList();
			}
		});

		// Gắn vào giao diện (Dùng BorderLayout để Panel này nằm dưới cùng)
		JPanel centerContainer = new JPanel(new BorderLayout());
		centerContainer.add(contentScroll, BorderLayout.CENTER);
		centerContainer.add(paginationPanel, BorderLayout.SOUTH);
		splitPane.setRightComponent(centerContainer);

		// Events
		btnSearch.addActionListener(e -> performSearch());
		categoryList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
				loadProductsByCategory();
		});
	}

	private void refreshList() {
		Category selected = categoryList.getSelectedValue();
		String query = searchField.getText();
		String finalUrl;

		if (query != null && !query.isEmpty()) {
			finalUrl = "https://tiki.vn/api/v2/products?limit=40&q=" + query.replace(" ", "+") + "&page=" + currentPage;
		} else if (selected != null) {
			finalUrl = "https://tiki.vn/api/personalish/v1/blocks/listings?category=" + selected.getId() + "&page="
					+ currentPage;
		} else {
			return;
		}

		renderProductList(finalUrl);
	}

	// --- LOGIC XỬ LÝ DỮ LIỆU ---

	private void loadCategories() {
		try {
			// 1. Lấy dữ liệu từ Server
			String resp = connection.sendRequest("{\"action\":\"GET_CATEGORIES\"}");

			// 2. Gson tự động parse toàn bộ List từ JSON string
			java.lang.reflect.Type listType = new TypeToken<List<Category>>() {
			}.getType();
			List<Category> categories = new Gson().fromJson(resp, listType);

			// 3. Đổ vào JList
			DefaultListModel<Category> model = new DefaultListModel<>();
			for (Category c : categories) {
				// Chỉ thêm các danh mục có ID hợp lệ (tránh các link quảng cáo)
				if (c.getId() != null) {
					model.addElement(c);
				}
			}
			categoryList.setModel(model);

		} catch (Exception e) {
			e.printStackTrace();
			// JOptionPane.showMessageDialog(this, "Lỗi kết nối Server!");
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
//		try {
//			@SuppressWarnings("deprecation")
//			URL url = new URL(product.get("thumbnail_url").getAsString());
//			ImageIcon icon = new ImageIcon(
//					new ImageIcon(url).getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH));
//			card.add(new JLabel(icon), BorderLayout.CENTER);
//		} catch (Exception e) {
//			card.add(new JLabel("No Image"), BorderLayout.CENTER);
//		}
		JLabel imgLabel = new JLabel("Loading...", JLabel.CENTER);
		String imgUrl = product.get("thumbnail_url").getAsString();

		// Dùng luồng riêng để tải ảnh, tránh làm treo UI
		new Thread(() -> {
			try {
				java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
				java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
						.uri(java.net.URI.create(imgUrl)).header("User-Agent", "Mozilla/5.0").build();
				byte[] imageBytes = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();

				ImageIcon icon = new ImageIcon(imageBytes);
				Image scaled = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);

				SwingUtilities.invokeLater(() -> {
					imgLabel.setIcon(new ImageIcon(scaled));
					imgLabel.setText(""); // Xóa chữ "Loading"
				});
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> imgLabel.setText("No Image"));
			}
		}).start();

		card.add(imgLabel, BorderLayout.CENTER);

		// Hiển thị trạng thái "Đang theo dõi" nếu isTracked = true
		// Trong hàm createProductCard
		if (product.has("isTracked") && product.get("isTracked").getAsBoolean()) {
			JLabel trackBadge = new JLabel(" ★ ĐANG THEO DÕI ");
			trackBadge.setOpaque(true);
			trackBadge.setBackground(new Color(255, 215, 0)); // Màu vàng gold
			trackBadge.setForeground(Color.BLACK);
			trackBadge.setFont(new Font("Arial", Font.BOLD, 10));
			card.add(trackBadge, BorderLayout.NORTH);
		} else {
			// Thêm một khoảng trống để giao diện các card đồng đều
			JPanel spacer = new JPanel();
			spacer.setPreferredSize(new Dimension(0, 20));
			card.add(spacer, BorderLayout.NORTH);
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
				showProductDetail(product); // Truyền cả object JSON
			}
		});

		return card;
	}

	private void showProductDetail(JsonObject productObj) {
		String productId = productObj.get("id").getAsString();

		JDialog detailDlg = new JDialog(this, "Chi tiết sản phẩm", true);
		detailDlg.setSize(900, 700);
		detailDlg.setLayout(new BorderLayout());
		detailDlg.setLocationRelativeTo(this);

		try {
			// 1. Lấy dữ liệu chi tiết từ Server
			String resp = connection.sendRequest("{\"action\":\"GET_DETAIL\", \"productId\":\"" + productId + "\"}");
			JsonObject data = JsonParser.parseString(resp).getAsJsonObject();

			boolean isTrackedOnServer = data.get("isTracked").getAsBoolean();
			JsonArray historyArr = data.getAsJsonArray("history");
			JsonArray reviewsArr = data.getAsJsonArray("reviews");

			// 2. Checkbox theo dõi (NORTH)
			JCheckBox chkTrack = new JCheckBox("Theo dõi giá sản phẩm này để xem biểu đồ biến động", isTrackedOnServer);
			chkTrack.setFont(new Font("Arial", Font.BOLD, 13));
			detailDlg.add(chkTrack, BorderLayout.NORTH);

			// 3. Vùng hiển thị Biểu đồ hoặc Thông báo (CENTER)
			JPanel centerPanel = new JPanel(new BorderLayout());
			if (isTrackedOnServer && historyArr.size() > 0) {
				// Hiển thị biểu đồ
				DefaultCategoryDataset dataset = new DefaultCategoryDataset();
				for (JsonElement e : historyArr) {
					JsonObject h = e.getAsJsonObject();
					dataset.addValue(h.get("value").getAsLong(), "Giá", h.get("key").getAsString().substring(5, 16));
				}
				JFreeChart chart = ChartFactory.createLineChart("Lịch sử biến động giá", "Ngày quét", "VNĐ", dataset);
				centerPanel.add(new ChartPanel(chart), BorderLayout.CENTER);
			} else {
				// Hiển thị thông báo hướng dẫn
				JLabel msgLabel = new JLabel(
						"<html><center>Biểu đồ giá chưa có dữ liệu.<br>Vui lòng tích vào 'Theo dõi' và quay lại sau khi Server đã cập nhật giá.</center></html>",
						JLabel.CENTER);
				msgLabel.setFont(new Font("Arial", Font.ITALIC, 16));
				msgLabel.setForeground(Color.GRAY);
				centerPanel.add(msgLabel, BorderLayout.CENTER);
			}
			detailDlg.add(centerPanel, BorderLayout.CENTER);

			// 4. Vùng hiển thị Reviews (SOUTH)
			StringBuilder sb = new StringBuilder("ĐÁNH GIÁ TỪ NGƯỜI DÙNG:\n");
			for (JsonElement e : reviewsArr) {
				sb.append("- ").append(e.getAsString()).append("\n\n");
			}
			JTextArea txtReviews = new JTextArea(sb.toString());
			txtReviews.setEditable(false);
			txtReviews.setLineWrap(true);
			detailDlg.add(new JScrollPane(txtReviews), BorderLayout.SOUTH);

			// Sự kiện click checkbox
			chkTrack.addActionListener(e -> {
				try {
					boolean selected = chkTrack.isSelected();
					connection.sendRequest("{\"action\":\"TOGGLE_TRACK\", \"productId\":\"" + productId
							+ "\", \"isTracked\":" + selected + "}");
					productObj.addProperty("isTracked", selected); // Cập nhật local
					refreshList(); // Cập nhật UI chính (huy hiệu sao vàng)

					if (selected) {
						JOptionPane.showMessageDialog(detailDlg,
								"Đã bắt đầu theo dõi. Biểu đồ sẽ hiển thị sau khi Server cập nhật giá (06:00).");
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}

		detailDlg.setVisible(true);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new TikiClientApp().setVisible(true));
	}
}
