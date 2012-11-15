package edu.isi.index;

import java.net.UnknownHostException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;

import edu.isi.index.MongoDBHandler.DB_COLLECTIONS;
import edu.isi.index.MongoDBHandler.categories_SCHEMA;
import edu.isi.index.MongoDBHandler.pages_SCHEMA;


public class Playground {
	
	private static Logger logger = LoggerFactory.getLogger(Playground.class);
	
	public static void main(String[] args) {		
		mongoGraphTest();
	}
	
	private static void mongoGraphTest() {
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
		
		DB wikiDB = m.getDB(MongoDBHandler.DB_NAME);
		
		/** Parse the entity db page ids received from the client **/
		List<Integer> pageIDs = new ArrayList<Integer>();
		pageIDs.add(4000);
		pageIDs.add(591524);
		
		Set<String> pageTitles = new HashSet<String>();
		// Construct the graph
		UndirectedGraph<String, DefaultEdge> graph = createGraph(pageIDs, wikiDB, pageTitles);
		System.out.println(graph.vertexSet().size()); // 62
		System.out.println(graph.edgeSet().size());	// 100
		
	}

	private static UndirectedGraph<String, DefaultEdge> createGraph(
			List<Integer> pageIDs, DB wikiDB, Set<String> pageTitles) {
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
					if (!originEncounteredInPath)
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
				if (graph.degreeOf(v) == 1 && !pageTitles.contains(v))
					verticesToBeRemoved.add(v);
			}
			graph.removeAllVertices(verticesToBeRemoved);
		} while (!verticesToBeRemoved.isEmpty());
		
		
		
		return graph;
	}

	private static void graphTest() {
		UndirectedGraph<String, DefaultEdge> g =
	            new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);

	        String v1 = "v1";
	        String v2 = "v2";
	        String v3 = "v3";
	        String v4 = "v4";

	        // add the vertices
	        g.addVertex(v1);
	        g.addVertex(v2);
	        g.addVertex(v3);
	        g.addVertex(v4);
	        g.addVertex(v1);

	        // add edges to create a circuit
	        g.addEdge(v1, v2);
	        g.addEdge(v2, v3);
	        g.addEdge(v3, v4);
	        g.addEdge(v4, v1);
	        
	        System.out.println(g);
		
	}

	public static void lineBreakTest() {
		String moreText = "She said, \"Hello there,\" and then " +
				"went on down the street.  When she stopped " +
				"to look at the fur coats in a shop window, " +
				"her dog growled.  \"Sorry Jake,\" she said... " +
				" \"I didn't know you would take it personally...\"...";
		 
		Locale currentLocale = new Locale ("en","US");
		BreakIterator boundary = BreakIterator.getSentenceInstance(currentLocale);
		boundary.setText(moreText);
		int start = boundary.first();
		int end = boundary.next();

		List<String> entities = new ArrayList<String>();
		
		while (end != BreakIterator.DONE) {
			boolean addToExistingName = false;
			boolean whitespaceBefore = true;
			StringBuilder entityName = new StringBuilder();
			
			String line = moreText.substring(start,end);
			for (int i=0; i<line.length(); i++) {
				char c = line.charAt(i);
				if (c == ' ') {
					whitespaceBefore = true;
					if (addToExistingName)
						entityName.append(" ");
				} else if (Character.isUpperCase(c)) {
					if (whitespaceBefore) {
						entityName.append(c);
						addToExistingName = true;
					} else if (addToExistingName) {
						entityName.append(c);
					}
					whitespaceBefore = false;
				} else if (Character.isLowerCase(c)) {
					if (whitespaceBefore) {
						addToExistingName = false;
						
						if (!entityName.toString().equals("")) {
							entities.add(entityName.toString().trim());
							entityName = new StringBuilder();
						}
					} else if (addToExistingName) {
						entityName.append(c);
					}
					whitespaceBefore = false;
				}
			}
			System.out.println(line);
			System.out.println(entities);
			start = end;
			end = boundary.next();
		}
	}
	
	public static void categoryExtrationTest() {
		String test = "{{Infobox disease\n" + 
				 "| Name = Autism\n" +
				 "| Image = Autism-stacking-cans 2nd edit.jpg\n" +
				 "| Alt = Young red-haired boy facing away from camera, stacking a seventh can atop a column of six food cans on the kitchen floor. An open pantry contains many more cans.\n" +
				 "| Caption = Repetitively stacking or lining up objects is a behavior sometimes associated with individuals with autism.\n" +
				 "| DiseasesDB = 1142\n" +
				 "| ICD10 = {{ICD10|F|84|0|f|80}}\n" +
				 "| term_start = January 20, 2001\n" +
				 "| term_end = January 20, 2009\n" +
				 "| ICD9 = 299.00\n" +
				 "| ICDO =\n" +
				 "| OMIM = 209850\n" +
				 "| MedlinePlus = 001526\n" +
				 "| eMedicineSubj = med\n" +
				 "| eMedicineTopic = 3202\n" +
				 "| eMedicine_mult = {{eMedicine2|ped|180}}\n" +
				 "| MeshID = D001321\n" +
				 "| GeneReviewsNBK = NBK1442\n" +
				 "| GeneReviewsName = Autism overview\n" +
				"}}";
				
				
				String re1="(\\|)";	// Any Single Character 1
			    String re2="( )";	// White Space 1
			    String re3="((?:[a-z][a-z]*[a-z0-9_]*))";	// Alphanum 1
			    String re4="( )";	// White Space 2
			    String re5="(=)";	// Any Single Character 2

//			    Pattern p = Pattern.compile(re1+re2+re3+re4+re5,Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
			    Pattern p = Pattern.compile(re1+re2+re3+re4+re5,Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
				Matcher m = p.matcher(test);
				while (m.find()) {
					System.out.println(m.group(3).trim());
				}
	}
}
