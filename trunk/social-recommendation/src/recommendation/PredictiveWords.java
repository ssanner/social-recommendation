package recommendation;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInteractionType;
import org.nicta.lr.util.ELikeType;
import org.nicta.lr.util.Interaction;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

import util.Statistics;

import com.cybozu.labs.langdetect.LangDetectException;

public class PredictiveWords {

	public static Set<Long> APP_USERS;
	public static Set<Long> ALL_USERS;
	public static Map<Long,String> UID_2_NAME;
	public static String MESSAGES_FILE = "messages.txt";
	public static int minFrequency = 25;
	public static MessageInteraction messageInteractions;
	public static EDirectionType dir = EDirectionType.INCOMING;

	static {
		try {
			APP_USERS = UserUtil.getAppUserIds();
			ALL_USERS = UserUtil.getUserIds();
			UID_2_NAME = UserUtil.getUserNames();
		} catch (SQLException e) {
			System.out.println(e);
			System.exit(1);
		}
	}	

	public static void main(String[] args) throws Exception {				
		PredictiveWords p = new PredictiveWords();
		messageInteractions = new MessageInteraction();				
		//p.processUserMessages(dir, false);
		//p.buildMessagesDictionary(MESSAGES_FILE);
		//p.ShowCondProbs();
		ExtractRelTables.ShowCondProbs();
	}

	/*
	 * Write all user messages to file and save user interactions
	 */
	public void processUserMessages(EDirectionType dir, Boolean writeMessages) throws SQLException, FileNotFoundException {

		PrintWriter writer = null;
		if (writeMessages){
			writer = new PrintWriter(MESSAGES_FILE);
		}

		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){
			String sql_query = "SELECT uid, from_id, message FROM " + table;

			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				long TARGET_ID = result.getLong(1);
				long FROM_ID = result.getLong(2);
				String message = result.getString(3);
				if (writeMessages){
					writer.println(message);
				}
				messageInteractions.addInteraction(TARGET_ID, FROM_ID, dir, message);
			}
			statement.close();			
		}			

		if (writeMessages){
			writer.close();
			System.out.println("Messages written to " + MESSAGES_FILE);
		}

	}	

	/*
	 * Build message frequency dictionary
	 */
	public void buildMessagesDictionary(String fileName) throws IOException, LangDetectException{
		MessageStringUtil.readStopList();
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		String message;
		int totalComments = 0;
		while ((message = br.readLine()) != null){
			MessageStringUtil.tokenize(message);
			if (totalComments % 10000 == 0){
				System.out.println("Processing comments " + totalComments + " to " + (totalComments+10000));
			}
			totalComments++;
		}
		MessageStringUtil.writeDictionary();
	}

	/*
	 * Display conditional probabilities
	 */
	public static void ShowCondProbs() throws Exception {

		PrintStream log = new PrintStream(new FileOutputStream("cond_probs.txt"));
		Set<Long> id_set = APP_USERS; 		
		System.out.println("*************************");
		log.println("*************************");

		BufferedReader br = new BufferedReader(new FileReader(MessageStringUtil.dictionaryFile));
		String dictWord;
		String word;
		int frequency;
		String[] wordAndFrequency;	
		StringBuilder builder;
		while ((dictWord = br.readLine()) != null){

			// split word and frequency value pairs
			wordAndFrequency = dictWord.split("<>");
			builder = new StringBuilder();
			for(int i = 0; i < wordAndFrequency.length-1; i++) {
				builder.append(wordAndFrequency[i]);
			}
			word = builder.toString();
			frequency = Integer.parseInt(wordAndFrequency[wordAndFrequency.length-1]);			
						
			// frequency constraint
			if (frequency > minFrequency){					
												
			}			
		}		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	// Scott Sanner
	public static HashMap<Long,Integer> GetLikesInteractions(long uid, MessageInteraction i, Map<Long,Set<Long>> id2likes) {
		HashMap<Long,Integer> likes = new HashMap<Long,Integer>();
		Set<Long> others = i.getInteractions(uid).getMessageInteractions().keySet();

		//Set<Long> uid_likes = id2likes.get(uid);
		//if (uid_likes != null)
		//	likes.addAll(uid_likes);

		if (others != null) for (long uid2 : others) {
			if (uid2 == uid)
				continue;

			Set<Long> other_likes = id2likes.get(uid2);
			if (other_likes != null) {
				for (Long item : other_likes) {
					Integer cur_count = likes.get(item);
					likes.put(item, cur_count == null ? 1 : cur_count + 1);
				}
			}
		}

		return likes;
	}

}
