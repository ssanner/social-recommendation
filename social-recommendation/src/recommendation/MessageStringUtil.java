package recommendation;

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

/*
 * Process:
 * - Read stop words into a set
 * - Read messages from text file
 * - Add words NOT in stop words set to dictionary (hashmap)
 * - Sort dictionary
 * - Write sorted dictionary to file
 * 
 */
public class MessageStringUtil {
		
	HashMap<String, Integer> dictionary = new HashMap<String, Integer>();
	Set<String> stopWords = new HashSet<String>();
	PrintWriter writer = new PrintWriter("output.txt");
	
	/*
	 * Read stop words to a set
	 */
	public void readStopList(String fileName) throws IOException{
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		String word;
		while ((word = br.readLine()) != null){
			stopWords.add(word);
		}
	}
	
	/*
	 * Tokenize each comment and add to dictionary if NOT a stop word
	 */
	public void tokenize(String comment){
		StringTokenizer tokens = new StringTokenizer(comment, " ");
		while (tokens.hasMoreTokens()){
			String word = tokens.nextToken().toLowerCase();
			if (!stopWords.contains(word)){
				addToDictionary(word);
			}
		}
	}	
	
	/*
	 * Add word to frequency dictionary
	 */
	public void addToDictionary(String str){	
		if (dictionary.containsKey(str)){
			dictionary.put(str, dictionary.get(str)+1);
		} else {
			dictionary.put(str, 1);
		}
	}

	/*
	 * Display frequency dictionary terms
	 * Sorted on frequency then alphabetically
	 */
	public void viewDictionary() throws FileNotFoundException{		
						
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
			System.out.println(key + ":" + dictionary.get(key));
			writeDictionary(key + ":" + dictionary.get(key));
		}
	}
	
	/*
	 * Write frequency dictionary to file
	 */
	public void writeDictionary(String output) throws FileNotFoundException{
		if (writer == null){
			
		}
		writer.println(output);
	}
	
	/*
	 * Close frequency dictionary
	 */
	public void closeWriter(){
		writer.close();
	}
	
	public static void main(String[] args) throws IOException {
		MessageStringUtil test = new MessageStringUtil();
		test.readStopList("stopwords.txt");
		test.tokenize("animal is a test sentence");
		test.tokenize("animal is a test sentence also");
		test.tokenize("animal is a test sentence sick dog cat  sdf");
		test.viewDictionary();
		test.closeWriter();
	}	
	
}
