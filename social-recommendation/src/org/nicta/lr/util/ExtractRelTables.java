package org.nicta.lr.util;

import java.sql.SQLException;
import java.util.*;

//Objective: to define various "groups" from a user-centric perspective and see 
//amount of overlap between users and their local "groups" in terms of overlap 
//in likes.  For starters, my main interest is the conditional probability 
//P(user likes | friend likes, link_type, friend_type), which can be computed 
//from the data in the table generated below.
//
//link_type (note the new subdivisions of group)
//=========
//linkComments
//linkLikes
//posts
//postComments
//postLikes
//postTags
//photos
//photoComments
//photoLikes
//photoTags
//videos
//videoComments
//videoLikes
//videoTags
//groups (all groups of all sizes)
//groups_1-10 (groups with 1-10 users... just count users among our 34,000+ user 
//		     subset since we probably don't have the Facebook group count)
//groups_11-100
//groups_101-1000
//groups_1001+ (if any groups this big)
//likes (union of linkLikes, postLikes, photoLikes, videoLikes)
//
//comments = (union of linkComments, postComments, photoComments, videoComments)
//tags = (union of linkTags, postTags, photoTags, videoTags)
//
//Algorithm: produces table of data for loading into spreadsheet and further analysis
//=========
//
//for each Facebook user_id {
//
//  for friend_type in (virtual, real, either) {
//    for each link_type in (see listing above) {
//
//   	  user_interaction = set of object ids "link_typed" by/with user_id (*not* LinkR likes, but general Facebook likes)
//
//      friend_set = set of *friends* ids that have >= 1 interaction with user_id via link_type;
//      if (friend_type == real)
//         remove friend ids from friend_set who *don't* have at least one photo or video tag with user_id
//      else if (friend_type == virtual)
//         remove friend ids from friend_set who have at least one photo or video tag with user_id
//
//      friend_interaction = set of ids of anything liked by people in friend_set
//
//      output table row with following tab-delimited columns:
//        *int -- user_id,
//        *bool -- is user LinkR_app_user?,
//        *int -- total number of Facebook friends of user_id,
//        *int -- total number of Facebook friends of user_id in our DB (i.e., among 34,000+ users for whom we have wall/friend data),
//        *string -- friend_type
//        *string -- link_type,
//        *int -- size(user_interaction),
//        *int -- size(friend_interaction),
//        *int -- size(user_interaction intersect friend_interaction),
//        *int -- size(user_interaction union friend_interaction)
//      }
//    }
//  }
//}

public class ExtractRelTables {

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
	
	// P(user likes | friend likes, link_type, like_type, friend_type, user_type)
	
	// For each like_type, build HashMap from uid to HashSet of items
	
	// For each interaction_type, build HashMap from uid to HashSet of items
	
	// Do for app users and all users
	
	// LATER:
	// For each friend_type, filter REAL according to only those friends also in photo tags & video tags 

	/**
	 * @param args
	 */
	public static void main(String[] args) throws SQLException {
		
		System.out.println("App users: " + APP_USERS.size());
		System.out.println("All users: " + ALL_USERS.size());	
		
		//ShowLikes("Sonia");
		//ShowInteractions("Sonia");
		ShowCondProbs("Sonia");
		//ShowGroups();
		
		//for (FriendType friend_type : FriendType.values()) {
		//	System.out.println(friend_type.toString());
		//}
	}

	public static Set<Long> GetLikesInteractions(long uid, Interaction i, Map<Long,Set<Long>> id2likes) {
		HashSet<Long> likes = new HashSet<Long>();
		Set<Long> others = i.getInteractions(uid);
		
		//Set<Long> uid_likes = id2likes.get(uid);
		//if (uid_likes != null)
		//	likes.addAll(uid_likes);
		
		if (others != null) for (long uid2 : others) {
			if (uid2 == uid)
				continue;
			
			Set<Long> other_likes = id2likes.get(uid2);
			if (other_likes != null)
				likes.addAll(other_likes);
		}
		
		return likes;
	}
	
	public static void ShowCondProbs(String restriction) throws SQLException {
		
		Set<Long> id_set = APP_USERS;

		for (InteractionType itype : InteractionType.values()) {
			System.out.println("=========================");
			Interaction i = UserUtil.getUserInteractions(itype, false);

			for (LikeType ltype : LikeType.values()) {
				System.out.println("=========================");
				Map<Long,Set<Long>> id2likes = UserUtil.getLikes(ltype);
			
				double accum_prob = 0d;
				for (long uid : id_set) {
					String uid_name = UID_2_NAME.get(uid);
					//if (!uid_name.contains(restriction))
					//	continue;
					Set<Long> other_likes_ids = GetLikesInteractions(uid, i, id2likes);
					//P(like | friend likes) = P(like and friend likes) / P(friend likes)
					//                       = F(like and friend likes) / F(friend likes)
					Set<Long> likes_intersect_other_likes_ids = id2likes.get(uid);
					double prob = 0d;
					if (likes_intersect_other_likes_ids != null && other_likes_ids != null && other_likes_ids.size() > 0) {
						likes_intersect_other_likes_ids.retainAll(other_likes_ids);
						prob = (double)likes_intersect_other_likes_ids.size() / (double)other_likes_ids.size();
					}
					accum_prob += prob;
					//System.out.println(uid + ", " + uid_name + " -- " + ltype + " / " + itype + ": " + prob);
				}
				System.out.println("** " + ltype + " / " + itype + ": " + (accum_prob / id_set.size()));
				System.out.println("=========================");
			}
		}
	}
	
	public static void ShowGroups() throws SQLException {
		
		Map<Long,String> GROUPID_2_NAME   = UserUtil.getGroupNames();
		Map<Long,Set<Long>> UID_2_GROUPID = UserUtil.getUser2Groups();
		Map<Long,Set<Long>> GROUPID_2_UID = UserUtil.getGroup2Users();
		Map<Long,Integer> GROUPID_2_SZ    = UserUtil.getGroupID2Size();
		
		System.out.println("=========================");
		for (long uid : APP_USERS) {
			String uid_name = UID_2_NAME.get(uid);
			Set<Long> groups = UID_2_GROUPID.get(uid);
			System.out.println(uid + ", " + uid_name + " -- #groups: " + (groups == null ? 0 : groups.size()));
			System.out.print(" * [ ");
			boolean first = true;
			if (groups != null) 
				for (Long gid : groups) {
					String  gid_name = GROUPID_2_NAME.get(gid);
					Integer gid_size = GROUPID_2_SZ.get(gid);
					Set<Long> other_users = GROUPID_2_UID.get(gid);
					System.out.print((first ? "" : ", ") + gid_name + ":" + gid_size + "/" + other_users.size());
					first = false;
				}
			System.out.println(" ]");
		}
		System.out.println("=========================");

	}

	public static void ShowLikes(String restriction) throws SQLException {
	
		for (LikeType type : LikeType.values()) {
			System.out.println("=========================");
			Map<Long,Set<Long>> id2likes = UserUtil.getLikes(type);
			for (long uid : APP_USERS) {
				String uid_name = UID_2_NAME.get(uid);
				if (!uid_name.contains(restriction))
					continue;
				Set<Long> likes_ids = id2likes.get(uid);
				System.out.println(uid + ", " + uid_name + " -- " + type + ": " + (likes_ids == null ? 0 : likes_ids.size()));
			}
			System.out.println("=========================");
		}
	}
	
	public static void ShowInteractions(String restriction) throws SQLException {

		for (InteractionType type : InteractionType.values()) {
			System.out.println("=========================");
			Interaction i = UserUtil.getUserInteractions(type, false);
			for (long uid : APP_USERS) {
				String uid_name = UID_2_NAME.get(uid);
				if (!uid_name.contains(restriction))
					continue;
				Set<Long> inter = i.getInteractions(uid);
				System.out.println(uid + ", " + uid_name + " -- " + type + ": " + (inter == null ? 0 : inter.size()));
				System.out.print(" * [ ");
				boolean first = true;
				if (inter != null) 
					for (Long uid2 : inter) {
						String uid2_name = UID_2_NAME.get(uid2);
						System.out.print((first ? "" : ", ") + uid2_name);
						first = false;
					}
				System.out.println(" ]");
			}
			System.out.println("=========================");
		}	
	}
}
