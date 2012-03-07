package recommendation;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInteractionType;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class PredictiveWords {

	public static Set<Long> APP_USERS;
	public static Set<Long> ALL_USERS;
	public static Map<Long,String> UID_2_NAME;	

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

	public static void main(String[] args) throws SQLException, FileNotFoundException {				
		PredictiveWords p = new PredictiveWords();				
		p.getAllComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);		
	}

	public static Interaction getUserComments(EInteractionType type, EDirectionType dir) throws SQLException {
		Interaction i = new Interaction();

		String[] tables = {"linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){
			String sql_query = "SELECT uid, from_id, message FROM " + table;

			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				// INCOMING if in correct order
				long TARGET_ID = result.getLong(1);
				long FROM_ID = result.getLong(2);
				String message = result.getString(3);			
				i.addInteraction(TARGET_ID, FROM_ID, dir, message);
			}
			statement.close();
		}			

		return i;		
	}

	public void getAllComments(EInteractionType type, EDirectionType dir) throws SQLException, FileNotFoundException{		
		Interaction i = getUserComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);

		//PrintWriter writer = new PrintWriter("outgoing.txt");
		//writer.println("//outgoing comments");		

		int totalUsers = 0;
		int totalComments = 0;
		
		for (long uid : i.getAllInteractions().keySet()) {

			totalUsers = i.getAllInteractions().size();
			
			String uid_name = UID_2_NAME.get(uid);				
			Set<Long> inter = i.getInteractions(uid);
			ArrayList<String> messages = i.getMessages(uid);						
						
			for (Long uid2 : inter) {
				if (messages.size() > 0){					
					//System.out.println("=====================================");
					//System.out.println(uid + ", " + uid_name + " -- " + type + ": " + messages.size());
					String uid2_name = UID_2_NAME.get(uid2);					
					for (String message : messages){
						//System.out.println("(" + uid_name + "->" + uid2_name + ":" + message + ")");
						totalComments++;
					}
				}
			}
			
			System.out.println(totalComments + " " + totalUsers);
			
		}
		//	writer.close();
	}

}
