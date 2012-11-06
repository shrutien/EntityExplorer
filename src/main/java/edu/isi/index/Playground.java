package edu.isi.index;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Playground {
	public static void main(String[] args) {
		
		String test = "{{Infobox disease\n" + 
		 "| Name = Autism\n" +
		 "| Image = Autism-stacking-cans 2nd edit.jpg\n" +
		 "| Alt = Young red-haired boy facing away from camera, stacking a seventh can atop a column of six food cans on the kitchen floor. An open pantry contains many more cans.\n" +
		 "| Caption = Repetitively stacking or lining up objects is a behavior sometimes associated with individuals with autism.\n" +
		 "| DiseasesDB = 1142\n" +
		 "| ICD10 = {{ICD10|F|84|0|f|80}}\n" +
		 "| term_start = January 20, 2001\n" +
		 "| term_end = January 20, 2009\n" +
		 "| ICD9 = 299.00\n" +
		 "| ICDO =\n" +
		 "| OMIM = 209850\n" +
		 "| MedlinePlus = 001526\n" +
		 "| eMedicineSubj = med\n" +
		 "| eMedicineTopic = 3202\n" +
		 "| eMedicine_mult = {{eMedicine2|ped|180}}\n" +
		 "| MeshID = D001321\n" +
		 "| GeneReviewsNBK = NBK1442\n" +
		 "| GeneReviewsName = Autism overview\n" +
		"}}";
		
		
		String re1="(\\|)";	// Any Single Character 1
	    String re2="( )";	// White Space 1
	    String re3="((?:[a-z][a-z]*[a-z0-9_]*))";	// Alphanum 1
	    String re4="( )";	// White Space 2
	    String re5="(=)";	// Any Single Character 2

//	    Pattern p = Pattern.compile(re1+re2+re3+re4+re5,Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
	    Pattern p = Pattern.compile(re1+re2+re3+re4+re5,Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
		Matcher m = p.matcher(test);
		while (m.find()) {
			System.out.println(m.group(3).trim());
		}
	}
}
