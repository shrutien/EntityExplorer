package edu.isi.category;

import org.json.JSONException;
import org.json.JSONObject;

public class CategoryTree {
	private CategoryNode root;

	public CategoryNode getRoot() {
		return root;
	}

	public void setRoot(CategoryNode root) {
		this.root = root;
	}

	@Override
	public String toString() {
		return "CategoryTree [root=" + root + "]";
	}

	public void print() {
		System.out.println(root.toString());
	}
	
	public JSONObject getJSONRepresentation() throws JSONException {
		return root.getJSONRepresentation();
	}
}
