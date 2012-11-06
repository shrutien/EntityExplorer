package edu.isi.index;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import edu.isi.index.MongoDBHandler.categories_SCHEMA;

public class CategoryManager {
	private AtomicInteger categoryIndexCounter = new AtomicInteger();
	private DBCollection categoriesColl; 
	private static Logger logger = LoggerFactory.getLogger(CategoryManager.class);
	
	public CategoryManager(DBCollection categoriesColl) {
		this.categoriesColl = categoriesColl;
	}

	public int getCategoryID(String categoryTitle) {
		DBObject query = new BasicDBObject(categories_SCHEMA.name.name(), categoryTitle);
		DBObject catObj = categoriesColl.findOne(query);
		
		if (catObj == null) { // If the category is not already present in the table, add it
			int newIndex = categoryIndexCounter.incrementAndGet();
			query.put(categories_SCHEMA._id.name(), newIndex);
			try {
				categoriesColl.insert(query);
			} catch (MongoException e) {
				logger.error("Error inserting category in the Categories collection!", e);
			}
			return newIndex;
		} else {
			return Integer.parseInt(catObj.get(categories_SCHEMA._id.name()).toString());
		}
	}
}
