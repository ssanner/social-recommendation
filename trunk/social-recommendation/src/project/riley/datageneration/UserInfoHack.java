package project.riley.datageneration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.nicta.lr.util.SQLUtil;

import project.ifilter.messagefrequency.PredictiveWords;

public class UserInfoHack {

	// top n words to test against
	static int TOPN = DataGeneratorPassiveActive.topWordsN;

	// store messages
	static String MESSAGES_FILE = "user_messages.txt";

	// user mentions
	static Map<Long,Map<Long,boolean[]>> OUTGOING_WORDS = new HashMap<Long,Map<Long,boolean[]>>();
	static Map<Long,Map<Long,boolean[]>> INCOMING_WORDS = new HashMap<Long,Map<Long,boolean[]>>();

	// extract incoming/outgoing messages to corresponding files
	public static void getMessageInfo() throws Exception{		

		//if (new File(OUTGOING_MESSAGES_FILE).exists() && new File(INCOMING_MESSAGES_FILE).exists()){
			//	System.out.println("Messages file already exists");
			//processMessages();
		//}	

		String usersToGet = DataGeneratorPassiveActive.setToString(DataGeneratorPassiveActive.usersSeen);

		PrintWriter messagesWriter = new PrintWriter(MESSAGES_FILE);

		Statement statement = SQLUtil.getStatement();
		ResultSet result;

		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){

			String sql_query = "SELECT uid, from_id, message FROM " + table + " where uid in  (" + usersToGet + ")"; 

			result = statement.executeQuery(sql_query);
			while (result.next()) {
				Long uid = result.getLong(1);
				Long from_id = result.getLong(2);
				String message = result.getString(3);	
				messagesWriter.println(uid + " " + from_id + " " + message.replace("\n", " ").replace("\r", " ").replace(".", " ").replace("!"," ").replace(","," ").replace("?"," ").replace("\""," ").replace("-", " "));
			}
			result.close();

			System.out.println("Written messages for " + table);

		}			
		statement.close();
		messagesWriter.close();
		System.out.println("Messages written to files");

		processMessages();
	}

	// find users who used top n words in messages
	public static void processMessages() throws Exception{
		BufferedReader br = new BufferedReader(new FileReader(MESSAGES_FILE));
		String message;
		String word;
		Long uid = 0L;
		Long from_id = 0L;
		String[] bits;
		ArrayList<String> topNWords = PredictiveWords.getTopN(TOPN);

		while ((message = br.readLine()) != null && message.length() > 0){
			bits = message.split("\\s+");
			uid = Long.parseLong(bits[0]);
			from_id = Long.parseLong(bits[1]);

			Map<Long,boolean[]> outgoingSet = (OUTGOING_WORDS.get(from_id) == null ? new HashMap<Long,boolean[]>() : OUTGOING_WORDS.get(from_id));
			Map<Long,boolean[]> incomingSet = (INCOMING_WORDS.get(uid) == null ? new HashMap<Long,boolean[]>() : INCOMING_WORDS.get(uid));
			
			for (int i = 2; i < bits.length; i++){
				word = bits[i].toLowerCase();
				boolean[] outgoingWords = (outgoingSet.get(uid) == null ? new boolean[TOPN] : outgoingSet.get(uid));
				boolean[] incomingWords = (incomingSet.get(from_id) == null ? new boolean[TOPN] : incomingSet.get(from_id));
				for (int j = 0; j < outgoingWords.length; j++){
					if (!outgoingWords[j]){
						outgoingWords[j] = topNWords.get(j).equals(word);
					}
					if (!incomingWords[j]){
						incomingWords[j] = topNWords.get(j).equals(word);
					}
				}
				outgoingSet.put(uid, outgoingWords);
				incomingSet.put(from_id, outgoingWords);
			}
			OUTGOING_WORDS.put(from_id, outgoingSet);
			INCOMING_WORDS.put(uid, incomingSet);
		}		
	}

	// users who have said a top n word in an outgiong message
	public static boolean[] getSeenOutgoing(long uid, long likeeid) throws Exception{
		return (OUTGOING_WORDS.get(uid) == null ? null : OUTGOING_WORDS.get(uid).get(likeeid));
	}

	// users who have said a top n word in an incoming message
	public static boolean[] getSeenIncoming(long uid, long likeeid) throws Exception{
		return (INCOMING_WORDS.get(uid) == null ? null : INCOMING_WORDS.get(uid).get(likeeid));
	}	

	public static void main(String[] args) throws Exception{
		DataGeneratorPassiveActive.populateCachedData(true);
		getMessageInfo();
		processMessages();		
	}

}
