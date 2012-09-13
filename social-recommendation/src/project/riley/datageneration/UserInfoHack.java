package project.riley.datageneration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import org.nicta.lr.util.SQLUtil;

import project.ifilter.messagefrequency.PredictiveWords;

public class UserInfoHack {

	// top n words to test against
	public static int TOPN = DataGeneratorPassiveActive.topWordsN;

	// store messages
	static String INCOMING_MESSAGES_FILE = "user_messages_incoming.txt";
	static String OUTGOING_MESSAGES_FILE = "user_messages_outgoing.txt";

	// user mentions
	static Map<Long,boolean[]> INCOMING_WORDS = new HashMap<Long,boolean[]>();
	static Map<Long,boolean[]> OUTGOING_WORDS = new HashMap<Long,boolean[]>();

	// user info based on words
	static String INCOMING = "incoming_seen.txt";
	static String OUTGOING = "outgoing_seen.txt";

	// extract incoming/outgoing messages to corresponding files
	public static void getMessageInfo() throws Exception{		

		if (new File(OUTGOING_MESSAGES_FILE).exists() && new File(INCOMING_MESSAGES_FILE).exists()){
			//	System.out.println("Messages file already exists");
			processMessages();
		}

		StringBuilder users = new StringBuilder();
		for (Long user : DataGeneratorPassiveActive.usersSeen){
			users.append(user + ",");
		}

		String usersToGet = users.toString().substring(0, users.length()-1);
		System.out.println("Extracting users info for: (" + usersToGet + ")");

		PrintWriter outgoingWriter = new PrintWriter(OUTGOING_MESSAGES_FILE);
		PrintWriter incomingWriter = new PrintWriter(INCOMING_MESSAGES_FILE);

		Statement statement = SQLUtil.getStatement();
		ResultSet result;

		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){

			String sql_query = "SELECT uid, message FROM " + table + " where uid in  (" + usersToGet + ")"; // incoming

			result = statement.executeQuery(sql_query);
			while (result.next()) {
				Long uid = result.getLong(1);
				String message = result.getString(2);
				incomingWriter.println(uid + ":" + message);				
			}
			result.close();

			System.out.println("Written incoming messages for " + table);

			sql_query = "SELECT from_id, message FROM " + table + " where from_id in  (" + usersToGet + ")"; // outgoing

			result = statement.executeQuery(sql_query);
			while (result.next()) {
				Long uid = result.getLong(1);
				String message = result.getString(2);
				outgoingWriter.println(uid + " " + message);				
			}
			result.close();

			System.out.println("Written outgoing messages for " + table);
			System.out.println();
		}			
		statement.close();
		incomingWriter.close();
		outgoingWriter.close();
		System.out.println("Messages written to files");

		processMessages();
	}

	// find users who used top n words in messages
	public static void processMessages() throws Exception{
		BufferedReader br = new BufferedReader(new FileReader(OUTGOING_MESSAGES_FILE));
		String message;
		String word;
		Long uid = 0L;
		boolean first = true;
		boolean[] flags = null;
		ArrayList<String> topNWords = PredictiveWords.getTopN(TOPN);

		while ((message = br.readLine()) != null){				
			first = true;								
			StringTokenizer tokens = new StringTokenizer(message);
			while (tokens.hasMoreTokens()){
				word = tokens.nextToken().toLowerCase();
				if (first){
					try {
						uid = Long.parseLong(word);
						flags = (OUTGOING_WORDS.get(uid) == null ? new boolean[TOPN] : OUTGOING_WORDS.get(uid));
					} catch (Exception e){ }
					first = false;
				}				

				for (int i = 0; i < topNWords.size(); i++){
					if (word.equals(topNWords.get(i))){
						flags[i] = true;
						continue;
					}
				}
				OUTGOING_WORDS.put(uid, flags);
			}
		}

		br = new BufferedReader(new FileReader(INCOMING_MESSAGES_FILE));
		while ((message = br.readLine()) != null){			
			first = true;								
			StringTokenizer tokens = new StringTokenizer(message);
			while (tokens.hasMoreTokens()){
				word = tokens.nextToken().toLowerCase();
				if (first){
					try {
						uid = Long.parseLong(word);
						flags = (INCOMING_WORDS.get(uid) == null ? new boolean[TOPN] : INCOMING_WORDS.get(uid));
					} catch (Exception e){ }
					first = false;
				}				

				for (int i = 0; i < topNWords.size(); i++){
					if (word.equals(topNWords.get(i))){
						flags[i] = true;
						continue;
					}
				}
				INCOMING_WORDS.put(uid, flags);
			}
		}

		//writeMessages();
	}

	// write users to files
	public static void writeMessages() throws Exception{
		PrintWriter writer = new PrintWriter(OUTGOING);
		for (Entry<Long, boolean[]> ui : OUTGOING_WORDS.entrySet()){
			writer.print(ui.getKey());
			for (boolean flag : ui.getValue()){
				writer.print(" " + flag);
			}
		}
		writer.println();
		writer.close();

		writer = new PrintWriter(INCOMING);
		for (Entry<Long, boolean[]> ui : INCOMING_WORDS.entrySet()){
			writer.print(ui.getKey());
			for (boolean flag : ui.getValue()){
				writer.print(" " + flag);
			}
		}
		writer.println();
		writer.close();
	}

	// users who have said a top n word in an outgiong message
	public static Map<Long, boolean[]> getSeenOutgoing() throws Exception{
		return OUTGOING_WORDS;
	}

	// users who have said a top n word in an incoming message
	public static Map<Long, boolean[]> getSeenIncoming() throws Exception{
		return INCOMING_WORDS;
	}	

	public static void main(String[] args) throws Exception{
		DataGeneratorPassiveActive.populateCachedData(true);
		getMessageInfo();
	}

}
