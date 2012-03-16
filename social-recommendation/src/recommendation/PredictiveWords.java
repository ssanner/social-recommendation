package recommendation;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.nicta.lr.util.SQLUtil;


import com.cybozu.labs.langdetect.LangDetectException;

public class PredictiveWords {

	public static String MESSAGES_FILE = "messages.txt";

	public static void main(String[] args) throws Exception {				
		PredictiveWords p = new PredictiveWords();				
		//p.processUserMessages();
		//p.buildMessagesDictionary(MESSAGES_FILE);
		//p.ShowCondProbs();
		ExtractRelTables.ShowCondProbs();
	}

	/*
	 * Write all user messages to file and save user interactions
	 */
	public void processUserMessages() throws SQLException, FileNotFoundException {

		PrintWriter writer = new PrintWriter(MESSAGES_FILE);

		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){
			String sql_query = "SELECT uid, from_id, message FROM " + table;

			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				String message = result.getString(3);
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

}
