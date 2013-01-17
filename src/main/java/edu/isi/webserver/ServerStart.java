package edu.isi.webserver;

import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.webserver.CategoryHierarchiesLookup.CONTEXT_PARAM_ATTRIBUTE;
import edu.isi.webserver.CategoryHierarchiesLookup.SERVLET_CONTEXT_ATTRIBUTE;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

public class ServerStart extends HttpServlet {
	private static final long serialVersionUID = 1L;

	 private static Logger logger = LoggerFactory.getLogger(ServerStart.class);

	public void init() throws ServletException {

		ServletContext ctx = getServletContext();

		/** Setup the Lucene index directory **/
		logger.info("Setting up Lucene index directory...");
		try {
			String indexDirectoryName = ctx.getInitParameter(CONTEXT_PARAM_ATTRIBUTE.LUCENE_INDEX_DIRECTORY_PATH.name());
			IndexReader indexReader = IndexReader.open(FSDirectory.open(new File(indexDirectoryName)));
			ctx.setAttribute(SERVLET_CONTEXT_ATTRIBUTE.indexSearcher.name(), new IndexSearcher(indexReader));
		} catch (Exception t) {
			logger.error("Error setting up reading from Lucene index directory.", t);
		}
		logger.info("done");
		
		/** Set up the entity extraction engine **/
		logger.info("Setting up entity extraction classifier ...");
		String serializedClassifier = ctx.getInitParameter(CONTEXT_PARAM_ATTRIBUTE.ENTITY_EXTRACTION_CLASSIFIER_DATA.name());
		AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);
		ctx.setAttribute(SERVLET_CONTEXT_ATTRIBUTE.entityExtractorClassifier.name(), classifier);
		logger.info("done");
		
		System.out.println("************");
		System.out.println("Server start servlet initialized successfully..");
		System.out.println("***********");

	}		
}

