package edu.isi.category;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;

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
import edu.isi.index.MongoDBHandler.DB_COLLECTIONS;
import edu.isi.index.MongoDBHandler.categoriesLinks_SCHEMA;
import edu.isi.index.MongoDBHandler.categories_SCHEMA;

public class CategoryTreeBuilder {
	private final int seedCategoryId;
	private final int depth;
	
	private static Logger logger = LoggerFactory.getLogger(CategoryTreeBuilder.class);

	public CategoryTreeBuilder(int seedCategory, int depth) {
		super();
		this.seedCategoryId = seedCategory;
		this.depth = depth;
	}
	
	public CategoryTree buildTree(DB wikiDB) {
		
		/** Create the tree and root **/
		DBCollection categoriesColl = wikiDB.getCollection(DB_COLLECTIONS.categories.name());
		DBCollection categoriesLinksColl = wikiDB.getCollection(DB_COLLECTIONS.categoriesLinks.name());
		
		DBObject seedCatObj =  categoriesColl.findOne(new BasicDBObject(categories_SCHEMA._id.name(), seedCategoryId));
		if (seedCatObj == null) {
			logger.error("Category object not found with id: " + seedCategoryId);
			return null;
		}
		String seedCatLabel = seedCatObj.get(categories_SCHEMA.name.name()).toString();
		CategoryNode root = new CategoryNode(seedCategoryId, seedCatLabel, 0);
		CategoryTree tree = new CategoryTree();
		tree.setRoot(root);
		
		/** Add nodes to the tree **/
		Queue<CategoryNode> currentNodes = new LinkedList<CategoryNode>();
		currentNodes.add(root);
		Queue<CategoryNode> nextDepthNodes = new LinkedList<CategoryNode>();
		int currentDepth = 0;
		while (!currentNodes.isEmpty() && currentDepth < depth ) {
			CategoryNode node = currentNodes.poll();
			int nodeId = node.getId();
			
			/** Get all the links **/
			DBCursor childrenCursor = categoriesLinksColl.find(new BasicDBObject(categoriesLinks_SCHEMA.categoryId.name(), nodeId));
			while (childrenCursor.hasNext()) {
				DBObject childObj = childrenCursor.next();
				int childId = Integer.parseInt(childObj.get(categoriesLinks_SCHEMA.parentCategoryId.name()).toString());
				String childLabel = childObj.get(categoriesLinks_SCHEMA.parentCategoryLabel.name()).toString();
				
				CategoryNode childNode = new CategoryNode(childId, childLabel, currentDepth+1);
				node.addChild(childNode);
				nextDepthNodes.add(childNode);
			}
			
			if (currentNodes.isEmpty()) {
				currentNodes.addAll(nextDepthNodes);
				nextDepthNodes.clear();
				currentDepth++;
			}
		}
		return tree;
	}

	public static void main(String[] args) {
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
		CategoryTreeBuilder builder = new CategoryTreeBuilder(12, 3);
		CategoryTree tree = builder.buildTree(m.getDB(MongoDBHandler.DB_NAME));
		tree.print();

	}

}
