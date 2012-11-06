package edu.isi.webserver;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
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

import edu.isi.category.CategoryTree;
import edu.isi.category.CategoryTreeBuilder;
import edu.isi.index.MongoDBHandler;
import edu.isi.lucene.WikipediaLuceneQuery;

public class CategoryHierarchiesLookup extends HttpServlet {

	private Pattern integerPattern = Pattern.compile("^\\d+$");
	private int treeCount = 1;
	private int maxTreeDepth = 3;
	
	private static Logger logger = LoggerFactory.getLogger(CategoryHierarchiesLookup.class);
	private static final long serialVersionUID = 1L;
	
	public enum Output_JSONSchema {
		pageTitle, trees, pageLuceneScore
	}
	
	private enum REQUEST_PARAMETER_NAME {
		entity, contextwords, pagecount, maxtreedepth
	}
	
	private enum SERVLET_CONTEXT_ATTRIBUTE {
		indexSearcher
	}
	
	private enum CONTEXT_PARAM_ATTRIBUTE {
		LUCENE_INDEX_DIRECTORY_PATH
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		/** Parse the input parameters **/
		String entityName = request.getParameter(REQUEST_PARAMETER_NAME.entity.name());
		String contextWords = request.getParameter(REQUEST_PARAMETER_NAME.contextwords.name());
		String treeCount = request.getParameter(REQUEST_PARAMETER_NAME.pagecount.name());
		String treeDepth = request.getParameter(REQUEST_PARAMETER_NAME.maxtreedepth.name());
		
		if (treeCount != null && !treeCount.trim().equals("")) {
			if (integerPattern.matcher(treeCount).find()) {
				this.treeCount = Integer.parseInt(treeCount);
			}
		}
		
		if (treeDepth != null && !treeDepth.trim().equals("")) {
			if (integerPattern.matcher(treeDepth).find()) {
				this.maxTreeDepth = Integer.parseInt(treeDepth);
			}
		}
		
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
		
		/** Get the index searcher for the Lucene **/
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
		
		/** Get all the relevant categories from the Lucene index **/
		WikipediaLuceneQuery luceneQuery = new WikipediaLuceneQuery(entityName, contextWords, this.treeCount, indexSearcher);
		Map<String,List<Integer>> categoryMap = null;
		Map<String, Float> pageScores = null;
		try {
			categoryMap = luceneQuery.getAllCategoryIds(wikiDB);
			pageScores = luceneQuery.getPageScores();
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		
		/** Build the trees and store them in the output JSON object **/
		JSONArray outputJSONArray = new JSONArray();
		for (String pageTitle: categoryMap.keySet()) {
			try {
				List<Integer> categoriesIds = categoryMap.get(pageTitle);
				JSONObject pageObj = new JSONObject();
				pageObj.put(Output_JSONSchema.pageTitle.name(), pageTitle);
				JSONArray treeArray = new JSONArray();
				for (Integer categoryId: categoriesIds) {
					CategoryTreeBuilder treeBuilder = new CategoryTreeBuilder(categoryId, maxTreeDepth);
					CategoryTree categoryTree = treeBuilder.buildTree(wikiDB);
					if (categoryTree != null)
						treeArray.put(categoryTree.getJSONRepresentation());
					else
						logger.error("Error occured while builging tree. Check logs!");
				}
				pageObj.put(Output_JSONSchema.trees.name(), treeArray);
				pageObj.put(Output_JSONSchema.pageLuceneScore.name(), pageScores.get(pageTitle));
				outputJSONArray.put(pageObj);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		m.close();
		
		response.setCharacterEncoding("UTF-8");
		try {
			response.setContentType("application/json");
			response.getWriter().write(outputJSONArray.toString(4));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		response.flushBuffer();
	}
}
