package recommendation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInteractionType;
import org.nicta.lr.util.Interaction;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class PredictiveWords {

	public static void main(String[] args) throws SQLException {
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT uid FROM linkrlinkComments";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			System.out.println(result);
		}
		statement.close();
		
		//PredictiveWords p = new PredictiveWords();
		//p.getAllComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
	}


	public static Interaction getUserComments(EInteractionType type, EDirectionType dir) 
	throws SQLException
{
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
			case LINK_COMMENTS:  table = "linkrlinkcomments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case POST_COMMENTS:  table = "linkrpostcomments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case PHOTO_COMMENTS: table = "linkrphotocomments"; target_uid = "uid"; interacting_uid = "from_id"; break; 			
			case VIDEO_COMMENTS: table = "linkrvideocomments"; target_uid = "uid"; interacting_uid = "from_id"; break;
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
			//long TARGET_ID = result.getLong(1);
			//long FROM_ID = result.getLong(2);
			//i.addInteraction(TARGET_ID, FROM_ID, type == EInteractionType.FRIENDS ? EDirectionType.BIDIR : dir);
			System.out.println(result.getString(3));
		}
		statement.close();
		
		return i;
	}
}
	
	
	public void getAllComments(EInteractionType type, EDirectionType dir) throws SQLException{		
		Interaction i = getUserComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
		for (long uid : ExtractRelTables.APP_USERS) {
			String uid_name = ExtractRelTables.UID_2_NAME.get(uid);				
			Set<Long> inter = i.getInteractions(uid);
			System.out.println(uid + ", " + uid_name + " -- " + type + ": " + (inter == null ? 0 : inter.size()));
			System.out.print(" * [ ");
			boolean first = true;
			if (inter != null) 
				for (Long uid2 : inter) {
					String uid2_name = ExtractRelTables.UID_2_NAME.get(uid2);
					System.out.print((first ? "" : ", ") + uid2_name);
					first = false;
				}
			System.out.println(" ]");
		}		
	}

}
