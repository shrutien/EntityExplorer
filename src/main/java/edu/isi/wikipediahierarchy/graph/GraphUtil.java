package edu.isi.wikipediahierarchy.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.WeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.wikipediahierarchy.graph.Node.NODE_TYPE;

public class GraphUtil {
	
	private static Logger logger = LoggerFactory.getLogger(GraphUtil.class);
	private enum OUTPUT_JSON_SCHEMA {
		nodes, links, name, group, source, target, value, id, minEdgeWeight, maxEdgeWeight, graphID
	}
	
	public static JSONObject createJSONForGraph(WeightedGraph<Node,DefaultWeightedEdge> graph, int graphID, double minEdgeWeight, double maxEdgeWeight) {
		JSONObject d3Json = new JSONObject();
		JSONArray nodesArr = new JSONArray();
		JSONArray linksArr = new JSONArray();
		
		Map<Node, Integer> nodeIndices = new HashMap<Node, Integer>();
		try {
			/** Add the nodes and store their indices in the hashmap **/
			int index = 0;
			for (Node node: graph.vertexSet()) {
				JSONObject nodeObj = new JSONObject();
				nodeObj.put(OUTPUT_JSON_SCHEMA.name.name(), node.getLabel());
				nodeObj.put(OUTPUT_JSON_SCHEMA.id.name(), node.getId());
				nodeObj.put(OUTPUT_JSON_SCHEMA.group.name(), node.getType().name());
				nodeIndices.put(node, index++);
				nodesArr.put(nodeObj);
			}
			
			/** Add the links **/
			for (DefaultWeightedEdge edge: graph.edgeSet()) {
				Node source = graph.getEdgeSource(edge);
				Node target = graph.getEdgeTarget(edge);
				int sourceIndex = nodeIndices.get(source);
				int targetIndex = nodeIndices.get(target);
				
				JSONObject linkObj = new JSONObject();
				linkObj.put(OUTPUT_JSON_SCHEMA.source.name(), sourceIndex);
				linkObj.put(OUTPUT_JSON_SCHEMA.target.name(), targetIndex);
				linksArr.put(linkObj);
			}
			d3Json.put(OUTPUT_JSON_SCHEMA.nodes.name(), nodesArr);
			d3Json.put(OUTPUT_JSON_SCHEMA.links.name(), linksArr);
			d3Json.put(OUTPUT_JSON_SCHEMA.minEdgeWeight.name(), minEdgeWeight);
			d3Json.put(OUTPUT_JSON_SCHEMA.maxEdgeWeight.name(), maxEdgeWeight);
			d3Json.put(OUTPUT_JSON_SCHEMA.graphID.name(), graphID);
		} catch (JSONException e) {
			logger.error("Error while creating JSON for D3 visualization!", e);
		}
		return d3Json;
	}
	
	public static void cleanupGraph(SimpleWeightedGraph<Node,DefaultWeightedEdge> graph) {
		/** Remove degree 1 vertices that are not page titles **/
		Set<Node> pageTitles = new HashSet<Node>();
		List<Node> verticesToBeRemoved = new ArrayList<Node>();
		do {
			verticesToBeRemoved.clear();
			for (Node v: graph.vertexSet()) {
				if ((graph.degreeOf(v) == 1 && v.getType()!=NODE_TYPE.PAGE_TITLE) || graph.degreeOf(v) == 0)
					verticesToBeRemoved.add(v);
				if (v.getType() == NODE_TYPE.PAGE_TITLE)
					pageTitles.add(v);
			}
			graph.removeAllVertices(verticesToBeRemoved);
		} while (!verticesToBeRemoved.isEmpty());
		
		/** Detect triangles **/
		verticesToBeRemoved.clear();
		for (Node vertex: graph.vertexSet()) {
			if (graph.degreeOf(vertex) == 2) {
				Set<DefaultWeightedEdge> vertexEdges = graph.edgesOf(vertex);
				boolean isConnectedToPageTitle = false;
				boolean otherVertexConnectedToPageTitle = false;
				Node pageTitleVertex = null;
				Node otherVertex = null;
				
				// Identify the page title vertex and the other vertex
				for (DefaultWeightedEdge vertexEdge: vertexEdges) {
					Node source = graph.getEdgeSource(vertexEdge);
					Node target = graph.getEdgeTarget(vertexEdge);
					
					if (pageTitles.contains(source)) {
						isConnectedToPageTitle = true;
						pageTitleVertex = source;
					} else if (pageTitles.contains(target)) {
						isConnectedToPageTitle = true;
						pageTitleVertex = target;
					} else if (source.equals(vertex)) {
						otherVertex = target;
					} else if (target.equals(vertex)) {
						otherVertex = source;
					}
				}
				if (pageTitleVertex != null && graph.containsEdge(pageTitleVertex, otherVertex))
					otherVertexConnectedToPageTitle = true;
				if (isConnectedToPageTitle && otherVertexConnectedToPageTitle) {
					verticesToBeRemoved.add(vertex);
				}
					
			}
		}
		graph.removeAllVertices(verticesToBeRemoved);
	}
}
