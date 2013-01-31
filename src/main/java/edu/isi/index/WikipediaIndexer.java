package edu.isi.index;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;

import edu.jhu.nlp.wikipedia.PageCallbackHandler;
import edu.jhu.nlp.wikipedia.WikiXMLParser;
import edu.jhu.nlp.wikipedia.WikiXMLParserFactory;

public class WikipediaIndexer {

	public static final String INDEX_DIRECTORY = "wikipedia-lucene-index";
	public static final Version APP_LUCENE_VERSION = Version.LUCENE_36;
	private static Logger logger = LoggerFactory.getLogger(WikipediaIndexer.class);
	
	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: WikipediaIndexer [Wikipedia Dump File Path] [MongoDB Database Name]");
			return;
		}
			
		String wikipediaDumpFilePath = args[0];
		String databaseName = args[1];
		MongoDBHandler.setDB_NAME(databaseName);
		
		logger.info("Starting application ...");
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
		
					
		/** Setup the Index writer **/
		IndexWriter indexWriter = null;
		try {
			IndexWriterConfig config = new IndexWriterConfig(APP_LUCENE_VERSION, new StandardAnalyzer(APP_LUCENE_VERSION));
			config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			indexWriter = new IndexWriter(FSDirectory.open(new File(INDEX_DIRECTORY)), config);	
			
			/** Setup the page handler **/
			PageCallbackHandler handler = new SAXWikiPageHandler(indexWriter, wikiDB);
			
			/** Start parsing the dump **/
			WikiXMLParser wxsp = WikiXMLParserFactory.getSAXParser(wikipediaDumpFilePath);
			try {
				wxsp.setPageCallback(handler);
				wxsp.parse();
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			indexWriter.close();
			m.close();
			logger.info("Done indexing the Wikipedia dump!");
		} catch (IOException e) {
			logger.error("Error while setting up the index writer!", e);
		}
	}
}
