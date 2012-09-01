package project.riley.datageneration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.nicta.lr.util.SQLUtil;

import project.ifilter.messagefrequency.PredictiveWords;

public class UserInfoHack {

	static String INCOMING_MESSAGES_FILE = "user_messages_incoming.txt";
	static String OUTGOING_MESSAGES_FILE = "user_messages_outgoing.txt";

	static HashSet<Long> INCOMING_SEEN = new HashSet<Long>();
	static HashSet<Long> OUTGOING_SEEN = new HashSet<Long>();
	
	static String INCOMING = "incoming_seen.txt";
	static String OUTGOING = "outgoing_seen.txt";

	public static void getMessageInfo() throws Exception{		

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
			System.out.println(sql_query);

			result = statement.executeQuery(sql_query);
			while (result.next()) {
				Long uid = result.getLong(1);
				String message = result.getString(2);
				incomingWriter.println(uid + ":" + message);				
			}
			result.close();

			System.out.println("Written incoming messages for " + table);

			sql_query = "SELECT from_id, message FROM " + table + " where from_id in  (" + usersToGet + ")"; // outgoing
			System.out.println(sql_query);

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
		System.out.println("Messages written");

		processMessages();
	}

	public static void processMessages() throws Exception{
		BufferedReader br = new BufferedReader(new FileReader(OUTGOING_MESSAGES_FILE));
		String message;
		String word;
		Long uid = 0L;
		boolean first = true;
		ArrayList<String> topNWords = PredictiveWords.getTopN(5);

		outer:
			while ((message = br.readLine()) != null){			
				first = true;								
				StringTokenizer tokens = new StringTokenizer(message);
				while (tokens.hasMoreTokens()){
					word = tokens.nextToken().toLowerCase();
					if (first){
						try {
							uid = Long.parseLong(word);
						} catch (Exception e){
							uid = uid;
						}
						if (OUTGOING_SEEN.contains(uid))
							continue outer;
						first = false;
					}				

					for (String needle : topNWords){
						if (word.equals(needle)){
							//System.out.println(uid + " " + needle + " " + word);
							OUTGOING_SEEN.add(uid);
							continue outer;
						}
					}

				}

			}
		
		br = new BufferedReader(new FileReader(INCOMING_MESSAGES_FILE));
		outer:
			while ((message = br.readLine()) != null){			
				first = true;								
				StringTokenizer tokens = new StringTokenizer(message);
				while (tokens.hasMoreTokens()){
					word = tokens.nextToken().toLowerCase();
					if (first){
						try {
							uid = Long.parseLong(word);
						} catch (Exception e){
							uid = uid;
						}
						if (INCOMING_SEEN.contains(uid))
							continue outer;
						first = false;
					}				

					for (String needle : topNWords){
						if (word.equals(needle)){
							//System.out.println(uid + " " + needle + " " + word);
							INCOMING_SEEN.add(uid);
							continue outer;
						}
					}

				}

			}
		
		writeMessages();
	}

	public static void writeMessages() throws Exception{
		PrintWriter writer = new PrintWriter(OUTGOING);
		for (Long uid : OUTGOING_SEEN)
			writer.println(uid);
		writer.close();
		
		writer = new PrintWriter(INCOMING);
		for (Long uid : INCOMING_SEEN)
			writer.println(uid);
		writer.close();
	}
	
	public static void main(String[] args) throws Exception{
		DataGeneratorPassiveActive.populateCachedData(true);
		getMessageInfo();
	}

}
