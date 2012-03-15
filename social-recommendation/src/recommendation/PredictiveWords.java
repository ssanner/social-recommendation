package recommendation;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;


import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInteractionType;
import org.nicta.lr.util.Interaction;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

import com.cybozu.labs.langdetect.LangDetectException;

public class PredictiveWords {

	public static Set<Long> APP_USERS;
	public static Set<Long> ALL_USERS;
	public static Map<Long,String> UID_2_NAME;
	public static String MESSAGES_FILE = "messages.txt";
	public static int minFrequency = 25;
	public static MessageInteraction i;

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
		i = new MessageInteraction();				
		p.processUserMessages(EDirectionType.INCOMING);
		p.buildMessagesDictionary(MESSAGES_FILE);
		//p.ShowCondProbs();
	}
	
	/*
	 * Write all user messages to file and save user interactions
	 */
	public void processUserMessages(EDirectionType dir) throws SQLException, FileNotFoundException {

		PrintWriter writer = new PrintWriter(MESSAGES_FILE);			
		
		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){
			String sql_query = "SELECT uid, from_id, message FROM " + table;

			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				long TARGET_ID = result.getLong(1);
				long FROM_ID = result.getLong(2);
				String message = result.getString(3);
				writer.println(message);
				i.addInteraction(TARGET_ID, FROM_ID, dir, message);
			}
			statement.close();			
		}			

		writer.close();
		System.out.println("Messages written to " + MESSAGES_FILE);
				
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
		
		EDirectionType[] directions = {EDirectionType.INCOMING, EDirectionType.OUTGOING};
		BufferedReader br = new BufferedReader(new FileReader(MessageStringUtil.dictionaryFile));
		String word;
		int frequency;
		String[] wordAndFrequency;	
		StringBuilder builder;
		while ((word = br.readLine()) != null){
			
			// split word and frequency value pairs
			wordAndFrequency = word.split(":");
			word = wordAndFrequency[0];
			builder = new StringBuilder();
			for(String s : wordAndFrequency) {
			    builder.append(s);
			}
			word = builder.toString();
			frequency = Integer.parseInt(wordAndFrequency[wordAndFrequency.length-1]);
			
			// frequency constraint
			if (frequency > minFrequency){
				for (EDirectionType dir : directions){					
					for (long uid : APP_USERS){
						// ???
					}																				
				}
			}			
		}		
	}

}
