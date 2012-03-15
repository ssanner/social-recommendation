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
import org.nicta.lr.util.ExtractRelTables;
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
		p.processUserMessages(dir, false);
		//p.buildMessagesDictionary(MESSAGES_FILE);
		p.ShowCondProbs();
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
			wordAndFrequency = dictWord.split(":");
			builder = new StringBuilder();
			for(int i = 0; i < wordAndFrequency.length-1; i++) {
				builder.append(wordAndFrequency[i]);
			}
			word = builder.toString();
			frequency = Integer.parseInt(wordAndFrequency[wordAndFrequency.length-1]);

			System.out.println(dictWord + ":" + word);
			
			// frequency constraint
			if (frequency > minFrequency){
				// each user interaction
				for (Long uid : messageInteractions.getAllInteractions().keySet()){
					MessageInteractionHolder mh = messageInteractions.getAllInteractions().get(uid);
					// each user interacted with
					for (Long uid2 : mh.getMessageInteractions().keySet()){
						// each word in the interaction
						for (String mword : mh.getMessageInteractions().get(uid2)){
							
							//System.out.println(word +  ":" + mword);
							
							// check if current dictionary word was used during interaction
							if (word.equals(mword)) {

								System.out.println(UID_2_NAME.get(uid) + "->" + UID_2_NAME.get(uid2) + ":" + word);
								
								// Scott Sanner
								System.out.println("=========================");
								log.println("=========================");
								Map<Long,Set<Long>> id2likes = UserUtil.getLikes(ELikeType.ALL);

								// Number of friends who also like the same thing
								double[] prob_at_k   = new double[10];
								double[] stderr_at_k = new double[10];
								for (int k = 1; k <= 10; k++) {

									ArrayList<Double> probs = new ArrayList<Double>();

									String uid_name = UID_2_NAME.get(uid2);
									HashMap<Long,Integer> other_likes_id2count = GetLikesInteractions(uid2, messageInteractions, id2likes);
									//P(like | friend likes) = P(like and friend likes) / P(friend likes)
									//                       = F(like and friend likes) / F(friend likes)
									Set<Long> other_likes_ids = ExtractRelTables.ThresholdAtK(other_likes_id2count, k);
									Set<Long> tmp = id2likes.get(uid2);
									Set<Long> likes_intersect_other_likes_ids = tmp == null ? null : new HashSet<Long>(tmp);
									if (likes_intersect_other_likes_ids == null && other_likes_ids.size() > 0) 
										probs.add(0d); // uid didn't like anything
									else if (other_likes_ids.size() > 0) {
										likes_intersect_other_likes_ids.retainAll(other_likes_ids);
										probs.add((double)likes_intersect_other_likes_ids.size() / (double)other_likes_ids.size());
									} // else (other_likes_ids.size() == 0) -- friends didn't like anything so undefined

									if (probs.size() > 10) {
										String line = "** " + ELikeType.ALL + " likes | " + word + " word & " + dir + " & >" + k + " likes " + ": " +
										(ExtractRelTables._df.format(Statistics.Avg(probs)) + " +/- " + ExtractRelTables._df.format(Statistics.StdError95(probs)) + " #" + probs.size() + " [ " + ExtractRelTables._df.format(Statistics.Min(probs)) + ", " + ExtractRelTables._df.format(Statistics.Max(probs)) + " ]");
										log.println(line);
										log.flush();
										System.out.println(line);
										prob_at_k[k-1]   = Statistics.Avg(probs);
										stderr_at_k[k-1] = Statistics.StdError95(probs);
									} else {
										prob_at_k[k-1]   = Double.NaN;
										stderr_at_k[k-1] = Double.NaN;
									}

								}
								//data.put((dir.index() - 1)*110 + (ltype.index() - 1)*22 + itype.index(), prob_at_k);
								//data.put(330 + (dir.index() - 1)*110 + (ltype.index() - 1)*22 + itype.index(), stderr_at_k);
								System.out.println("=========================");
								log.println("=========================");
								log.close();

							}														
						}
					}
				}								
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
