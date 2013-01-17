package edu.isi.webserver;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;

import edu.isi.index.MongoDBHandler;
import edu.isi.lucene.WikipediaLuceneQuery;
import edu.isi.webserver.CategoryHierarchiesLookup.CONTEXT_PARAM_ATTRIBUTE;
import edu.isi.webserver.CategoryHierarchiesLookup.SERVLET_CONTEXT_ATTRIBUTE;
import edu.jhu.nlp.wikipedia.WikiPage;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Triple;

public class IdentifyEntitiesServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(IdentifyEntitiesServlet.class);
	private static String WIKIPEDIA_URL_PREFIX = "http://en.wikipedia.org/wiki?curid=";
	
	private enum SERVLET_PARAM_ATTR {
		text, parser
	}
	
	private enum OUTPUT_JSONSchema {
		entities, title, URL, entityName, dbID
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String text = request.getParameter(SERVLET_PARAM_ATTR.text.name()).trim();
		
		/** Extract the entities **/
//		Set<String> entityNames = getEntities(text);
		@SuppressWarnings("unchecked")
		AbstractSequenceClassifier<CoreLabel> classifier = 
				(AbstractSequenceClassifier<CoreLabel>) request.getServletContext().getAttribute(SERVLET_CONTEXT_ATTRIBUTE.entityExtractorClassifier.name());
		Set<String> entityNames = getEntitiesFromEntityExtractor(text, classifier);
		System.out.println("Entities: " + entityNames);
		
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
		
		/** Get the top WikiPage object for each entity (by doing finding the appropriate wiki page for the entity name) **/
		///// REPLACE THIS CODE ////////////////
		IndexSearcher indexSearcher = null;
		Object indexSearcherObj = request.getServletContext().getAttribute(SERVLET_CONTEXT_ATTRIBUTE.indexSearcher.name());
		if (indexSearcherObj == null) {
			String indexDirectoryName = request.getServletContext().getInitParameter(CONTEXT_PARAM_ATTRIBUTE.LUCENE_INDEX_DIRECTORY_PATH.name());
			IndexReader indexReader = IndexReader.open(FSDirectory.open(new File(indexDirectoryName)));
			indexSearcher = new IndexSearcher(indexReader);
			request.getServletContext().setAttribute(SERVLET_CONTEXT_ATTRIBUTE.indexSearcher.name(), indexSearcher);
		} else {
			indexSearcher = (IndexSearcher) indexSearcherObj;
		}
		////////////////////////////
		
		Map<String, WikiPage> wikiPages = new HashMap<String, WikiPage>();
		for (String entity: entityNames) {
			try {
				entity = entity.replaceAll("[*?.]", "");
				String contextWords = text.replaceAll(entity, "");
				contextWords = contextWords.replaceAll("[*?.]", "");
				WikipediaLuceneQuery wikiQuery = new WikipediaLuceneQuery(entity, contextWords, 1, indexSearcher);
				WikiPage page = wikiQuery.getTopMatchingWikiPage(wikiDB);
				wikiPages.put(entity, page);
			} catch (Exception e) {
				logger.error("Error occured while getting top matching wiki page. Entity: " + entity, e);
				continue;
			}
		}
		
		/** Construct the output JSON array that will hold the list of entities found and the wikipedia pages **/
		JSONArray pagesJsonArray = new JSONArray();
		for (String entity: wikiPages.keySet()) {
			try {
				WikiPage page = wikiPages.get(entity);
				JSONObject pageObj = new JSONObject();
				pageObj.put(OUTPUT_JSONSchema.title.name(), page.getTitle());
				pageObj.put(OUTPUT_JSONSchema.URL.name(), WIKIPEDIA_URL_PREFIX + page.getID());
				pageObj.put(OUTPUT_JSONSchema.entityName.name(), entity);
				pageObj.put(OUTPUT_JSONSchema.dbID.name(), Integer.parseInt(page.getWikiText()));
				pagesJsonArray.put(pageObj);
			} catch (JSONException e) {
				logger.error("Error constructing wiki page JSON object! ", e);
				continue;
			}
		}
		
		JSONObject obj = new JSONObject();
		try {
			obj.put(OUTPUT_JSONSchema.entities.name(), pagesJsonArray);
		} catch (JSONException e) {
			logger.error("Error while constructing JSON object!", e);
		}
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(obj.toString());
		response.flushBuffer();
	}

	private Set<String> getEntitiesFromEntityExtractor(String text, AbstractSequenceClassifier<CoreLabel> classifier) {
		Set<String> entities = new HashSet<String>();
		for (Triple<String, Integer, Integer> triple: classifier.classifyToCharacterOffsets(text)) {
			entities.add(text.substring(triple.second, triple.third));
		}
		return entities;
	}

	/*
	private Set<String> getEntities(String text) {
		Locale currentLocale = new Locale ("en","US");
		BreakIterator boundary = BreakIterator.getSentenceInstance(currentLocale);
		boundary.setText(text);
		int start = boundary.first();
		int end = boundary.next();
		Set<String> entities = new HashSet<String>();
		
		while (end != BreakIterator.DONE) {
			boolean addToExistingName = false;
			boolean whitespaceBefore = true;
			StringBuilder entityName = new StringBuilder();
			
			String line = text.substring(start,end);
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
				if (i == line.length()-1 && !entityName.toString().equals("")) {
					entities.add(entityName.toString().trim());
					entityName = new StringBuilder();
				}
			}
			start = end;
			end = boundary.next();
		}
		return entities;
	}
	*/
}
