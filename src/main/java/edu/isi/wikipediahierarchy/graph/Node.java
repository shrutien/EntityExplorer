package edu.isi.wikipediahierarchy.graph;

import java.util.concurrent.atomic.AtomicInteger;

public class Node {
	private final String label;
	private final NODE_TYPE type;
	private final int databaseId;
	private final int id;
	
	private int incomingDegree = 0;
	private int outgoingDegree = 0;
	private int pageCount = 1;
	
	private static AtomicInteger nodeIndexCounter = new AtomicInteger(0);
	
	public enum NODE_TYPE {
		CATEGORY, PAGE_TITLE
	}
	
	public Node(String label, NODE_TYPE type, int dbId) {
		super();
		this.label = label;
		this.type = type;
		this.databaseId = dbId;
		this.id = nodeIndexCounter.incrementAndGet();
	}

	
	public int getDatabaseId() {
		return databaseId;
	}
	
	public int getTotalDegree() {
		return pageCount + incomingDegree + outgoingDegree;
	}


	@Override
	public String toString() {
		return "Node [label=" + label + ", type=" + type + ", databaseId="
				+ databaseId + ", id=" + id + ", incomingDegree="
				+ incomingDegree + ", outgoingDegree=" + outgoingDegree
				+ ", pageCount=" + pageCount + "]";
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + databaseId;
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Node other = (Node) obj;
		if (databaseId != other.databaseId)
			return false;
		if (label == null) {
			if (other.label != null)
				return false;
		} else if (!label.equals(other.label))
			return false;
		if (type != other.type)
			return false;
		return true;
	}


	public String getLabel() {
		return label;
	}

	public NODE_TYPE getType() {
		return type;
	}

	public int getIncomingDegree() {
		return incomingDegree;
	}

	public void setIncomingDegree(int incomingDegree) {
		this.incomingDegree = incomingDegree;
	}

	public int getOutgoingDegree() {
		return outgoingDegree;
	}

	public void setOutgoingDegree(int outgoingDegree) {
		this.outgoingDegree = outgoingDegree;
	}

	public int getId() {
		return id;
	}


	public int getPageCount() {
		return pageCount;
	}


	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}
}
