package edu.isi.webserver;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jgrapht.WeightedGraph;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;

import edu.isi.index.MongoDBHandler;
import edu.isi.index.MongoDBHandler.DB_COLLECTIONS;
import edu.isi.index.MongoDBHandler.categoriesLinks_SCHEMA;
import edu.isi.index.MongoDBHandler.categories_SCHEMA;
import edu.isi.index.MongoDBHandler.pagesAndCategories_SCHEMA;
import edu.isi.index.MongoDBHandler.pages_SCHEMA;
import edu.isi.wikipediahierarchy.graph.GraphUtil;
import edu.isi.wikipediahierarchy.graph.Node;
import edu.isi.wikipediahierarchy.graph.Node.NODE_TYPE;

public class CreateGraphForEntitiesServlet extends HttpServlet {
	
	private double minEdgeWeight;
	private double maxEdgeWeight;
	private int graphID;
	private static AtomicInteger graphIDGenerator = new AtomicInteger();
	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(CreateGraphForEntitiesServlet.class);
	
	private enum SERVLET_PARAM_ATTR {
		entities
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		/** Setup mongodb **/
		Mongo m = null;
		try {
			m = MongoDBHandler.getNewMongoConnection();
			m.setWriteConcern(WriteConcern.SAFE);
		} catch (UnknownHostException e) {
			logger.error("UnknownHostException", e);
		} catch (MongoException e) {
			logger.error("MongoException", e);
		}
		if(m == null) {
			response.getWriter().write("Error getting connection to MongoDB! Cannot proceed!");
			response.flushBuffer();
			return;
		}
		DB wikiDB = m.getDB(MongoDBHandler.DB_NAME);
		
		/** Parse the entity db page ids received from the client **/
		List<Integer> pageIDs = new ArrayList<Integer>();
		try {
			JSONArray entities = new JSONArray(request.getParameter(SERVLET_PARAM_ATTR.entities.name()));
			for (int i=0; i<entities.length(); i++) {
				pageIDs.add(entities.getInt(i));
			}
		} catch (JSONException e) {
			logger.error("Error getting entities!", e);
		}
		
		// Construct the graph
		SimpleWeightedGraph<Node, DefaultWeightedEdge> graph = createGraph(pageIDs, wikiDB);
		assignDegreesToNodes(graph, wikiDB);
		assignEdgeWeights(graph);
		graphID = graphIDGenerator.incrementAndGet();
		GraphManager.Instance().addGraphToMap(graphID, graph);
		
		logger.info("Graph vertex Size: " + graph.vertexSet().size());
//		System.out.println("Minimum Edge weight: " + minEdgeWeight);
//		System.out.println("Max Edge weight: " + maxEdgeWeight);
		// Get the JSON of graph to visualize with D3
		JSONObject graphJson = GraphUtil.createJSONForGraph(graph, graphID, minEdgeWeight, maxEdgeWeight);
		
		m.close();
		
		// logger.debug(graphJson.toString());
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(graphJson.toString());
		response.flushBuffer();
	}

	private void assignEdgeWeights(WeightedGraph<Node,DefaultWeightedEdge> graph) {
		minEdgeWeight = Double.MAX_VALUE;
		maxEdgeWeight = Double.MIN_NORMAL;
		for (DefaultWeightedEdge edge: graph.edgeSet()) {
			Node source = graph.getEdgeSource(edge);
			Node target = graph.getEdgeTarget(edge);
			
			double weight = 4 / (Math.log(source.getTotalDegree() + target.getTotalDegree()));
//			System.out.println(edge);
//			System.out.println("Weight: " + weight);
			minEdgeWeight = (weight < minEdgeWeight) ? weight : minEdgeWeight;
			maxEdgeWeight = (weight > maxEdgeWeight) ? weight : maxEdgeWeight;
			graph.setEdgeWeight(edge, weight);
		}
		
	}

	private void assignDegreesToNodes(WeightedGraph<Node,DefaultWeightedEdge> graph, DB wikiDB) {
		DBCollection pagesAndCategoriesColl = wikiDB.getCollection(DB_COLLECTIONS.pagesAndCategories.name());
		DBCollection categoriesLinksColl = wikiDB.getCollection(DB_COLLECTIONS.categoriesLinks.name());
		
		for (Node node: graph.vertexSet()) {
			int inDegree = 0;
			int outDegree = 0;
			int numberOfPages = 1;
			if (node.getType() == NODE_TYPE.PAGE_TITLE) {
				outDegree = (int) pagesAndCategoriesColl.count(new BasicDBObject(pagesAndCategories_SCHEMA.pageId.name(), node.getDatabaseId()));
			} else {
				inDegree = (int) categoriesLinksColl.count(new BasicDBObject(categoriesLinks_SCHEMA.parentCategoryId.name(), node.getDatabaseId()));
				outDegree = (int) categoriesLinksColl.count(new BasicDBObject(categoriesLinks_SCHEMA.categoryId.name(), node.getDatabaseId()));
				numberOfPages = (int) pagesAndCategoriesColl.count(new BasicDBObject(pagesAndCategories_SCHEMA.categoryId.name(), node.getDatabaseId()));
			}
//			System.out.println("Node: " + node);
//			System.out.println("In degree: " + inDegree);
//			System.out.println("Out degree: " + outDegree);
//			System.out.println("Number of pages:" + numberOfPages);
			
			node.setIncomingDegree(inDegree);
			node.setOutgoingDegree(outDegree);
			node.setPageCount(numberOfPages);
		}
	}

	private SimpleWeightedGraph<Node, DefaultWeightedEdge> createGraph(List<Integer> pageIDs, DB wikiDB) {
		DBCollection pagesAndCategoriesColl = wikiDB.getCollection(DB_COLLECTIONS.pagesAndCategories.name());
		DBCollection categoriesLinksColl = wikiDB.getCollection(DB_COLLECTIONS.categoriesLinks.name());
		DBCollection pagesColl = wikiDB.getCollection(DB_COLLECTIONS.pages.name());
		DBCollection categoriesColl = wikiDB.getCollection(DB_COLLECTIONS.categories.name());
		
		SimpleWeightedGraph<Node, DefaultWeightedEdge> graph = new SimpleWeightedGraph<Node, DefaultWeightedEdge>(DefaultWeightedEdge.class);
		Set<Node> distantNeighbours = new HashSet<Node>();
		Set<Node> pageTitles = new HashSet<Node>();
		
		Map<Node, Node> distantNeighboursOrigin = new HashMap<Node, Node>(); 
		
		for (Integer pageId: pageIDs) {
			List<Integer> categoryIds = MongoDBHandler.getCategoriesForPage(pageId, pagesAndCategoriesColl);
			String pageTitle = pagesColl.findOne(new BasicDBObject(pages_SCHEMA._id.name(), pageId)).get(pages_SCHEMA.title.name()).toString();
			Node pageNode = new Node(pageTitle, NODE_TYPE.PAGE_TITLE, pageId);
			pageTitles.add(pageNode);
			graph.addVertex(pageNode);
			for (Integer categoryID: categoryIds) {
				String categoryTitle = categoriesColl.findOne(new BasicDBObject(categories_SCHEMA._id.name(), categoryID))
						.get(categories_SCHEMA.name.name()).toString();
				Node categoryNode = new Node(categoryTitle, NODE_TYPE.CATEGORY, categoryID);
				graph.addVertex(categoryNode);
				// System.out.println(pageTitle + " : " + categoryTitle);
				if (!pageTitle.equals(categoryTitle))
					graph.addEdge(pageNode, categoryNode);
				
				// Add one more level in the graph
				Map<Integer, String> parentCategoryIds = MongoDBHandler.getParentCategoriesForCategory(categoryID, categoriesLinksColl);
				for (int parentCategoryId: parentCategoryIds.keySet()) {
					String parentCategory = parentCategoryIds.get(parentCategoryId);
					if (!pageTitle.equals(parentCategory)) {
						Node parentCategoryNode = new Node(parentCategory, NODE_TYPE.CATEGORY, parentCategoryId);
						graph.addVertex(parentCategoryNode);
						graph.addEdge(categoryNode, parentCategoryNode);
						distantNeighbours.add(parentCategoryNode);
						distantNeighboursOrigin.put(parentCategoryNode,pageNode);
					}
				}
			}
		}

		/** Check if any distant neighbor needs to be removed **/
//		System.out.println(distantNeighbours);
		for (Node distNbr: distantNeighbours) {
			Node orig = distantNeighboursOrigin.get(distNbr);
			List<Node> nodesToCheck = new ArrayList<Node>(pageTitles);
			nodesToCheck.remove(orig);
			boolean importantPathFound = false;
			for (Node node: nodesToCheck) {
				List<DefaultWeightedEdge> path = DijkstraShortestPath.findPathBetween(graph, distNbr, node);
				if (path == null)
					continue;
				else {
					boolean originEncounteredInPath = false;
					for (DefaultWeightedEdge edge : path) {
						if (graph.getEdgeSource(edge).equals(orig) || graph.getEdgeTarget(edge).equals(orig))
							originEncounteredInPath = true;
						else
							continue;
					}
					if (!originEncounteredInPath && path.size() < 4)
						importantPathFound = true;
				}
			}
			if (!importantPathFound)
				graph.removeVertex(distNbr);
		}
		
		/** Remove degree 1 vertices that are not page titles **/
		List<Node> verticesToBeRemoved = new ArrayList<Node>();
		do {
			verticesToBeRemoved.clear();
			for (Node v: graph.vertexSet()) {
				if ((graph.degreeOf(v) == 1 && !pageTitles.contains(v)) || graph.degreeOf(v) == 0)
					verticesToBeRemoved.add(v);
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
		return graph;
	}
}
