package com.tiki.common;

import java.io.Serializable;

public class Pair<K, V> implements Serializable {
	private static final long serialVersionUID = 1L;
	private K key;
	private V value;

	public Pair(K key, V value) {
		this.key = key;
		this.value = value;
	}

	public K getKey() {
		return key;
	}

	public void setKey(K key) {
		this.key = key;
	}

	public V getValue() {
		return value;
	}

	public void setValue(V value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return key + " : " + value;
	}
}
