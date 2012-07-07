package project.ifilter.messagefrequency;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.nicta.lr.util.Configuration;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;

/*
 * Process:
 * - Read stop words into a set
 * - Read messages from text file
 * - Ensure they're English
 * - Add words NOT in stop words set to dictionary (hashmap)
 * - Sort dictionary
 * - Write sorted dictionary to file
 * 
 */
public class MessageStringUtil {

	static HashMap<String, Integer> dictionary = new HashMap<String, Integer>();
	static Set<String> stopWords = new HashSet<String>();
	static String stopList = "messagesStopWords.txt";
	static String dictionaryFile = "dictionary.txt";
	static boolean setProfile = false;

	/*
	 * Read stop words to a set
	 */
	public static void readStopList() throws IOException, LangDetectException{		
		BufferedReader br = new BufferedReader(new FileReader(stopList));
		String word;
		while ((word = br.readLine()) != null){
			stopWords.add(word);
		}
	}

	/*
	 * Tokenize each comment and add to dictionary if NOT a stop word
	 */
	public static void tokenize(String comment) throws LangDetectException{
		if (!isEnglish(comment)){
			return;
		}		
		StringTokenizer tokens = new StringTokenizer(comment, " ");
		while (tokens.hasMoreTokens()){
			String word = tokens.nextToken().toLowerCase();
			word = word.replace("!", "");
			word = word.replace(".", "");
			word = word.replace(",", "");
			word = word.replace("-", "");
			if (word.length() > 0 && !stopWords.contains(word)){
				addToDictionary(word);
			}
		}
	}	

	/*
	 * English words only
	 */
	public static boolean isEnglish(String word) throws LangDetectException{
		if(!setProfile){
			DetectorFactory.loadProfile(Configuration.LANG_PROFILE_FOLDER);
			setProfile = true;
		}

		try {
			Detector messageDetector = DetectorFactory.create();
			messageDetector.append(word);				
			String messageLang = messageDetector.detect();				

			if (!messageLang.equals("en")) {
				return false;
			}
		} 
		catch (LangDetectException e) {			
			return false;
		}

		return true;
	}

	/*
	 * Add word to frequency dictionary
	 */
	public static void addToDictionary(String str){	
		if (dictionary.containsKey(str)){
			dictionary.put(str, dictionary.get(str)+1);
		} else {
			dictionary.put(str, 1);
		}
	}

	/*
	 * Write frequency dictionary terms tp file
	 * Sorted on frequency then alphabetically
	 */
	public static void writeDictionary() throws FileNotFoundException{		

		PrintWriter writer = writer = new PrintWriter(dictionaryFile);

		Comparator<String> vc = new Comparator<String>(){
			@Override
			public int compare(String a, String b) {
				int compare = dictionary.get(b) - dictionary.get(a);
				if (compare == 0) return a.compareTo(b);
				else return compare;
			}						
		};

		TreeMap<String, Integer> sortedDictionary = new TreeMap(vc);
		sortedDictionary.putAll(dictionary);

		for (String key : sortedDictionary.keySet()){
			writer.println(key + "<>" + dictionary.get(key));
			//System.out.println(key + ":" + dictionary.get(key));
		}		
		writer.close();
	}
	
}
