package org.nicta.lr.util;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

import util.Statistics;

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

	public static DecimalFormat _df = new DecimalFormat("0.000");
	
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
	public static void main(String[] args) throws Exception {
		
		System.out.println("App users: " + APP_USERS.size());
		System.out.println("All users: " + ALL_USERS.size());	
		
		//ShowLikes("Sonia");
		//ShowInteractions("Sonia");
		ShowCondProbs();
		//ShowGroups();
		
		//for (FriendType friend_type : FriendType.values()) {
		//	System.out.println(friend_type.toString());
		//}
	}

	public static HashMap<Long,Integer> GetLikesInteractions(long uid, Interaction i, Map<Long,Set<Long>> id2likes) {
		HashMap<Long,Integer> likes = new HashMap<Long,Integer>();
		Set<Long> others = i.getInteractions(uid);
		
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
	
	public static Set<Long> ThresholdAtK(HashMap<Long,Integer> counts, int k) {
		Set<Long> ret = new HashSet<Long>();
		for (Map.Entry<Long, Integer> entry : counts.entrySet())
			if (entry.getValue() >= k)
				ret.add(entry.getKey());
		return ret;
	}
	
	public static void ShowCondProbs() throws Exception {
		
		PrintStream log = new PrintStream(new FileOutputStream("cond_probs.txt"));
		Set<Long> id_set = APP_USERS;

		for (LikeType ltype : LikeType.values()) {

		for (InteractionType itype : InteractionType.values()) {
			
			if (itype == InteractionType.GROUPS_SZ_2_5 || itype == InteractionType.GROUPS_SZ_6_10 || itype == InteractionType.GROUPS_SZ_11_25 || 
				itype == InteractionType.GROUPS_SZ_26_50 || itype == InteractionType.GROUPS_SZ_51_100 || itype == InteractionType.GROUPS_SZ_101_500 || 
				itype == InteractionType.GROUPS_SZ_500_PLUS || itype == InteractionType.GROUPS_SZ_2_2)
					continue;

			System.out.println("*************************");
			log.println("*************************");

			for (Direction dir : Direction.values()) {
		
				Interaction i = UserUtil.getUserInteractions(itype, dir, false);

				//for (LikeType ltype : LikeType.values()) {
					System.out.println("=========================");
					log.println("=========================");
					Map<Long,Set<Long>> id2likes = UserUtil.getLikes(ltype);
	
					// Number of friends who also like the same thing
					ArrayList<Double> prob_at_k = new ArrayList<Double>();
					for (int k = 1; k <= 10; k++) {
						
						ArrayList<Double> probs = new ArrayList<Double>();
						for (long uid : id_set) {
							String uid_name = UID_2_NAME.get(uid);
							//if (!uid_name.contains(restriction))
							//	continue;
							HashMap<Long,Integer> other_likes_id2count = GetLikesInteractions(uid, i, id2likes);
							//P(like | friend likes) = P(like and friend likes) / P(friend likes)
							//                       = F(like and friend likes) / F(friend likes)
							Set<Long> other_likes_ids = ThresholdAtK(other_likes_id2count, k);
							Set<Long> likes_intersect_other_likes_ids = id2likes.get(uid);
							if (likes_intersect_other_likes_ids == null && other_likes_ids.size() > 0) 
								probs.add(0d); // uid didn't like anything
							else if (other_likes_ids.size() > 0) {
								likes_intersect_other_likes_ids.retainAll(other_likes_ids);
								probs.add((double)likes_intersect_other_likes_ids.size() / (double)other_likes_ids.size());
							} // else (other_likes_ids.size() == 0) -- friends didn't like anything so undefined
						}
						if (probs.size() > 10) {
							String line = "** " + ltype + " likes | " + itype + " inter & " + dir + " & >" + k + " likes " + ": " +
								(_df.format(Statistics.Avg(probs)) + " +/- " + _df.format(Statistics.StdError95(probs)) + " #" + probs.size() + " [ " + _df.format(Statistics.Min(probs)) + ", " + _df.format(Statistics.Max(probs)) + " ]");
							log.println(line);
							log.flush();
							System.out.println(line);
						}
						prob_at_k.add(Statistics.Avg(probs));
					}
					//for (int k = 1; k < 10; k++) {
					//	System.out.println(k + ": " + prob_at_k.get(k-1) + "   ");
					//}
					System.out.println("=========================");
					log.println("=========================");
				}
			}
		}
		log.close();
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
			Interaction i = UserUtil.getUserInteractions(type, Direction.BIDIR, false);
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
