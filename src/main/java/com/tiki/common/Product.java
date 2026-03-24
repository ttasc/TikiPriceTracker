package com.tiki.common;

import java.io.Serializable;

public class Product implements Serializable {
	private static final long serialVersionUID = 1L;
	private String id;
	private String name;
	private long price;
	private String thumbnail_url;
	private boolean isTracked;

	public Product(String id, String name, long price, String thumbnail, boolean isTracked) {
		this.id = id;
		this.name = name;
		this.price = price;
		this.thumbnail_url = thumbnail;
		this.isTracked = isTracked;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getPrice() {
		return price;
	}

	public String getThumbnail() {
		return thumbnail_url;
	}

	public boolean isTracked() {
		return isTracked;
	}

	public void setTracked(boolean tracked) {
		isTracked = tracked;
	}
}
