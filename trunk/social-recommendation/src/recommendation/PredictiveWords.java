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
		Interaction i = new Interaction(); // currently treat interactions as undirected

		// Repeated calls or direct
		if (type == EInteractionType.ALL_COMMENTS) {
			Interaction link_comments  = getUserComments(EInteractionType.LINK_COMMENTS, dir);
			Interaction post_comments  = getUserComments(EInteractionType.POST_COMMENTS, dir);
			Interaction photo_comments = getUserComments(EInteractionType.PHOTO_COMMENTS, dir);
			Interaction video_comments = getUserComments(EInteractionType.VIDEO_COMMENTS, dir);
			link_comments.addAllInteractions(post_comments);
			link_comments.addAllInteractions(photo_comments);
			link_comments.addAllInteractions(video_comments);
			return link_comments;		
		} else { 	
			// Base case retrieval
			String table = null;
			String target_uid = null;
			String interacting_uid = null;
			switch (type) {
			case LINK_COMMENTS:  table = "linkrLinkComments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case POST_COMMENTS:  table = "linkrPostComments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case PHOTO_COMMENTS: table = "linkrPhotoComments"; target_uid = "uid"; interacting_uid = "from_id"; break; 			
			case VIDEO_COMMENTS: table = "linkrVideoComments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			default: {
				System.out.println("ERROR: Illegal type -- " + type);
				System.exit(1);
			}
			}

			String sql_query = "SELECT " + target_uid + ", " + interacting_uid + ", message FROM " + table;

			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				// INCOMING if in correct order
				long TARGET_ID = result.getLong(1);
				long FROM_ID = result.getLong(2);
				String message = result.getString(3);			
				i.addInteraction(TARGET_ID, FROM_ID, type == EInteractionType.FRIENDS ? EDirectionType.BIDIR : dir, message);
			}
			statement.close();

			return i;
		}
	}

	// 627624281
	public void getAllComments(EInteractionType type, EDirectionType dir) throws SQLException, FileNotFoundException{		
		Interaction i = getUserComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);

		//PrintWriter writer = new PrintWriter("outgoing.txt");
		//writer.println("//outgoing comments");		

		for (long uid : i.getAllInteractions().keySet()) {
			String uid_name = UID_2_NAME.get(uid);				
			Set<Long> inter = i.getInteractions(uid);
			ArrayList<String> messages = i.getMessages(uid);						

			for (Long uid2 : inter) {
				if (messages.size() > 0){					
					System.out.println("=====================================");
					System.out.println(uid + ", " + uid_name + " -- " + type + ": " + messages.size());
					String uid2_name = UID_2_NAME.get(uid2);					
					for (String message : messages){
						System.out.println("(" + uid_name + "->" + uid2_name + ":" + message + ")");
					}
				}
			}
		}
		//	writer.close();
	}

}
