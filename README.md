Entity Explorer
===========================

It is a web-application that allows a user to identify entities from a given text, and then visualize relationships between them by linking them through Wikipedia categories. It generates an interactive graph of relationships, where the user can increase the edge weights to view more specialized relationships. Core technologies/frameworks/libraries used are: Java, D3, Maven, MongoDB, Twitter Bootstrap, and Lucene.

### Installation

This software uses Maven as the build tool, so it is required to be installed on the system (Mac OSX has it already installed). Details for downloading and installing Maven are [here](http://maven.apache.org/download.cgi). Once installed, run `mvn package` from inside the main directory to create a WAR file inside the target folder. It can then be deployed as a regular Tomcat web-app or in a Jetty container (by copying and pasting the WAR file to the webapps folder inside the Tomcat). Certain parameters need to be configured in the web.xml file:

- `LUCENE_INDEX_DIRECTORY_PATH`: Full file path to the directory where your Wikipedia index is stored.
- `ENTITY_EXTRACTION_CLASSIFIER_DATA`: Full file path to the Stanford NER classifier file.
- `MONGODB_DB_NAME`: Name of the MongoDB database name where the information about Wikipedia category links, pages, entities, etc. was stored during the indexing.

### Screenshot

<img alt="Demo picture"
        src="http://isi.edu/~shubhamg/demo.png">

###  Frequently Asked Questions

- **How to create Wikipedia index and store links in MongoDB database?**

	WikipediaIndexer.java creates Lucene index and stores the links in the database. It expects arguments of full file path to Wikipedia dump file and the name of the MongoDB database. Example execution using Maven:
	`mvn exec:java -Dexec.mainClass="edu.isi.index.WikipediaIndexer" -Dexec.args="/tmp/wikipedia/articles.xml.bz2 wikipedia"`

- **How to change the edge weights in the visualization?**

	Use the slider! It still needs some work though.

- **How are entities extracted from the text?**

	Currently we provide choice of following entity extractors:
	* [Stanford NER](http://nlp.stanford.edu/software/CRF-NER.shtml)
	* Basic Capitalization: We treat the words starting with capital letters as entities.

	In future we also plan to integrate the [CALAIS](http://www.opencalais.com/) system
	
## License
This software is licensed under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)
