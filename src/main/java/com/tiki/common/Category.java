package com.tiki.common;

public class Category {
    private String id;   // manual pair by getCategories()
    private String text; // auto pair
    private String link; // auto pair

    public Category(String id, String text, String link) {
        this.id = id;
        this.text = text;
        this.link = link;
    }
    public Category(String id, String text) {
		this.id = id;
		this.text = text;
	}
	public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }

    public String getLink() { return link; }

    @Override
    public String toString() { return text; }
}
