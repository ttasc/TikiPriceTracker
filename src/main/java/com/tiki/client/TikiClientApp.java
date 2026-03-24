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
import javax.swing.UIManager;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
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

	private ClientConnection connection;
	private String serverIp;

	private JPanel productPanel;
	private JList<Category> categoryList;
	private JTextField searchField;

	private int currentPage = 1;

	public TikiClientApp(String host) {
		this.serverIp = host;

		setTitle("Tiki Price Tracker - Connected to: " + serverIp);
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
			connection.connect(serverIp, 12345);
			System.out.println("[INFO] Successfully connected to Server: " + serverIp);
		} catch (Exception e) {
			System.err.println("[ERROR] Connection failed to " + serverIp + ": " + e.getMessage());
			JOptionPane.showMessageDialog(this,
					"Error: Could not connect to Server at " + serverIp + "\n\nDetails: " + e.getMessage(),
					"Connection Error", JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		}
	}

	private void setupUI() {
		setLayout(new BorderLayout());

		JPanel topPanel = new JPanel(new BorderLayout(10, 10));
		topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		searchField = new JTextField();
		JButton btnSearch = new JButton("Search");
		topPanel.add(new JLabel("Search Product: "), BorderLayout.WEST);
		topPanel.add(searchField, BorderLayout.CENTER);
		topPanel.add(btnSearch, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		categoryList = new JList<>();
		categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane sidebarScroll = new JScrollPane(categoryList);
		sidebarScroll.setPreferredSize(new Dimension(250, 0));

		productPanel = new JPanel(new GridLayout(0, 3, 10, 10));
		JScrollPane contentScroll = new JScrollPane(productPanel);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarScroll, contentScroll);
		add(splitPane, BorderLayout.CENTER);

		JButton btnViewTracked = new JButton("★ Tracked Products");
		btnViewTracked.setBackground(new Color(255, 255, 204));
		btnViewTracked.setFocusable(false);

		JPanel sidebarPanel = new JPanel(new BorderLayout());
		sidebarPanel.add(new JLabel(" CATEGORIES", JLabel.CENTER), BorderLayout.NORTH);
		sidebarPanel.add(sidebarScroll, BorderLayout.CENTER);
		sidebarPanel.add(btnViewTracked, BorderLayout.SOUTH);

		splitPane.setLeftComponent(sidebarPanel);

		btnViewTracked.addActionListener(e -> {
			System.out.println("[LOG] Loading tracked products list...");
			categoryList.clearSelection();
			searchField.setText("");
			loadTrackedProducts();
		});

		JPanel paginationPanel = new JPanel();
		JButton btnPrev = new JButton("< Previous");
		JButton btnNext = new JButton("Next >");
		JLabel lblPage = new JLabel("Page: 1");

		paginationPanel.add(btnPrev);
		paginationPanel.add(lblPage);
		paginationPanel.add(btnNext);

		btnNext.addActionListener(e -> {
			currentPage++;
			lblPage.setText("Page: " + currentPage);
			refreshList();
		});
		btnPrev.addActionListener(e -> {
			if (currentPage > 1) {
				currentPage--;
				lblPage.setText("Page: " + currentPage);
				refreshList();
			}
		});

		JPanel centerContainer = new JPanel(new BorderLayout());
		centerContainer.add(contentScroll, BorderLayout.CENTER);
		centerContainer.add(paginationPanel, BorderLayout.SOUTH);
		splitPane.setRightComponent(centerContainer);

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

	private void loadTrackedProducts() {
		productPanel.removeAll();
		try {
			String jsonResp = connection.sendRequest("{\"action\":\"GET_TRACKED_LIST\"}");
			JsonArray array = JsonParser.parseString(jsonResp).getAsJsonArray();

			if (array.size() == 0) {
				productPanel.setLayout(new BorderLayout());
				productPanel.add(new JLabel("You are not tracking any products yet.", JLabel.CENTER));
			} else {
				productPanel.setLayout(new GridLayout(0, 3, 10, 10));
				for (JsonElement el : array) {
					productPanel.add(createProductCard(el.getAsJsonObject()));
				}
			}
			System.out.println("[INFO] Loaded " + array.size() + " tracked products.");
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to load tracked products: " + e.getMessage());
		}
		productPanel.revalidate();
		productPanel.repaint();
	}

	private void loadCategories() {
		try {
			System.out.println("[LOG] Requesting categories from server...");
			String resp = connection.sendRequest("{\"action\":\"GET_CATEGORIES\"}");

			java.lang.reflect.Type listType = new TypeToken<List<Category>>() {
			}.getType();
			List<Category> categories = new Gson().fromJson(resp, listType);

			DefaultListModel<Category> model = new DefaultListModel<>();
			for (Category c : categories) {
				if (c.getId() != null) {
					model.addElement(c);
				}
			}
			categoryList.setModel(model);
			System.out.println("[INFO] Categories updated.");
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to load categories: " + e.getMessage());
		}
	}

	private void performSearch() {
		String query = searchField.getText();
		System.out.println("[LOG] Performing search for: " + query);
		renderProductList("https://tiki.vn/api/v2/products?limit=40&q=" + query.replace(" ", "+"));
	}

	private void loadProductsByCategory() {
		Category selected = categoryList.getSelectedValue();
		if (selected != null) {
			System.out.println("[LOG] Loading products for category: " + selected.getText());
			renderProductList(
					"https://tiki.vn/api/personalish/v1/blocks/listings?category=" + selected.getId() + "&page=1");
		}
	}

	private void renderProductList(String apiUrl) {
		productPanel.removeAll();
		try {
			String jsonResp = connection.sendRequest("{\"action\":\"GET_PRODUCTS\", \"url\":\"" + apiUrl + "\"}");
			JsonArray array = JsonParser.parseString(jsonResp).getAsJsonArray();

			for (JsonElement el : array) {
				JsonObject obj = el.getAsJsonObject();
				productPanel.add(createProductCard(obj));
			}
			System.out.println("[INFO] Rendered " + array.size() + " products.");
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to render product list: " + e.getMessage());
		}
		productPanel.revalidate();
		productPanel.repaint();
	}

	private JPanel createProductCard(JsonObject product) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		JLabel imgLabel = new JLabel("Loading...", JLabel.CENTER);
		String imgUrl = product.get("thumbnail_url").getAsString();

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
					imgLabel.setText("");
				});
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> imgLabel.setText("No Image"));
			}
		}).start();

		card.add(imgLabel, BorderLayout.CENTER);

		if (product.has("isTracked") && product.get("isTracked").getAsBoolean()) {
			JLabel trackBadge = new JLabel(" ★ TRACKING ");
			trackBadge.setOpaque(true);
			trackBadge.setBackground(new Color(255, 215, 0));
			trackBadge.setForeground(Color.BLACK);
			trackBadge.setFont(new Font("Arial", Font.BOLD, 10));
			card.add(trackBadge, BorderLayout.NORTH);
		} else {
			JPanel spacer = new JPanel();
			spacer.setPreferredSize(new Dimension(0, 20));
			card.add(spacer, BorderLayout.NORTH);
		}

		JPanel info = new JPanel(new GridLayout(2, 1));
		JLabel nameLabel = new JLabel(
				"<html><body style='width: 120px'>" + product.get("name").getAsString() + "</body></html>");
		JLabel priceLabel = new JLabel(product.get("price").getAsLong() + " VND");
		priceLabel.setForeground(Color.RED);

		info.add(nameLabel);
		info.add(priceLabel);
		card.add(info, BorderLayout.SOUTH);

		card.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				showProductDetail(product);
			}
		});

		return card;
	}

	private void showProductDetail(JsonObject productObj) {
		String productId = productObj.get("id").getAsString();
		System.out.println("[LOG] Opening details for Product ID: " + productId);

		JDialog detailDlg = new JDialog(this, "Product Details", true);
		detailDlg.setSize(900, 700);
		detailDlg.setLayout(new BorderLayout());
		detailDlg.setLocationRelativeTo(this);

		try {
			String resp = connection.sendRequest("{\"action\":\"GET_DETAIL\", \"productId\":\"" + productId + "\"}");
			JsonObject data = JsonParser.parseString(resp).getAsJsonObject();

			boolean isTrackedOnServer = data.get("isTracked").getAsBoolean();
			JsonArray historyArr = data.getAsJsonArray("history");
			JsonArray reviewsArr = data.getAsJsonArray("reviews");

			JCheckBox chkTrack = new JCheckBox("Track this product's price to view history chart", isTrackedOnServer);
			chkTrack.setFont(new Font("Arial", Font.BOLD, 13));
			detailDlg.add(chkTrack, BorderLayout.NORTH);

			JPanel centerPanel = new JPanel(new BorderLayout());
			if (isTrackedOnServer && historyArr.size() > 0) {
				DefaultCategoryDataset dataset = new DefaultCategoryDataset();
				for (JsonElement e : historyArr) {
					JsonObject h = e.getAsJsonObject();
					dataset.addValue(h.get("value").getAsLong(), "Price", h.get("key").getAsString().substring(5, 16));
				}
				JFreeChart chart = ChartFactory.createLineChart("Price History Trend", "Scan Date", "VND", dataset);
				centerPanel.add(new ChartPanel(chart), BorderLayout.CENTER);
			} else {
				JLabel msgLabel = new JLabel(
						"<html><center>No price data available yet.<br>Please enable 'Track' and return after the server updates prices.</center></html>",
						JLabel.CENTER);
				msgLabel.setFont(new Font("Arial", Font.ITALIC, 16));
				msgLabel.setForeground(Color.GRAY);
				centerPanel.add(msgLabel, BorderLayout.CENTER);
			}
			detailDlg.add(centerPanel, BorderLayout.CENTER);

			StringBuilder sb = new StringBuilder("USER REVIEWS:\n");
			for (JsonElement e : reviewsArr) {
				sb.append("- ").append(e.getAsString()).append("\n\n");
			}
			JTextArea txtReviews = new JTextArea(sb.toString());
			txtReviews.setEditable(false);
			txtReviews.setLineWrap(true);
			detailDlg.add(new JScrollPane(txtReviews), BorderLayout.SOUTH);

			chkTrack.addActionListener(e -> {
				try {
					boolean selected = chkTrack.isSelected();
					connection.sendRequest("{\"action\":\"TOGGLE_TRACK\", \"productId\":\"" + productId
							+ "\", \"isTracked\":" + selected + "}");
					productObj.addProperty("isTracked", selected);
					refreshList();

					if (selected) {
						JOptionPane.showMessageDialog(detailDlg,
								"Tracking started. The chart will update after the next server sync (06:00).");
					}
					System.out.println("[INFO] Tracking status changed for " + productId + " to " + selected);
				} catch (Exception ex) {
					System.err.println("[ERROR] Failed to toggle tracking: " + ex.getMessage());
				}
			});

		} catch (Exception e) {
			System.err.println("[ERROR] Failed to fetch product details: " + e.getMessage());
		}

		detailDlg.setVisible(true);
	}

	public static void main(String[] args) {
	    System.setProperty("awt.useSystemAAFontSettings","on");
	    System.setProperty("swing.aatext", "true");

	    System.setProperty("flatlaf.uiScale", "1.1");
	    
	    try {
	        UIManager.setLookAndFeel(new FlatLightLaf());
	    } catch (Exception ex) {
	        System.err.println("Failed to initialize LaF");
	    }

	    String host = args.length > 0 ? args[0] : "localhost";
	    SwingUtilities.invokeLater(() -> new TikiClientApp(host).setVisible(true));
	}
}
