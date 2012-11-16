package edu.isi.webserver;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

import edu.isi.category.CategoryTreeBuilder;
import edu.isi.index.MongoDBHandler;
import edu.isi.index.MongoDBHandler.DB_COLLECTIONS;
import edu.isi.index.MongoDBHandler.pages_SCHEMA;

public class ViewCategoriesForEntity extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(ViewCategoriesForEntity.class);
	
	private enum SERVLET_PARAM_ATTR {
		pageDBID
	}
	
	private enum OUTPUT_JSON_SCHEMA {
		label, children
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		int dbPageID = Integer.parseInt(request.getParameter(SERVLET_PARAM_ATTR.pageDBID.name()));
		
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
		
		DBCollection pagesAndCategoriesColl = wikiDB.getCollection(DB_COLLECTIONS.pagesAndCategories.name());
		DBCollection pagesColl = wikiDB.getCollection(DB_COLLECTIONS.pages.name());
		String pageTitle = pagesColl.findOne(new BasicDBObject(pages_SCHEMA._id.name(), dbPageID))
				.get(pages_SCHEMA.title.name()).toString();
    	
    	/** Construct the tree for each category and store it in the forest JSON **/
    	JSONArray forestArr = new JSONArray();
    	List<Integer> categoryIds = MongoDBHandler.getCategoriesForPage(dbPageID, pagesAndCategoriesColl);
    	for (Integer catId: categoryIds) {
    		CategoryTreeBuilder treeBldr = new CategoryTreeBuilder(catId, 3);
    		try {
				JSONObject treeJson = treeBldr.buildTree(wikiDB).getJSONRepresentation();
				forestArr.put(treeJson);
			} catch (JSONException e) {
				logger.error("Error occured while constructing tree for category with id: " + catId, e);
				continue;
			}
    	}
    	
    	/** Prepare and write the output object **/
    	JSONObject outputJSONObj = new JSONObject();
    	try {
			outputJSONObj.put(OUTPUT_JSON_SCHEMA.label.name(), pageTitle);
			outputJSONObj.put(OUTPUT_JSON_SCHEMA.children.name(), forestArr);
		} catch (JSONException e) {
			logger.error("Error occured while constructing category forest for page with id: " + dbPageID, e);
		}
    	
    	response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		response.getWriter().write(outputJSONObj.toString());
		m.close();
	}
}
