package project.ifilter.messagefrequency;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.nicta.lr.util.SQLUtil;


import com.cybozu.labs.langdetect.LangDetectException;

public class PredictiveWords {

	public static String MESSAGES_FILE = "messages.txt";

	public static void main(String[] args) throws Exception {
		
		//buildMessagesDictionary(true/* trackedUsers*/);
		buildMessagesDictionary(false/* trackedUsers*/);
		//ExtractRelTables.ShowCondProbs();
	}

	/*
	 * Write all user messages to file and save user interactions
	 */
	public static void processUserMessages(boolean trackedUsers) throws SQLException, FileNotFoundException {

		if (new File(MESSAGES_FILE).exists()){
		//	System.out.println("Messages file already exists");
			return;
		}
		
		PrintWriter writer = new PrintWriter(MESSAGES_FILE);

		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){

			String sql_query = "SELECT message FROM " + table + (trackedUsers ? " where uid in (select distinct uid from trackRecommendedLinks)" : "");
			System.out.println(sql_query);

			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				String message = result.getString(1);
				writer.println(message);				
			}
			statement.close();			
		}			

		writer.close();
		System.out.println("Messages written to " + MESSAGES_FILE);
	}	

	/*
	 * Build message frequency dictionary
	 */
	public static void buildMessagesDictionary(boolean trackedUsers) throws Exception{
		
		if (new File(MessageStringUtil.dictionaryFile).exists()){
			//System.out.println("Dictionary file already exists");
			return;
		}
		
		processUserMessages(trackedUsers);
		MessageStringUtil.readStopList();
		BufferedReader br = new BufferedReader(new FileReader(MESSAGES_FILE));
		String message;
		int totalComments = 0;
		while ((message = br.readLine()) != null){
			MessageStringUtil.tokenize(message);
			if (totalComments % 10000 == 0){
				System.out.println("Processing messages " + totalComments + " to " + (totalComments+10000));
			}
			totalComments++;
		}
		MessageStringUtil.writeDictionary();
		System.out.println("Dictionary written to " + MessageStringUtil.dictionaryFile);
	}
	
	/*
	 * return top n words
	 */
	public static ArrayList<String> getTopN(int n) throws Exception{
		ArrayList<String> topN = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(MessageStringUtil.dictionaryFile));
		String message;
		while ((message = br.readLine()) != null && n > 0){
			topN.add(message.split("<>")[0]); // dictionary format: word<>count
			n--;
		}
		return topN;
	}

}
