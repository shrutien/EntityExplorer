package edu.isi.webserver;

import java.util.HashMap;
import java.util.Map;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import edu.isi.wikipediahierarchy.graph.Node;

public class GraphManager {
	private static Map<Integer, SimpleWeightedGraph<Node, DefaultWeightedEdge>> graphMap; 
	private static GraphManager _InternalInstance;
	
	public static GraphManager Instance() {
		if (_InternalInstance == null) {
			_InternalInstance = new GraphManager();
			graphMap = new HashMap<Integer, SimpleWeightedGraph<Node, DefaultWeightedEdge>>();
		}
		return _InternalInstance;
	}
	
	public void addGraphToMap(int graphID, SimpleWeightedGraph<Node, DefaultWeightedEdge> graph) {
		graphMap.put(graphID, graph);
	}
	
	public SimpleWeightedGraph<Node, DefaultWeightedEdge> getGraph(int graphID) {
		return graphMap.get(graphID);
	}
	
	public void removeGraph (String graphID) {
		graphMap.remove(graphID);
	}
}
