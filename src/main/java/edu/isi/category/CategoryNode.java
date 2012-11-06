package edu.isi.category;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CategoryNode {
	private final String label;
	private final int id;
	private final int depth;
	private List<CategoryNode> children;
	
	public enum Node_JSONSchema {
		id, label, depth, children
	}
	
	public CategoryNode(int id, String label, int depth) {
		super();
		this.id = id;
		this.label = label;
		this.depth = depth;
		this.children = new ArrayList<CategoryNode>();
	}
	
	public String getLabel() {
		return label;
	}

	public void addChild (CategoryNode node) {
		children.add(node);
	}
	
	public void addChild (int id, String label) {
		addChild(new CategoryNode(id, label, depth+1));
	}
	
	public List<CategoryNode> getChildren() {
		return children;
	}
	
	public boolean hasChildren() {
		return (!children.isEmpty());
	}

	@Override
	public String toString() {
		String indentation = "";
		for (int i=0; i<depth; i++)
			indentation += "----";
		
		StringBuilder out = new StringBuilder();
		out.append(indentation + "CategoryNode [id=" +id + " label=" + label + ", depth=" + depth + "]\n");
		
		for (CategoryNode child : children) {
			out.append(child.toString());
		}
		return out.toString();
	}

	public JSONObject getJSONRepresentation() throws JSONException {
		JSONObject nodeObj = new JSONObject();
		// Add the children
		JSONArray childrenArray = new JSONArray();
		for (CategoryNode childNode : children) {
			childrenArray.put(childNode.getJSONRepresentation());
		}
		// Add the node's information
		nodeObj.put(Node_JSONSchema.label.name(), label)
			.put(Node_JSONSchema.id.name(), id)
			.put(Node_JSONSchema.depth.name(), depth)
			.put(Node_JSONSchema.children.name(), childrenArray);
		return nodeObj;
	}
}
