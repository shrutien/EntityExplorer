package edu.isi.index;

import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.MongoOptions;

public class MongoDBHandler {
	public static String HOSTNAME = "localhost";
	public static String DB_NAME = "wiki3";
	
	
	
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
	
	public static Mongo getNewMongoConnection() throws UnknownHostException, MongoException {
		MongoOptions opt = new MongoOptions();
		opt.setAutoConnectRetry(true);
		int connectTimeoutDur = new Long(TimeUnit.SECONDS.toMillis(30)).intValue();
		opt.setConnectTimeout(connectTimeoutDur);
		return new Mongo(HOSTNAME, opt);
	}
	
	public static Mongo getNewFusionMongoConnection() throws UnknownHostException, MongoException{
		MongoOptions opt = new MongoOptions();
		opt.setAutoConnectRetry(true);
		int connectTimeoutDur = new Long(TimeUnit.SECONDS.toMillis(30)).intValue();
		opt.setConnectTimeout(connectTimeoutDur);
		return new Mongo("fusion.adx.isi.edu", opt);
	}
}
