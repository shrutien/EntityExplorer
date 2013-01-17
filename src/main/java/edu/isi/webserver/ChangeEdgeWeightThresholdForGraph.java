package edu.isi.webserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.json.JSONObject;

import edu.isi.wikipediahierarchy.graph.GraphUtil;
import edu.isi.wikipediahierarchy.graph.Node;
import edu.isi.wikipediahierarchy.graph.Node.NODE_TYPE;

public class ChangeEdgeWeightThresholdForGraph extends HttpServlet {
	
	private double minEdgeWeight;
	private double maxEdgeWeight;
	
	private static final long serialVersionUID = 1L;

	private enum SERVLET_PARAM_ATTR {
		graphID, minEdgeWeightThreshold, maxEdgeWeightThreshold
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		minEdgeWeight = Double.parseDouble(request.getParameter(SERVLET_PARAM_ATTR.minEdgeWeightThreshold.name()));
		maxEdgeWeight = Double.parseDouble(request.getParameter(SERVLET_PARAM_ATTR.maxEdgeWeightThreshold.name()));
		int graphID = Integer.parseInt(request.getParameter(SERVLET_PARAM_ATTR.graphID.name()));
		
		
		SimpleWeightedGraph<Node, DefaultWeightedEdge> graph = GraphManager.Instance().getGraph(graphID);
		SimpleWeightedGraph<Node, DefaultWeightedEdge> graphFiltered = filterGraphByEdgeThreshold(graph, minEdgeWeight);
		
		GraphUtil.cleanupGraph(graphFiltered);
		
		System.out.println("Original graph. Vertices count: " + graph.vertexSet().size() + " . Edges count: " + graph.edgeSet().size());
		System.out.println("Filtered graph. Vertices count: " + graphFiltered.vertexSet().size() + " . Edges count: " + graphFiltered.edgeSet().size());
		
		JSONObject graphJson = GraphUtil.createJSONForGraph(graphFiltered, graphID, minEdgeWeight, maxEdgeWeight);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(graphJson.toString());
		response.flushBuffer();
	}

	private SimpleWeightedGraph<Node, DefaultWeightedEdge> filterGraphByEdgeThreshold(SimpleWeightedGraph<Node,DefaultWeightedEdge> graph, 
			double minEdgeWeightThreshold) {
//		Cloner cloner = new Cloner();
//		SimpleWeightedGraph<Node, DefaultWeightedEdge> graphFiltered = cloner.deepClone(graph);
		
		SimpleWeightedGraph<Node, DefaultWeightedEdge> graphFiltered = new SimpleWeightedGraph<Node, DefaultWeightedEdge>(DefaultWeightedEdge.class);
		Map<Integer, Node> dbIDsMap = new HashMap<Integer, Node>();
		Set<Node> pageNodes = new HashSet<Node>();
		for (Node node: graph.vertexSet()) {
			Node nDup = new Node(node.getLabel(), node.getType(), node.getDatabaseId());
			dbIDsMap.put(nDup.getDatabaseId(), nDup);
			graphFiltered.addVertex(nDup);
			if (node.getType() == NODE_TYPE.PAGE_TITLE) {
				pageNodes.add(nDup);
			}
		}
		for (DefaultWeightedEdge edge: graph.edgeSet()) {
			Node sourceNode = dbIDsMap.get(graph.getEdgeSource(edge).getDatabaseId());
			Node targetNode = dbIDsMap.get(graph.getEdgeTarget(edge).getDatabaseId());
			graphFiltered.addEdge(sourceNode, targetNode);
			graphFiltered.setEdgeWeight(graphFiltered.getEdge(sourceNode, targetNode), graph.getEdgeWeight(edge));
		}
		
		List<DefaultWeightedEdge> edgesToBeRemoved = new ArrayList<DefaultWeightedEdge>();
		for (DefaultWeightedEdge edge: graphFiltered.edgeSet()) {
			double edgeWeight = graphFiltered.getEdgeWeight(edge);
			if (edgeWeight < minEdgeWeightThreshold) {
				edgesToBeRemoved.add(edge);
			}
		}
//		System.out.println("Number of edges going to be removed: " + edgesToBeRemoved.size());
		graphFiltered.removeAllEdges(edgesToBeRemoved);
		
		List<Node> nodesToBeRemoved = new ArrayList<Node>();
//		int i=0;
		do {
//			System.out.println("Loop: " + i++);
			nodesToBeRemoved.clear();
			for (Node orig: graphFiltered.vertexSet()) {
				if (orig.getType() == NODE_TYPE.PAGE_TITLE)
					continue;
//				System.out.print("Node: " + orig.getLabel());
				boolean intermediatePageNodeFound = false;
				for (Node node: pageNodes) {
					List<DefaultWeightedEdge> path = DijkstraShortestPath.findPathBetween(graph, orig, node);
					if (path == null)
						continue;
					else {
						for (DefaultWeightedEdge edge : path) {
							if (!graph.getEdgeSource(edge).equals(node) && !graph.getEdgeTarget(edge).equals(node) && 
									(graph.getEdgeSource(edge).getType() == NODE_TYPE.PAGE_TITLE || 
										graph.getEdgeTarget(edge).getType() == NODE_TYPE.PAGE_TITLE))
								 intermediatePageNodeFound = true;
						}
						
					}
					
				}
//				System.out.println(": " + intermediatePageNodeFound);
				if (intermediatePageNodeFound)
					nodesToBeRemoved.add(orig);
			}
//			System.out.println("Number of vertices going to be removed: " + nodesToBeRemoved.size());
			graphFiltered.removeAllVertices(nodesToBeRemoved);
		} while (nodesToBeRemoved.size() != 0);
		
		return graphFiltered;
	}
}
