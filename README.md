WikipediaHierarchyExtractor
===========================

This is a web-application that allows a user to identify entities from a given text, and then visualize relationships between them by linking them through of Wikipedia categories. Core technologies/frameworks/libraries used are: Java, Maven, MongoDB, Twitter Bootstrap, and Lucene.

### Installation

It can be deployed as a regular Tomcat web-app or in a Jetty container. Certain parameters need to be configured in the web.xml file:

- `LUCENE_INDEX_DIRECTORY_PATH`: Full file path to the directory where your Wikipedia index is stored.
- `ENTITY_EXTRACTION_CLASSIFIER_DATA`: Full file path to the Stanford NER classifier file.
- `MONGODB_DB_NAME`: Name of the MongoDB database name where the information about Wikipedia category links, pages, entities, etc. was stored during the indexing.

