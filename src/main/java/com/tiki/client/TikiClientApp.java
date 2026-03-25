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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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
import javax.swing.ListSelectionModel;
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

/**
 * Tiki Price Tracker Client Features: Hybrid Encryption, Price History Chart
 * with Tooltips, Manual Sync, Pagination, and Tracked List Management.
 */
public class TikiClientApp extends JFrame {
	private static final long serialVersionUID = 1L;

	// View Modes for logic handling
	private enum ViewMode {
		CATEGORY, SEARCH, TRACKED
	}

	private ViewMode currentViewMode = ViewMode.CATEGORY;

	private ClientConnection connection;
	private String serverIp;
	private int currentPage = 1;

	// UI Components
	private JPanel productPanel;
	private JList<Category> categoryList;
	private JTextField searchField;
	private JLabel lblPageStatus;
	private JScrollPane contentScroll;

	// Simple memory cache for images to ensure smooth scrolling
	private final ConcurrentHashMap<String, ImageIcon> imageCache = new ConcurrentHashMap<>();

	public TikiClientApp(String host) {
		this.serverIp = host;
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
	}

	private void initConnection() {
		connection = new ClientConnection();
		try {
			connection.connect(serverIp, 12345);
			System.out.println("[Network] Connected to " + serverIp);
		} catch (Exception e) {
			System.err.println("[Critical] Connection failed: " + e.getMessage());
			JOptionPane.showMessageDialog(this, "Cannot connect to Server at " + serverIp, "Network Error",
					JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		}
	}

	private void setupUI() {
		setLayout(new BorderLayout());

		// 1. TOP PANEL: Search Bar
		JPanel topPanel = new JPanel(new BorderLayout(15, 0));
		topPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
		searchField = new JTextField();
		searchField.putClientProperty("JTextField.placeholderText", "Enter product name...");
		JButton btnSearch = new JButton("Search");
		btnSearch.setCursor(new Cursor(Cursor.HAND_CURSOR));

		topPanel.add(new JLabel("Search: "), BorderLayout.WEST);
		topPanel.add(searchField, BorderLayout.CENTER);
		topPanel.add(btnSearch, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		// 2. LEFT PANEL: Categories & Tracked Button
		categoryList = new JList<>();
		categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		categoryList.setFixedCellHeight(35);
		JScrollPane sidebarScroll = new JScrollPane(categoryList);

		JButton btnTrackedView = new JButton("★ My Tracked Products");
		btnTrackedView.setPreferredSize(new Dimension(0, 50));
		btnTrackedView.setBackground(new Color(255, 248, 220)); // Cornsilk
		btnTrackedView.setFont(new Font("SansSerif", Font.BOLD, 13));

		JPanel sidebarPanel = new JPanel(new BorderLayout());
		sidebarPanel.setBorder(new EmptyBorder(0, 10, 10, 0));
		sidebarPanel.setPreferredSize(new Dimension(280, 0));
		sidebarPanel.add(new JLabel(" CATEGORIES", JLabel.CENTER), BorderLayout.NORTH);
		sidebarPanel.add(sidebarScroll, BorderLayout.CENTER);
		sidebarPanel.add(btnTrackedView, BorderLayout.SOUTH);

		// 3. CENTER PANEL: Product Grid
		productPanel = new JPanel(new GridLayout(0, 3, 15, 15));
		productPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		contentScroll = new JScrollPane(productPanel);
		contentScroll.getVerticalScrollBar().setUnitIncrement(25); // Smooth scrolling fix

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, null);
		splitPane.setDividerLocation(280);
		add(splitPane, BorderLayout.CENTER);

		// 4. BOTTOM PANEL: Pagination
		JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
		JButton btnPrev = new JButton("Previous");
		JButton btnNext = new JButton("Next Page");
		lblPageStatus = new JLabel("Page 1");
		lblPageStatus.setFont(new Font("SansSerif", Font.BOLD, 12));

		paginationPanel.add(btnPrev);
		paginationPanel.add(lblPageStatus);
		paginationPanel.add(btnNext);

		JPanel centerContainer = new JPanel(new BorderLayout());
		centerContainer.add(contentScroll, BorderLayout.CENTER);
		centerContainer.add(paginationPanel, BorderLayout.SOUTH);
		splitPane.setRightComponent(centerContainer);

		// --- EVENT LISTENERS ---
		btnSearch.addActionListener(e -> {
			currentViewMode = ViewMode.SEARCH;
			currentPage = 1;
			categoryList.clearSelection();
			refreshList();
		});

		categoryList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && categoryList.getSelectedValue() != null) {
				currentViewMode = ViewMode.CATEGORY;
				currentPage = 1;
				searchField.setText("");
				refreshList();
			}
		});

		btnTrackedView.addActionListener(e -> {
			currentViewMode = ViewMode.TRACKED;
			currentPage = 1;
			categoryList.clearSelection();
			searchField.setText("");
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
				jsonRequest = "{\"action\":\"GET_TRACKED_LIST\", \"page\":" + currentPage + ", \"limit\":50}";
				break;
			}

			if (!jsonRequest.isEmpty()) {
				System.out.println("[Request] Sending action: " + currentViewMode + " Page " + currentPage);
				String resp = connection.sendRequest(jsonRequest);
				JsonArray array = JsonParser.parseString(resp).getAsJsonArray();

				if (array.size() == 0) {
					productPanel.setLayout(new BorderLayout());
					productPanel.add(new JLabel("No products found.", JLabel.CENTER));
				} else {
					productPanel.setLayout(new GridLayout(0, 3, 15, 15));
					for (JsonElement el : array)
						productPanel.add(createProductCard(el.getAsJsonObject()));
				}
			}
		} catch (Exception e) {
			System.err.println("[Error] Refresh failed: " + e.getMessage());
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
			System.err.println("[Error] Category load failed: " + e.getMessage());
		}
	}

	private JPanel createProductCard(JsonObject product) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230), 1));
		card.setBackground(Color.WHITE);
		card.setCursor(new Cursor(Cursor.HAND_CURSOR));

		// 1. Image handling with Cache & Threading
		JLabel imgLabel = new JLabel(" ", JLabel.CENTER);
		imgLabel.setPreferredSize(new Dimension(180, 180));
		String imgUrl = product.has("thumbnail_url") ? product.get("thumbnail_url").getAsString() : "";

		if (!imgUrl.isEmpty()) {
			if (imageCache.containsKey(imgUrl)) {
				imgLabel.setIcon(imageCache.get(imgUrl));
			} else {
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

		// 2. Tracking Badge
		if (product.has("isTracked") && product.get("isTracked").getAsBoolean()) {
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

		// 3. Information (Name & Price)
		JPanel info = new JPanel(new GridLayout(2, 1, 5, 5));
		info.setBackground(Color.WHITE);
		info.setBorder(new EmptyBorder(10, 10, 10, 10));

		String cleanName = product.get("name").getAsString();
		JLabel lblName = new JLabel("<html><body style='width: 150px'>" + cleanName + "</body></html>");
		lblName.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JLabel lblPrice = new JLabel(String.format("%,d VND", product.get("price").getAsLong()));
		lblPrice.setForeground(new Color(204, 0, 0));
		lblPrice.setFont(new Font("SansSerif", Font.BOLD, 14));

		info.add(lblName);
		info.add(lblPrice);
		card.add(info, BorderLayout.SOUTH);

		card.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				showProductDetail(product);
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				card.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
			}

			@Override
			public void mouseExited(MouseEvent e) {
				card.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230), 1));
			}
		});

		return card;
	}

	private void showProductDetail(JsonObject productObj) {
		String productId = productObj.get("id").getAsString();
		JDialog detailDlg = new JDialog(this, "Product Detail: " + productId, true);
		detailDlg.setSize(1000, 750);
		detailDlg.setLocationRelativeTo(this);

		try {
			String resp = connection.sendRequest("{\"action\":\"GET_DETAIL\", \"productId\":\"" + productId + "\"}");
			JsonObject data = JsonParser.parseString(resp).getAsJsonObject();

			boolean isTracked = data.get("isTracked").getAsBoolean();
			JsonArray historyArr = data.getAsJsonArray("history");
			JsonArray reviewsArr = data.getAsJsonArray("reviews");

			// --- NORTH: Control Header ---
			JPanel header = new JPanel(new BorderLayout());
			header.setBorder(new EmptyBorder(10, 15, 10, 15));
			JCheckBox chkTrack = new JCheckBox("Enable Price Tracking (Auto-sync every 3 hours)", isTracked);
			chkTrack.setFont(new Font("SansSerif", Font.BOLD, 14));
			header.add(chkTrack, BorderLayout.WEST);
			detailDlg.add(header, BorderLayout.NORTH);

			// --- CENTER: Chart with Tooltips ---
			JPanel centerPanel = new JPanel(new BorderLayout());
			if (isTracked && historyArr.size() > 0) {
				DefaultCategoryDataset dataset = new DefaultCategoryDataset();
				for (JsonElement e : historyArr) {
					JsonObject h = e.getAsJsonObject();
					String time = h.get("key").getAsString();
					// Label for X-Axis: MM-dd HH:mm
					String axisLabel = time.substring(5, 16);
					dataset.addValue(h.get("value").getAsLong(), "Price", axisLabel);
				}

				// Create Chart with Tooltips enabled (true as second to last param)
				JFreeChart chart = ChartFactory.createLineChart("Price Variation History", "Timestamp", "Price (VND)",
						dataset, org.jfree.chart.plot.PlotOrientation.VERTICAL, true, true, false);

				// Customize Tooltips
				CategoryPlot plot = chart.getCategoryPlot();
				LineAndShapeRenderer renderer = new LineAndShapeRenderer();
				renderer.setDefaultShapesVisible(true);
				renderer.setDefaultToolTipGenerator(new StandardCategoryToolTipGenerator("Time: {1} | Price: {2} VND",
						java.text.NumberFormat.getIntegerInstance()));
				plot.setRenderer(renderer);

				centerPanel.add(new ChartPanel(chart), BorderLayout.CENTER);
			} else {
				centerPanel.add(new JLabel(
						"<html><center><font size='5'>No price data yet.</font><br>Enable tracking and wait for the next system update.</center></html>",
						JLabel.CENTER));
			}
			detailDlg.add(centerPanel, BorderLayout.CENTER);

			// --- SOUTH: Reviews Section ---
			StringBuilder sb = new StringBuilder("CUSTOMER REVIEWS (TEXT ONLY):\n\n");
			if (reviewsArr.size() == 0)
				sb.append("No reviews found for this product.");
			for (JsonElement e : reviewsArr)
				sb.append("● ").append(e.getAsString()).append("\n\n");

			JTextArea txtReviews = new JTextArea(sb.toString());
			txtReviews.setEditable(false);
			txtReviews.setLineWrap(true);
			txtReviews.setWrapStyleWord(true);
			txtReviews.setBackground(new Color(245, 245, 245));
			JScrollPane reviewScroll = new JScrollPane(txtReviews);
			reviewScroll.setPreferredSize(new Dimension(0, 200));
			reviewScroll.setBorder(BorderFactory.createTitledBorder("Reviews"));
			detailDlg.add(reviewScroll, BorderLayout.SOUTH);

			// Checkbox logic
			chkTrack.addActionListener(e -> {
				try {
					boolean selected = chkTrack.isSelected();
					connection.sendRequest("{\"action\":\"TOGGLE_TRACK\", \"productId\":\"" + productId
							+ "\", \"isTracked\":" + selected + "}");
					productObj.addProperty("isTracked", selected);
					refreshList();
					if (selected)
						JOptionPane.showMessageDialog(detailDlg,
								"Success! We will now record price changes for this item.");
				} catch (Exception ex) {
					System.err.println("[Error] Toggle failed.");
				}
			});

		} catch (Exception e) {
			System.err.println("[Error] Detail fetch failed.");
		}

		detailDlg.setVisible(true);
	}

	public static void main(String[] args) {
		// Performance & UI Optimization
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");
		System.setProperty("flatlaf.uiScale", "1.1");
		UIManager.put("ScrollPane.smoothScrolling", true);

		try {
			UIManager.setLookAndFeel(new FlatLightLaf());
		} catch (Exception ex) {
			System.err.println("[System] FlatLaf failed to initialize.");
		}

		String host = (args.length > 0) ? args[0] : "localhost";
		SwingUtilities.invokeLater(() -> new TikiClientApp(host).setVisible(true));
	}
}
