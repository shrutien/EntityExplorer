package edu.isi.webserver;

import java.io.IOException;
import java.net.UnknownHostException;
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

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
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
import edu.isi.index.MongoDBHandler.categories_SCHEMA;
import edu.isi.index.MongoDBHandler.pages_SCHEMA;

public class CreateGraphForEntitiesServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(CreateGraphForEntitiesServlet.class);
	
	private enum SERVLET_PARAM_ATTR {
		entities
	}
	
	private enum OUTPUT_JSON_SCHEMA {
		nodes, links, name, group, source, target, value
	}
	
	private enum NODE_TYPE {
		CATEGORY, PAGE_TITLE
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
		
		Set<String> pageTitles = new HashSet<String>();
		// Construct the graph
		UndirectedGraph<String, DefaultEdge> graph = createGraph(pageIDs, wikiDB, pageTitles);
		
		// Get the JSON of graph to visualize with D3
		JSONObject graphJson = createJSONForGraph(graph, pageTitles);
		
		System.out.println("Size: " + graph.vertexSet().size());
		System.out.println(graphJson.toString());
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(graphJson.toString());
		response.flushBuffer();
	}

	private UndirectedGraph<String, DefaultEdge> createGraph(List<Integer> pageIDs, DB wikiDB, Set<String> pageTitles) {
		DBCollection pagesAndCategoriesColl = wikiDB.getCollection(DB_COLLECTIONS.pagesAndCategories.name());
		DBCollection categoriesLinksColl = wikiDB.getCollection(DB_COLLECTIONS.categoriesLinks.name());
		DBCollection pagesColl = wikiDB.getCollection(DB_COLLECTIONS.pages.name());
		DBCollection categoriesColl = wikiDB.getCollection(DB_COLLECTIONS.categories.name());
		
		UndirectedGraph<String, DefaultEdge> graph = new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);
		Set<String> distantNeighbours = new HashSet<String>();
		Map<String, String> distantNeighboursOrigin = new HashMap<String, String>(); 
		
		for (Integer pageId: pageIDs) {
			List<Integer> categoryIds = MongoDBHandler.getCategoriesForPage(pageId, pagesAndCategoriesColl);
			String pageTitle = pagesColl.findOne(new BasicDBObject(pages_SCHEMA._id.name(), pageId)).get(pages_SCHEMA.title.name()).toString();
			pageTitles.add(pageTitle);
			graph.addVertex(pageTitle);
			for (Integer categoryID: categoryIds) {
				String categoryTitle = categoriesColl.findOne(new BasicDBObject(categories_SCHEMA._id.name(), categoryID))
						.get(categories_SCHEMA.name.name()).toString();
				graph.addVertex(categoryTitle);
				// System.out.println(pageTitle + " : " + categoryTitle);
				if (!pageTitle.equals(categoryTitle))
					graph.addEdge(pageTitle, categoryTitle);
				
				// Add one more level in the graph
				Map<Integer, String> parentCategoryIds = MongoDBHandler.getParentCategoriesForCategory(categoryID, categoriesLinksColl);
				for (String parentCategory: parentCategoryIds.values()) {
					if (!pageTitle.equals(parentCategory)) {
						graph.addVertex(parentCategory);
						graph.addEdge(categoryTitle, parentCategory);
						distantNeighbours.add(parentCategory);
						distantNeighboursOrigin.put(parentCategory,pageTitle);
					}
				}
			}
		}
		
		/** Check if any distant neighbor needs to be removed **/
		System.out.println(distantNeighbours);
		for (String distNbr: distantNeighbours) {
			String orig = distantNeighboursOrigin.get(distNbr);
			List<String> nodesToCheck = new ArrayList<String>(pageTitles);
			nodesToCheck.remove(orig);
			boolean importantPathFound = false;
			for (String node: nodesToCheck) {
				List<DefaultEdge> path = DijkstraShortestPath.findPathBetween(graph, distNbr, node);
				if (path == null)
					continue;
				else {
					boolean originEncounteredInPath = false;
					for (DefaultEdge edge : path) {
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
		List<String> verticesToBeRemoved = new ArrayList<String>();
		do {
			verticesToBeRemoved.clear();
			for (String v: graph.vertexSet()) {
				if ((graph.degreeOf(v) == 1 && !pageTitles.contains(v)) || graph.degreeOf(v) == 0)
					verticesToBeRemoved.add(v);
			}
			graph.removeAllVertices(verticesToBeRemoved);
		} while (!verticesToBeRemoved.isEmpty());
		
		return graph;
	}

	private JSONObject createJSONForGraph(UndirectedGraph<String, DefaultEdge> graph, Set<String> pageTitles) {
		JSONObject d3Json = new JSONObject();
		JSONArray nodesArr = new JSONArray();
		JSONArray linksArr = new JSONArray();
		
		Map<String, Integer> nodeIndices = new HashMap<String, Integer>();
		try {
			/** Add the nodes and store their indices in the hashmap **/
			int index = 0;
			for (String node: graph.vertexSet()) {
				JSONObject nodeObj = new JSONObject();
				nodeObj.put(OUTPUT_JSON_SCHEMA.name.name(), node);
				if (pageTitles.contains(node))
					nodeObj.put(OUTPUT_JSON_SCHEMA.group.name(), NODE_TYPE.PAGE_TITLE.name());
				else
					nodeObj.put(OUTPUT_JSON_SCHEMA.group.name(), NODE_TYPE.CATEGORY.name());
				nodeIndices.put(node, index++);
				nodesArr.put(nodeObj);
			}
			
			/** Add the links **/
			for (DefaultEdge edge: graph.edgeSet()) {
				String source = graph.getEdgeSource(edge);
				String target = graph.getEdgeTarget(edge);
				int sourceIndex = nodeIndices.get(source);
				int targetIndex = nodeIndices.get(target);
				
				JSONObject linkObj = new JSONObject();
				linkObj.put(OUTPUT_JSON_SCHEMA.source.name(), sourceIndex);
				linkObj.put(OUTPUT_JSON_SCHEMA.target.name(), targetIndex);
				linksArr.put(linkObj);
			}
			d3Json.put(OUTPUT_JSON_SCHEMA.nodes.name(), nodesArr);
			d3Json.put(OUTPUT_JSON_SCHEMA.links.name(), linksArr);
		} catch (JSONException e) {
			logger.error("Error while creating JSON for D3 visualization!", e);
		}
		return d3Json;
	}
}
