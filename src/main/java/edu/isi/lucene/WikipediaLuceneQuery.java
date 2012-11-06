package edu.isi.lucene;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;

import edu.isi.index.MongoDBHandler;
import edu.isi.index.MongoDBHandler.pagesAndCategories_SCHEMA;
import edu.isi.index.SAXWikiPageHandler.INDEX_FIELD_NAME;
import edu.isi.index.WikipediaIndexer;

public class WikipediaLuceneQuery {
	private String entityName;
	private String contextWords;
	private int resultCount;
	private IndexSearcher indexSearcher;
	private Map<String, Float> pageScores = new HashMap<String, Float>();
	private static Logger logger = LoggerFactory.getLogger(WikipediaLuceneQuery.class);
	
	public WikipediaLuceneQuery(String entityName, String contextWords, int resultCount, IndexSearcher indexSearcher) {
		this.entityName = entityName;
		this.contextWords = contextWords;
		this.resultCount = resultCount;
		this.indexSearcher = indexSearcher;
	}
	
	public Map<String,List<Integer>> getAllCategoryIds(DB wikiDB) 
			throws ParseException, CorruptIndexException, IOException {
		Map<String,List<Integer>> categoriesMap = new HashMap<String, List<Integer>>();
		
		DBCollection pagesAndCategoriesColl = wikiDB.getCollection(WikipediaIndexer.DB_COLLECTIONS.pagesAndCategories.name());
		
		/** Prepare the Lucene query **/
		BooleanQuery finalQuery = new BooleanQuery();
		// Create a query for each term in the entity name
		QueryParser parser = new QueryParser(WikipediaIndexer.APP_LUCENE_VERSION, INDEX_FIELD_NAME.title.name()
				, new StandardAnalyzer(WikipediaIndexer.APP_LUCENE_VERSION));
		Query query = parser.parse(entityName);
		finalQuery.add(query, BooleanClause.Occur.SHOULD);
		
		// Add the context words too
		if (contextWords != null && !contextWords.trim().equals("")) {
			QueryParser parser2 = new QueryParser(WikipediaIndexer.APP_LUCENE_VERSION, INDEX_FIELD_NAME.text.name()
					, new StandardAnalyzer(WikipediaIndexer.APP_LUCENE_VERSION));
			Query query2 = parser2.parse(contextWords);
			finalQuery.add(query2, BooleanClause.Occur.SHOULD);
		}
		
		TopScoreDocCollector collector = TopScoreDocCollector.create(resultCount, true);
	    indexSearcher.search(finalQuery, collector);
	    
	    ScoreDoc[] hits = collector.topDocs().scoreDocs;
	    
	    if (hits.length == 0) {
	    	logger.error("No results found!!!");
	    	return categoriesMap;
	    }
	    
	    // Parse the Lucene results
	    for (int i=hits.length-1; i>=0; i--) {	
	    	 int docId = hits[i].doc;
	         Document d = indexSearcher.doc(docId);
	         String pageTitle = d.get(INDEX_FIELD_NAME.title.name());
	    	 logger.info("Found match! Document matching field: " + pageTitle + " Score: " + hits[i].score);
	    	 pageScores.put(pageTitle, hits[i].score);
	    	 int pageId = Integer.parseInt(d.get(INDEX_FIELD_NAME.dbPageId.name()));

	    	 // Get all the associated categories with that page
	    	 DBCursor catCursor = pagesAndCategoriesColl.find(new BasicDBObject(pagesAndCategories_SCHEMA.pageId.name(), pageId));
	    	 List<Integer> categoriesIds = new ArrayList<Integer>();
	    	 System.out.println("Cat size: " + catCursor.count());
	    	 while(catCursor.hasNext()) {
	    		 DBObject relObj = catCursor.next();
	    		 Integer catId = Integer.parseInt(relObj.get(pagesAndCategories_SCHEMA.categoryId.name()).toString());
	    		 categoriesIds.add(catId);
	    	 }
	    	 categoriesMap.put(pageTitle, categoriesIds);
	    }
		return categoriesMap;
	}
	
	public Map<String, Float> getPageScores() {
		return pageScores;
	}

	public static void main(String[] args) throws ParseException {
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
			logger.error("Error getting connection to MongoDB! Cannot proceed with this thread.");
			return;
		}
		DB wikiDB = m.getDB(MongoDBHandler.DB_NAME);
		
		String indexDirectoryName = WikipediaIndexer.INDEX_DIRECTORY;
		logger.info("Lucene index location: " + new File(indexDirectoryName).getAbsolutePath());
		try {
			if(!new File(indexDirectoryName).exists()) {
				new File(indexDirectoryName).mkdir();
			} else {
				IndexReader indexReader = IndexReader.open(FSDirectory.open(new File(indexDirectoryName)));
				IndexSearcher indexSearcher = new IndexSearcher(indexReader);
				String entity = "Arsenal F.C.";
				String ctxWords = "football";
				WikipediaLuceneQuery qry = new WikipediaLuceneQuery(entity, ctxWords, 1, indexSearcher);
				Map<String,List<Integer>> results = qry.getAllCategoryIds(wikiDB);
				System.out.println(results);
				System.out.println(results.size());
			}
			
		} catch (IOException e) {
			logger.error("Error occured while attempting to setup index reader.", e);
		}
	}
}
