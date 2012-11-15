package edu.isi.index;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.MongoOptions;

public class MongoDBHandler {
	public static String HOSTNAME = "localhost";
	public static String DB_NAME = "wiki3";
	
	public enum DB_COLLECTIONS {
		pages, categories, categoriesLinks, pagesAndCategories, pagesInfoboxAttributes, pagecache
	}
	
	public enum pages_SCHEMA {
		title, _id
	}
	
	public enum categories_SCHEMA {
		name, _id
	}
	
	public enum pagesAndCategories_SCHEMA {
		pageId, categoryId
	}
	
	public enum categoriesLinks_SCHEMA {
		categoryId, parentCategoryId, parentCategoryLabel
	}
	
	public enum pagesInfoboxAttributes_SCHEMA {
		pageId, attribute
	}
	
	public enum pagecache_SCHEMA {
		pageTitle, forest
	}
	
	public static Mongo getNewMongoConnection() throws UnknownHostException, MongoException {
		MongoOptions opt = new MongoOptions();
		opt.setAutoConnectRetry(true);
		int connectTimeoutDur = new Long(TimeUnit.SECONDS.toMillis(30)).intValue();
		opt.setConnectTimeout(connectTimeoutDur);
		return new Mongo(HOSTNAME, opt);
	}
	
	public static List<Integer> getCategoriesForPage (int pageId, DBCollection pagesAndCategoriesColl) {
		DBCursor catCursor = pagesAndCategoriesColl.find(new BasicDBObject(pagesAndCategories_SCHEMA.pageId.name(), pageId));
	   	List<Integer> categoriesIds = new ArrayList<Integer>();
	   	while(catCursor.hasNext()) {
	   		DBObject relObj = catCursor.next();
	   		Integer catId = Integer.parseInt(relObj.get(pagesAndCategories_SCHEMA.categoryId.name()).toString());
	   		categoriesIds.add(catId);
	   	}
		return categoriesIds;
	}
	
	public static Map<Integer, String> getParentCategoriesForCategory(int categoryId, DBCollection categoriesLinksColl) {
		DBCursor childrenCursor = categoriesLinksColl.find(new BasicDBObject(categoriesLinks_SCHEMA.categoryId.name(), categoryId));
		Map<Integer, String> parentCategoriesIds = new HashMap<Integer, String>();
		while (childrenCursor.hasNext()) {
			DBObject childObj = childrenCursor.next();
			int childId = Integer.parseInt(childObj.get(categoriesLinks_SCHEMA.parentCategoryId.name()).toString());
			String childLabel = childObj.get(categoriesLinks_SCHEMA.parentCategoryLabel.name()).toString();
			parentCategoriesIds.put(childId, childLabel);
		}
		return parentCategoriesIds;
	}
	
	public static Mongo getNewFusionMongoConnection() throws UnknownHostException, MongoException{
		MongoOptions opt = new MongoOptions();
		opt.setAutoConnectRetry(true);
		int connectTimeoutDur = new Long(TimeUnit.SECONDS.toMillis(30)).intValue();
		opt.setConnectTimeout(connectTimeoutDur);
		return new Mongo("fusion.adx.isi.edu", opt);
	}
}
