package com.tiki.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.tiki.common.Category;

public class TikiClientApp extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final String LOCAL_TRACK_FILE = "my_tracked_ids.json";

	private enum ViewMode {
		CATEGORY, SEARCH, TRACKED
	}

	private ViewMode currentViewMode = ViewMode.CATEGORY;

	private ClientConnection connection;
	private String serverIp;
	private boolean isAdminMode = false;
	private int currentPage = 1;

	private JPanel productPanel;
	private JList<Category> categoryList;
	private JTextField searchField;
	private JLabel lblPageStatus;
	private JScrollPane contentScroll;

	// Lưu trữ ID sản phẩm mà máy khách này thực sự theo dõi
	private Set<String> myLocalTrackedIds = new HashSet<>();
	private final ConcurrentHashMap<String, ImageIcon> imageCache = new ConcurrentHashMap<>();

	public TikiClientApp(String host, boolean adminMode) {
		this.serverIp = host;
		this.isAdminMode = adminMode;

		loadLocalTrackingData(); // Tải dữ liệu ID từ file local
		setupBaseFrame();
		initConnection();
		setupUI();
		loadCategories();
	}

	private void setupBaseFrame() {
		setTitle("Tiki Price Tracker | Server: " + serverIp);
		setSize(1280, 850);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		System.out.println("[SYSTEM] Mode: " + (isAdminMode ? "ADMIN" : "USER"));
	}

	// --- LOCAL DATA PERSISTENCE ---
	private void loadLocalTrackingData() {
		try {
			Path path = Paths.get(LOCAL_TRACK_FILE);
			if (Files.exists(path)) {
				String content = Files.readString(path);
				String[] ids = new Gson().fromJson(content, String[].class);
				myLocalTrackedIds.addAll(Arrays.asList(ids));
				System.out.println("[SYSTEM] Loaded " + myLocalTrackedIds.size() + " local tracked IDs.");
			}
		} catch (Exception e) {
			System.err.println("[SYSTEM] Could not load local tracking file.");
		}
	}

	private void saveLocalTrackingData() {
		try {
			String json = new Gson().toJson(myLocalTrackedIds);
			Files.writeString(Paths.get(LOCAL_TRACK_FILE), json);
			System.out.println("[SYSTEM] Local tracking data saved.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void initConnection() {
		connection = new ClientConnection();
		try {
			connection.connect(serverIp, 12345);
			System.out.println("[NETWORK] Connected to " + serverIp);
		} catch (Exception e) {
			System.err.println("[CRITICAL] Connection failed: " + e.getMessage());
			JOptionPane.showMessageDialog(this, "Cannot connect to Server", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		}
	}

	private void setupUI() {
		setLayout(new BorderLayout());

		// TOP PANEL
		JPanel topPanel = new JPanel(new BorderLayout(15, 0));
		topPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
		searchField = new JTextField();
		JButton btnSearch = new JButton("Search");
		topPanel.add(new JLabel("Search: "), BorderLayout.WEST);
		topPanel.add(searchField, BorderLayout.CENTER);
		topPanel.add(btnSearch, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		// SIDEBAR
		categoryList = new JList<>();
		categoryList.setFixedCellHeight(35);
		JScrollPane sidebarScroll = new JScrollPane(categoryList);

		JButton btnTrackedView = new JButton(isAdminMode ? "★ ALL TRACKED" : "★ MY TRACKED PRODUCTS");
		btnTrackedView.setPreferredSize(new Dimension(0, 50));
		btnTrackedView.setBackground(isAdminMode ? new Color(255, 204, 204) : new Color(220, 255, 220));
		btnTrackedView.setFont(new Font("SansSerif", Font.BOLD, 13));

		JPanel sidebarPanel = new JPanel(new BorderLayout());
		sidebarPanel.setBorder(new EmptyBorder(0, 10, 10, 0));
		sidebarPanel.setPreferredSize(new Dimension(280, 0));
		sidebarPanel.add(new JLabel(" CATEGORIES", JLabel.CENTER), BorderLayout.NORTH);
		sidebarPanel.add(sidebarScroll, BorderLayout.CENTER);
		sidebarPanel.add(btnTrackedView, BorderLayout.SOUTH);

		// CENTER
		productPanel = new JPanel(new GridLayout(0, 3, 15, 15));
		contentScroll = new JScrollPane(productPanel);
		contentScroll.getVerticalScrollBar().setUnitIncrement(25);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, null);
		splitPane.setDividerLocation(280);
		add(splitPane, BorderLayout.CENTER);

		// PAGINATION
		JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
		JButton btnPrev = new JButton("Previous");
		JButton btnNext = new JButton("Next Page");
		lblPageStatus = new JLabel("Page 1");
		paginationPanel.add(btnPrev);
		paginationPanel.add(lblPageStatus);
		paginationPanel.add(btnNext);

		JPanel centerContainer = new JPanel(new BorderLayout());
		centerContainer.add(contentScroll, BorderLayout.CENTER);
		centerContainer.add(paginationPanel, BorderLayout.SOUTH);
		splitPane.setRightComponent(centerContainer);

		// EVENTS
		btnSearch.addActionListener(e -> {
			currentViewMode = ViewMode.SEARCH;
			currentPage = 1;
			refreshList();
		});
		categoryList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && categoryList.getSelectedValue() != null) {
				currentViewMode = ViewMode.CATEGORY;
				currentPage = 1;
				refreshList();
			}
		});
		btnTrackedView.addActionListener(e -> {
			currentViewMode = ViewMode.TRACKED;
			currentPage = 1;
			refreshList();
		});
		btnNext.addActionListener(e -> {
			currentPage++;
			refreshList();
		});
		btnPrev.addActionListener(e -> {
			if (currentPage > 1) {
				currentPage--;
				refreshList();
			}
		});
	}

	private void refreshList() {
		productPanel.removeAll();
		lblPageStatus.setText("Page: " + currentPage);
		String jsonRequest = "";

		try {
			switch (currentViewMode) {
			case CATEGORY:
				Category selected = categoryList.getSelectedValue();
				if (selected != null) {
					String url = "https://tiki.vn/api/personalish/v1/blocks/listings?category=" + selected.getId()
							+ "&page=" + currentPage;
					jsonRequest = "{\"action\":\"GET_PRODUCTS\", \"url\":\"" + url + "\"}";
				}
				break;
			case SEARCH:
				String q = searchField.getText().trim();
				if (!q.isEmpty()) {
					String url = "https://tiki.vn/api/v2/products?limit=40&q=" + q.replace(" ", "+") + "&page="
							+ currentPage;
					jsonRequest = "{\"action\":\"GET_PRODUCTS\", \"url\":\"" + url + "\"}";
				}
				break;
			case TRACKED:
				// Admin Mode: 50 items/page. User Mode: Fetch more to filter.
				int limit = isAdminMode ? 50 : 200;
				jsonRequest = "{\"action\":\"GET_TRACKED_LIST\", \"page\":" + currentPage + ", \"limit\":" + limit
						+ "}";
				break;
			}

			if (!jsonRequest.isEmpty()) {
				String resp = connection.sendRequest(jsonRequest);
				JsonArray array = JsonParser.parseString(resp).getAsJsonArray();

				productPanel.setLayout(new GridLayout(0, 3, 15, 15));
				int displayedCount = 0;

				for (JsonElement el : array) {
					JsonObject obj = el.getAsJsonObject();
					String id = obj.get("id").getAsString();

					// --- LOGIC LỌC DỮ LIỆU ---
					if (currentViewMode == ViewMode.TRACKED && !isAdminMode) {
						if (!myLocalTrackedIds.contains(id))
							continue;
					}

					// Ghi đè thuộc tính isTracked dựa trên local data (cho User Mode)
					if (!isAdminMode) {
						obj.addProperty("isTracked", myLocalTrackedIds.contains(id));
					}

					productPanel.add(createProductCard(obj));
					displayedCount++;
				}

				if (displayedCount == 0) {
					productPanel.setLayout(new BorderLayout());
					productPanel.add(new JLabel("No products to display in this view.", JLabel.CENTER));
				}
				System.out.println("[INFO] Displayed " + displayedCount + " items.");
			}
		} catch (Exception e) {
			System.err.println("[ERROR] Refresh failed: " + e.getMessage());
		}

		productPanel.revalidate();
		productPanel.repaint();
		contentScroll.getVerticalScrollBar().setValue(0);
	}

	private void loadCategories() {
		try {
			String resp = connection.sendRequest("{\"action\":\"GET_CATEGORIES\"}");
			java.lang.reflect.Type listType = new TypeToken<List<Category>>() {
			}.getType();
			List<Category> categories = new Gson().fromJson(resp, listType);
			DefaultListModel<Category> model = new DefaultListModel<>();
			for (Category c : categories)
				if (c.getId() != null)
					model.addElement(c);
			categoryList.setModel(model);
		} catch (Exception e) {
			System.err.println("[ERROR] Category load failed.");
		}
	}

	private JPanel createProductCard(JsonObject product) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230), 1));
		card.setBackground(Color.WHITE);
		card.setCursor(new Cursor(Cursor.HAND_CURSOR));

		// Image
		JLabel imgLabel = new JLabel(" ", JLabel.CENTER);
		imgLabel.setPreferredSize(new Dimension(180, 180));
		String imgUrl = product.has("thumbnail_url") ? product.get("thumbnail_url").getAsString() : "";
		if (!imgUrl.isEmpty()) {
			if (imageCache.containsKey(imgUrl))
				imgLabel.setIcon(imageCache.get(imgUrl));
			else {
				new Thread(() -> {
					try {
						HttpClient client = HttpClient.newHttpClient();
						HttpRequest req = HttpRequest.newBuilder().uri(URI.create(imgUrl))
								.header("User-Agent", "Mozilla/5.0").build();
						byte[] data = client.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
						ImageIcon icon = new ImageIcon(
								new ImageIcon(data).getImage().getScaledInstance(160, 160, Image.SCALE_SMOOTH));
						imageCache.put(imgUrl, icon);
						SwingUtilities.invokeLater(() -> imgLabel.setIcon(icon));
					} catch (Exception e) {
						imgLabel.setText("No Image");
					}
				}).start();
			}
		}
		card.add(imgLabel, BorderLayout.CENTER);

		// Badge (Dựa trên local IDs trong User Mode, hoặc server state trong Admin
		// Mode)
		boolean isTracked = product.has("isTracked") && product.get("isTracked").getAsBoolean();
		if (isTracked) {
			JLabel badge = new JLabel(" ★ TRACKING ", JLabel.CENTER);
			badge.setOpaque(true);
			badge.setBackground(new Color(255, 215, 0));
			badge.setFont(new Font("SansSerif", Font.BOLD, 10));
			card.add(badge, BorderLayout.NORTH);
		} else {
			JPanel spacer = new JPanel();
			spacer.setBackground(Color.WHITE);
			spacer.setPreferredSize(new Dimension(0, 20));
			card.add(spacer, BorderLayout.NORTH);
		}

		// Info
		JPanel info = new JPanel(new GridLayout(2, 1, 5, 5));
		info.setBackground(Color.WHITE);
		info.setBorder(new EmptyBorder(10, 10, 10, 10));
		info.add(
				new JLabel("<html><body style='width: 150px'>" + product.get("name").getAsString() + "</body></html>"));
		JLabel lblPrice = new JLabel(String.format("%,d VND", product.get("price").getAsLong()));
		lblPrice.setForeground(new Color(204, 0, 0));
		lblPrice.setFont(new Font("SansSerif", Font.BOLD, 14));
		info.add(lblPrice);
		card.add(info, BorderLayout.SOUTH);

		card.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				showProductDetail(product);
			}
		});
		return card;
	}

	private void showProductDetail(JsonObject productObj) {
		String productId = productObj.get("id").getAsString();
		JDialog detailDlg = new JDialog(this, "Product Detail", true);
		detailDlg.setSize(1000, 750);
		detailDlg.setLocationRelativeTo(this);

		try {
			String resp = connection.sendRequest("{\"action\":\"GET_DETAIL\", \"productId\":\"" + productId + "\"}");
			JsonObject data = JsonParser.parseString(resp).getAsJsonObject();

			// Override isTracked bằng dữ liệu local nếu không phải Admin
			boolean isTracked = isAdminMode ? data.get("isTracked").getAsBoolean()
					: myLocalTrackedIds.contains(productId);
			JsonArray historyArr = data.getAsJsonArray("history");
			JsonArray reviewsArr = data.getAsJsonArray("reviews");

			JCheckBox chkTrack = new JCheckBox("Enable Price Tracking", isTracked);
			chkTrack.setFont(new Font("SansSerif", Font.BOLD, 14));
			detailDlg.add(chkTrack, BorderLayout.NORTH);

			JPanel centerPanel = new JPanel(new BorderLayout());
			// Hiển thị biểu đồ cho bất kỳ ai có dữ liệu (Admin thấy hết, User thấy của
			// mình)
			if (historyArr.size() > 0 && (isAdminMode || isTracked)) {
				DefaultCategoryDataset dataset = new DefaultCategoryDataset();
				for (JsonElement e : historyArr) {
					JsonObject h = e.getAsJsonObject();
					dataset.addValue(h.get("value").getAsLong(), "Price", h.get("key").getAsString().substring(5, 16));
				}
				JFreeChart chart = ChartFactory.createLineChart("Price History", "Time", "VND", dataset,
						org.jfree.chart.plot.PlotOrientation.VERTICAL, true, true, false);
				CategoryPlot plot = chart.getCategoryPlot();
				LineAndShapeRenderer renderer = new LineAndShapeRenderer();
				renderer.setDefaultToolTipGenerator(new StandardCategoryToolTipGenerator("Time: {1} | Price: {2} VND",
						java.text.NumberFormat.getIntegerInstance()));
				plot.setRenderer(renderer);
				centerPanel.add(new ChartPanel(chart), BorderLayout.CENTER);
			} else {
				centerPanel
						.add(new JLabel("<html><center>No price history recorded yet.</center></html>", JLabel.CENTER));
			}
			detailDlg.add(centerPanel, BorderLayout.CENTER);

			// Reviews...
			JTextArea txtReviews = new JTextArea(10, 0);
			for (JsonElement e : reviewsArr)
				txtReviews.append("● " + e.getAsString() + "\n\n");
			txtReviews.setEditable(false);
			txtReviews.setLineWrap(true);
			detailDlg.add(new JScrollPane(txtReviews), BorderLayout.SOUTH);

			chkTrack.addActionListener(e -> {
				try {
					boolean selected = chkTrack.isSelected();
					connection.sendRequest("{\"action\":\"TOGGLE_TRACK\", \"productId\":\"" + productId
							+ "\", \"isTracked\":" + selected + "}");

					// Cập nhật local storage
					if (selected)
						myLocalTrackedIds.add(productId);
					else
						myLocalTrackedIds.remove(productId);
					saveLocalTrackingData();

					productObj.addProperty("isTracked", selected);
					refreshList();
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
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");
		System.setProperty("flatlaf.uiScale", "1.2");

		try {
			UIManager.setLookAndFeel(new FlatLightLaf());
		} catch (Exception ex) {
		}

		String testHost = "localhost";
		boolean isAdmin = false;
		for (String arg : args) {
			if (arg.equalsIgnoreCase("--admin")) {
				isAdmin = true;
			} else if (isValidHost(arg)) {
				testHost = arg;
			}
		}

		String host = testHost;
		boolean admin = isAdmin;
		SwingUtilities.invokeLater(() -> new TikiClientApp(host, admin).setVisible(true));
	}

	public static boolean isValidHost(String host) {
	    try {
	        java.net.InetAddress.getByName(host);
	        return true;
	    } catch (java.net.UnknownHostException e) {
	        return false;
	    }
	}

}
