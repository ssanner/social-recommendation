package org.nicta.filters;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;

public class StopWordChecker {

	public HashSet<String> _ignoreWords;
	public final static String IGNORE_WORDS_ABS = "./src/org/nicta/filters/stopwords.txt";
	//public final static String IGNORE_WORDS_ABS = "/home/u4754420/stopwords.txt";
	
	public StopWordChecker() {
		_ignoreWords = new HashSet<String>();
		loadStopWords(IGNORE_WORDS_ABS);
	}

	public StopWordChecker(String src) {
		_ignoreWords = new HashSet<String>();
		loadStopWords(src);
	}

	public void loadStopWords(String src) {
        try {
            String line;
            BufferedReader br = new BufferedReader(new FileReader(src));
            while ((line = br.readLine()) != null) {
                _ignoreWords.add(line.trim().toLowerCase());
            }
            br.close();
        } catch (Exception e) {
            System.out.println("File not found");
            e.printStackTrace();
        }

	}
	
    private boolean malformedWord(String s) {
	    //System.out.println(s);
	    if (!Character.isLetterOrDigit(s.charAt(0))) {
	        return true;
	    }
	    if ((Character.isDigit(s.charAt(0))) && (s.length() <= 3)) {
	        return true;
	    }
	    if (s.length() <= 1) {
	        return true;
	    }
	    return false;
    }

	
	public boolean isStopWord(String s) {
		return malformedWord(s) || _ignoreWords.contains(s);
	}
	
	public static void main(String args[]) {
		StopWordChecker swc = new StopWordChecker();
		Test(swc, "is");
		Test(swc, "Scott");
		Test(swc, "had");
		Test(swc, "went");
		Test(swc, "crawled");
	}
	
	public static void Test(StopWordChecker swc, String s) {
		System.out.println("'" + s + "' is stopword: " + ((swc.isStopWord(s)) ? "yes" : "no"));
	}
}
