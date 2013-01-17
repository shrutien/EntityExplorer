package edu.isi.entityextraction;

import java.util.List;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Triple;

public class Test {
	public static void main (String[] args) {
		String serializedClassifier = "classifiers/english.muc.7class.distsim.crf.ser.gz";

		AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);
		String s2 = "Lionel Messi or Cristiano Ronaldo?";
		
//		System.out.println(classifier.classifyToString(s2));
		//System.out.println(classifier.classifyToCharacterOffsets(s2));
		for (Triple<String, Integer, Integer> triple: classifier.classifyToCharacterOffsets(s2)) {
			System.out.println(s2.substring(triple.second, triple.third));
		}
//		System.out.println(classifier.classifyWithInlineXML(s2));
		for (List<CoreLabel> lcl : classifier.classify(s2)) {
			int i=0;
			for (CoreLabel cl : lcl) {
				System.out.print(i++ + ":");
				System.out.println(cl);
			}
		}
	}
}