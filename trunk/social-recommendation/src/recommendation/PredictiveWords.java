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
		PredictiveWords p = new PredictiveWords();
		p.getAllComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
	}

	public static Interaction getUserInteractions(EInteractionType type, EDirectionType dir) 
	throws SQLException
{
	Interaction i = new Interaction(); // currently treat interactions as undirected

	// Repeated calls or direct
	if (type == EInteractionType.ALL_INTER) {
		Interaction all_inter = getUserInteractions(EInteractionType.ALL_LIKES, dir);
		all_inter.addAllInteractions(getUserInteractions(EInteractionType.ALL_COMMENTS, dir));
		all_inter.addAllInteractions(getUserInteractions(EInteractionType.ALL_TAGS, dir));			
		return all_inter;
		
	} else if (type == EInteractionType.REAL) {
		Interaction photo_tags = getUserInteractions(EInteractionType.PHOTO_TAGS, dir);
		Interaction video_tags = getUserInteractions(EInteractionType.VIDEO_TAGS, dir);
		video_tags.addAllInteractions(photo_tags);
		return video_tags;
		
	} else if (type == EInteractionType.VIRTUAL) {
		Interaction real = getUserInteractions(EInteractionType.REAL, dir);
		Interaction virtual = getUserInteractions(EInteractionType.ALL_INTER, dir);
		virtual.removeAllInteractions(real);
		return virtual;
		
	} else if (type == EInteractionType.ALL_LIKES) {
		Interaction link_likes  = getUserInteractions(EInteractionType.LINK_LIKES, dir);
		Interaction post_likes  = getUserInteractions(EInteractionType.POST_LIKES, dir);
		Interaction photo_likes = getUserInteractions(EInteractionType.PHOTO_LIKES, dir);
		Interaction video_likes = getUserInteractions(EInteractionType.VIDEO_LIKES, dir);
		link_likes.addAllInteractions(post_likes);
		link_likes.addAllInteractions(photo_likes);
		link_likes.addAllInteractions(video_likes);
		return link_likes;
		
	} else if (type == EInteractionType.ALL_COMMENTS) {
		Interaction link_comments  = getUserInteractions(EInteractionType.LINK_COMMENTS, dir);
		Interaction post_comments  = getUserInteractions(EInteractionType.POST_COMMENTS, dir);
		Interaction photo_comments = getUserInteractions(EInteractionType.PHOTO_COMMENTS, dir);
		Interaction video_comments = getUserInteractions(EInteractionType.VIDEO_COMMENTS, dir);
		link_comments.addAllInteractions(post_comments);
		link_comments.addAllInteractions(photo_comments);
		link_comments.addAllInteractions(video_comments);
		return link_comments;
		
	} else if (type == EInteractionType.ALL_TAGS) {
		Interaction post_tags  = getUserInteractions(EInteractionType.POST_TAGS, dir);
		Interaction photo_tags = getUserInteractions(EInteractionType.PHOTO_TAGS, dir);
		Interaction video_tags = getUserInteractions(EInteractionType.VIDEO_TAGS, dir);
		post_tags.addAllInteractions(photo_tags);
		post_tags.addAllInteractions(video_tags);
		return post_tags;
		
	} else if (type == EInteractionType.ALL_LINK) {
		Interaction link_comments = getUserInteractions(EInteractionType.LINK_COMMENTS, dir);
		Interaction link_likes    = getUserInteractions(EInteractionType.LINK_LIKES, dir);
		link_comments.addAllInteractions(link_likes);
		return link_comments;
		
	} else if (type == EInteractionType.ALL_POST) {
		Interaction post_comments = getUserInteractions(EInteractionType.POST_COMMENTS, dir);
		Interaction post_likes    = getUserInteractions(EInteractionType.POST_LIKES, dir);
		Interaction post_tags     = getUserInteractions(EInteractionType.POST_TAGS, dir);
		post_comments.addAllInteractions(post_likes);
		post_comments.addAllInteractions(post_tags);
		return post_comments;
		
	} else if (type == EInteractionType.ALL_PHOTO) {
		Interaction photo_comments = getUserInteractions(EInteractionType.PHOTO_COMMENTS, dir);
		Interaction photo_likes    = getUserInteractions(EInteractionType.PHOTO_LIKES, dir);
		Interaction photo_tags     = getUserInteractions(EInteractionType.PHOTO_TAGS, dir);
		photo_comments.addAllInteractions(photo_likes);
		photo_comments.addAllInteractions(photo_tags);
		return photo_comments;
		
	} else if (type == EInteractionType.ALL_VIDEO) {
		Interaction video_comments = getUserInteractions(EInteractionType.VIDEO_COMMENTS, dir);
		Interaction video_likes    = getUserInteractions(EInteractionType.VIDEO_LIKES, dir);
		Interaction video_tags     = getUserInteractions(EInteractionType.VIDEO_TAGS, dir);
		video_comments.addAllInteractions(video_likes);
		video_comments.addAllInteractions(video_tags);
		return video_comments;
		
	} else { 
	
		// Base case retrieval
		String table = null;
		String target_uid = null;
		String interacting_uid = null;
		switch (type) {
			case FRIENDS:        table = "linkrfriends"; target_uid = "uid1"; interacting_uid = "uid2"; break;
			case LINK_LIKES:     table = "linkrlinklikes"; target_uid = "uid"; interacting_uid = "id"; break;
			case LINK_COMMENTS:  table = "linkrlinkcomments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case POST_LIKES:     table = "linkrpostlikes"; target_uid = "uid"; interacting_uid = "id"; break;
			case POST_COMMENTS:  table = "linkrpostcomments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case POST_TAGS:      table = "linkrposttags"; target_uid = "uid1"; interacting_uid = "uid2"; break;
			case PHOTO_LIKES:    table = "linkrphotolikes"; target_uid = "uid"; interacting_uid = "id"; break;
			case PHOTO_COMMENTS: table = "linkrphotocomments"; target_uid = "uid"; interacting_uid = "from_id"; break; 
			case PHOTO_TAGS:     table = "linkrphototags"; target_uid = "uid1"; interacting_uid = "uid2"; break;
			case VIDEO_LIKES:    table = "linkrvideolikes"; target_uid = "uid"; interacting_uid = "id"; break;
			case VIDEO_COMMENTS: table = "linkrvideocomments"; target_uid = "uid"; interacting_uid = "from_id"; break;
			case VIDEO_TAGS:     table = "linkrvideotags"; target_uid = "uid1"; interacting_uid = "uid2"; break;
			default: {
				System.out.println("ERROR: Illegal type -- " + type);
				System.exit(1);
			}
		}
		
		String sql_query = "SELECT " + target_uid + ", " + interacting_uid + " FROM " + table;
		
		Statement statement = SQLUtil.getStatement();
		ResultSet result = statement.executeQuery(sql_query);
		while (result.next()) {
			// INCOMING if in correct order
			long TARGET_ID = result.getLong(1);
			long FROM_ID = result.getLong(2);
			i.addInteraction(TARGET_ID, FROM_ID, type == EInteractionType.FRIENDS ? EDirectionType.BIDIR : dir);
		}
		statement.close();
		
		return i;
	}
}



	public void getAllComments(EInteractionType type, EDirectionType dir) throws SQLException{		
		Interaction i = getUserInteractions(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
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
