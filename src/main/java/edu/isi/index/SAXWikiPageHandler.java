package edu.isi.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import edu.isi.index.MongoDBHandler.categoriesLinks_SCHEMA;
import edu.isi.index.MongoDBHandler.categories_SCHEMA;
import edu.isi.index.MongoDBHandler.pagesAndCategories_SCHEMA;
import edu.isi.index.MongoDBHandler.pagesInfoboxAttributes_SCHEMA;
import edu.isi.index.MongoDBHandler.pages_SCHEMA;
import edu.isi.index.WikipediaIndexer.DB_COLLECTIONS;
import edu.jhu.nlp.wikipedia.PageCallbackHandler;
import edu.jhu.nlp.wikipedia.WikiPage;

public class SAXWikiPageHandler implements PageCallbackHandler {
	private int wikiPagescounter = 0;
	private int catPagescounter = 0;
	private AtomicInteger pageIndexCounter = new AtomicInteger();
	
	private CategoryManager catMgr;
	private DBCollection pagesColl;
	private DBCollection categoriesColl;
	private DBCollection pagesAndCategoriesColl;
	private DBCollection categoriesLinksColl;
	private DBCollection pagesInfoboxAttributesColl;
	private IndexWriter  indexWriter;
	
	// Regex for parsing infobox attributes
	String re1="(\\|)";	// Single Character |
    String re2="( )";	// White Space 1
    String re3="((?:[a-z][a-z]*[a-z0-9_]*))";	// Alphanum 1
    String re4="( )";	// White Space 2
    String re5="(=)";	// Single Character =
    private Pattern p = Pattern.compile(re1+re2+re3+re4+re5,Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	
	private static Logger logger = LoggerFactory.getLogger(SAXWikiPageHandler.class);
	
	public enum INDEX_FIELD_NAME {
		title, text, category, wikiId, dbPageId
	}
	
	
	public SAXWikiPageHandler(IndexWriter indexWriter, DB wikiDB) {
		this.indexWriter 		= indexWriter;
		pagesColl 				= wikiDB.getCollection(DB_COLLECTIONS.pages.name());
		categoriesColl 			= wikiDB.getCollection(DB_COLLECTIONS.categories.name());
		pagesAndCategoriesColl 	= wikiDB.getCollection(DB_COLLECTIONS.pagesAndCategories.name());
		categoriesLinksColl 	= wikiDB.getCollection(DB_COLLECTIONS.categoriesLinks.name());
		pagesInfoboxAttributesColl = wikiDB.getCollection(DB_COLLECTIONS.pagesInfoboxAttributes.name());
		catMgr 					= new CategoryManager(categoriesColl);
		
		/** Creating indexes **/
		DBObject uniqueIndexOption = new BasicDBObject("unique", true).append("dropDups", true);
		pagesColl.ensureIndex(new BasicDBObject(pages_SCHEMA._id.name(), 1), uniqueIndexOption);
		pagesColl.ensureIndex(new BasicDBObject(pages_SCHEMA.title.name(), 1));
		categoriesColl.ensureIndex(new BasicDBObject(categories_SCHEMA._id.name(), 1), uniqueIndexOption);
		categoriesColl.ensureIndex(new BasicDBObject(categories_SCHEMA.name.name(), 1));
		pagesAndCategoriesColl.ensureIndex(new BasicDBObject(pagesAndCategories_SCHEMA.pageId.name(),1));
		pagesAndCategoriesColl.ensureIndex(new BasicDBObject(pagesAndCategories_SCHEMA.categoryId.name(),1));
		categoriesLinksColl.ensureIndex(new BasicDBObject(categoriesLinks_SCHEMA.categoryId.name(),1));
		pagesInfoboxAttributesColl.ensureIndex(new BasicDBObject(pagesInfoboxAttributes_SCHEMA.pageId.name(), 1));
	}

	@Override
	public void process(WikiPage page) {
		int pageId = pageIndexCounter.incrementAndGet();
		String wikiPageId = page.getID();
		String pageTitle = page.getTitle().trim();
		//logger.info("Page: " + page.getTitle());
		
		try {
			// If it's a category page, save the link b/w category and it's super categories
			if (isCategoryPage(page)) {
				catPagescounter++;
				if (catPagescounter % 10000 == 0)
					logger.info("Done with " + catPagescounter + " category pages!");
				String catTitle = pageTitle.replaceFirst("Category:", "").trim();
				if (catTitle.equals(""))
					return;
				
				/** Save the parent categories for this category **/
				int catID = catMgr.getCategoryID(catTitle);
				Vector<String> parentCategories = page.getCategories();
				if (parentCategories != null && !parentCategories.isEmpty()) {
					for (String parentCategory : parentCategories) {
						int parentCatID = catMgr.getCategoryID(parentCategory);
						DBObject link = new BasicDBObject(categoriesLinks_SCHEMA.categoryId.name(), catID)
							.append(categoriesLinks_SCHEMA.parentCategoryId.name(), parentCatID)
							.append(categoriesLinks_SCHEMA.parentCategoryLabel.name(), parentCategory);
						categoriesLinksColl.insert(link);	
					}
				}
			} 
			// Ignore if it is a redirect
			else if (page.isDisambiguationPage() || page.isRedirect() || page.isStub()) {
				// Do nothing
			}
			
			// else index the page and save the category info associated with this page
			else {
				wikiPagescounter++;
				if (wikiPagescounter % 50000 == 0) {
					logger.info("Done with " + wikiPagescounter + " wiki pages!");
				}
				try {
					if (page.getInfoBox() != null) {
						storeInfoboxAttributes(page, pageId);
					}
				} catch (Throwable t) {
					logger.error("Malformed infobox for " + pageId);
				}
				
				
				/** Index the page using Lucene **/
				Document pageIndexDoc = new Document();
				pageIndexDoc.add(new Field(INDEX_FIELD_NAME.title.name(), pageTitle, Field.Store.YES, Field.Index.ANALYZED));
				pageIndexDoc.add(new Field(INDEX_FIELD_NAME.dbPageId.name(), Integer.toString(pageId), Field.Store.YES, Field.Index.ANALYZED));
				pageIndexDoc.add(new Field(INDEX_FIELD_NAME.wikiId.name(), wikiPageId, Field.Store.YES, Field.Index.ANALYZED));
				pageIndexDoc.add(new Field(INDEX_FIELD_NAME.text.name(), page.getText(), Field.Store.NO, Field.Index.ANALYZED));
				pageIndexDoc.add(new Field(INDEX_FIELD_NAME.category.name(), page.getCategories().toString(), Field.Store.NO, Field.Index.ANALYZED));
				indexWriter.addDocument(pageIndexDoc);
				
				/** Save the page, category and the category hierarchy information into database **/ 
				// Save the page info
				DBObject pageObj = new BasicDBObject(pages_SCHEMA.title.name(), pageTitle)
					.append(pages_SCHEMA._id.name(), pageId);
				
				try {
					pagesColl.insert(pageObj);
				} catch (MongoException e) {
					if(e.getCode() == 11000) {
						logger.error("Page already exists! " + pageTitle + " Wiki ID: " + page.getID() + " App Id: " + pageId);
						return;
					}
				}
				
				// Save the information between page and the assigned category
				Vector<String> categoriesList = page.getCategories();
				if (categoriesList != null && !categoriesList.isEmpty()) {
					for (String category: categoriesList) {
						int catID = catMgr.getCategoryID(category);
						pagesAndCategoriesColl.insert(new BasicDBObject(pagesAndCategories_SCHEMA.pageId.name(), pageId)
								.append(pagesAndCategories_SCHEMA.categoryId.name(), catID));
					}
				}
			}
		} catch (IOException e) {
			logger.error("Error occured while parsing page with title: " + pageTitle + " Wiki ID: " + page.getID() + " App Id: " + pageId);
		}
	}
	
	private List<String> getInfoboxAttributes(String infoboxText) {
		List<String> attributes = new ArrayList<String>();
		Matcher m = p.matcher(infoboxText);
		while (m.find()) {
			attributes.add(m.group(3).trim());
		}
		return attributes;
	}
	
	private void storeInfoboxAttributes (WikiPage page, int pageId) {
		List<String> infoBoxAttributes = getInfoboxAttributes(page.getInfoBox().dumpRaw());
		for (String attr: infoBoxAttributes) {
			pagesInfoboxAttributesColl.insert(new BasicDBObject(pagesInfoboxAttributes_SCHEMA.pageId.name(), pageId)
					.append(pagesInfoboxAttributes_SCHEMA.attribute.name(), attr));
		}
	}

	private boolean isCategoryPage(WikiPage page) {
		return (page.isSpecialPage() && page.getTitle().startsWith("Category:"));
	}
}
