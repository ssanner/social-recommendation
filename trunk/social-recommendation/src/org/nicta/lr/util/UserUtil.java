package org.nicta.lr.util;

import org.nicta.lr.LinkRecommender;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class UserUtil 
{
	/**
	 * Retrieves user features from the database and saves them into a HashMap.
	 * User features are normalized between 0 and 1. Only features that don't grow are currently used.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Set<Long> getUserIds()
		throws SQLException
	{
		HashSet<Long> userIds = new HashSet<Long>();
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT uid FROM linkrUser";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			userIds.add(result.getLong("uid"));
		}
		statement.close();
		
		return userIds;
	}
	
	public static Set<Long> getAppUserIds()
		throws SQLException
	{
		HashSet<Long> userIds = new HashSet<Long>();
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT uid FROM trackUserUpdates WHERE is_app_user=1";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			userIds.add(result.getLong("uid"));
		}
		statement.close();
		
		return userIds;
	}
	
	public static Map<Long,Set<Long>> getLikes(ELikeType type)
		throws SQLException
	{
		// Recursive case for all types
		if (type == ELikeType.ALL) {
			Map<Long,Set<Long>> ret = getLikes(ELikeType.LINK);
			ret = Merge(ret, getLikes(ELikeType.POST));
			ret = Merge(ret, getLikes(ELikeType.PHOTO));			
			ret = Merge(ret, getLikes(ELikeType.VIDEO));
			return ret;
			
		} else { // Base cases for primitive like types
			
			// uid -> set(likeIDs)
			HashMap<Long,Set<Long>> user2likeIDs = new HashMap<Long,Set<Long>>();
			
			Statement statement = SQLUtil.getStatement();
			
			String table = null;
			String liked_id = null;
			switch (type) {
				case LINK:  table = "linkrLinkLikes";  liked_id = "link_id"; break;
				case POST:  table = "linkrPostLikes";  liked_id = "post_id"; break;
				case PHOTO: table = "linkrPhotoLikes"; liked_id = "photo_id"; break;
				case VIDEO: table = "linkrVideoLikes"; liked_id = "video_id"; break;
			}
			String userQuery = "SELECT " + liked_id + ", id FROM " + table;
			
			ResultSet result = statement.executeQuery(userQuery);
			while (result.next()) {
				long LIKED_ID = result.getLong(1);
				long USER_ID = result.getLong(2);
				Set<Long> likedIDs = user2likeIDs.get(USER_ID);
				if (likedIDs == null) {
					likedIDs = new HashSet<Long>();
					user2likeIDs.put(USER_ID, likedIDs);
				}
				likedIDs.add(LIKED_ID);
			}
			statement.close();
			
			return user2likeIDs;
		}
	}
	
	public static Map<Long,Set<Long>> 
			Merge(Map<Long,Set<Long>> s1, Map<Long,Set<Long>> s2) {
		Map<Long,Set<Long>> results = new HashMap<Long,Set<Long>>();
		for (Map.Entry<Long,Set<Long>> e : s1.entrySet()) {
			long uid = e.getKey();
			Set<Long> to_add = e.getValue();
			results.put(uid, new HashSet<Long>(to_add));
		}
		for (Map.Entry<Long,Set<Long>> e : s2.entrySet()) {
			long uid = e.getKey();
			Set<Long> to_add = e.getValue();
			Set<Long> target_set = results.get(uid);
			if (target_set == null) {
				results.put(uid, new HashSet<Long>(to_add));
			} else {
				target_set.addAll(to_add);
			}
		}
		return results;
	}
	
	/**
	 * 
	 * @param type -- see InteractionType
	 * @param complete_colikes -- currently unused... whether to complete all pairs of people
	 *                            liking same item
	 * @return
	 * @throws SQLException
	 */
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
				case FRIENDS:        table = "linkrFriends"; target_uid = "uid1"; interacting_uid = "uid2"; break;
				case LINK_LIKES:     table = "linkrLinkLikes"; target_uid = "uid"; interacting_uid = "id"; break;
				case LINK_COMMENTS:  table = "linkrLinkComments"; target_uid = "uid"; interacting_uid = "from_id"; break;
				case POST_LIKES:     table = "linkrPostLikes"; target_uid = "uid"; interacting_uid = "id"; break;
				case POST_COMMENTS:  table = "linkrPostComments"; target_uid = "uid"; interacting_uid = "from_id"; break;
				case POST_TAGS:      table = "linkrPostTags"; target_uid = "uid1"; interacting_uid = "uid2"; break;
				case PHOTO_LIKES:    table = "linkrPhotoLikes"; target_uid = "uid"; interacting_uid = "id"; break;
				case PHOTO_COMMENTS: table = "linkrPhotoComments"; target_uid = "uid"; interacting_uid = "from_id"; break; 
				case PHOTO_TAGS:     table = "linkrPhotoTags"; target_uid = "uid1"; interacting_uid = "uid2"; break;
				case VIDEO_LIKES:    table = "linkrVideoLikes"; target_uid = "uid"; interacting_uid = "id"; break;
				case VIDEO_COMMENTS: table = "linkrVideoComments"; target_uid = "uid"; interacting_uid = "from_id"; break;
				case VIDEO_TAGS:     table = "linkrVideoTags"; target_uid = "uid1"; interacting_uid = "uid2"; break;
				default: {
					System.out.println("ERROR: Illegal type -- " + type);
					System.exit(1);
				}
			}
			
			String sql_query = "SELECT " + target_uid + ", " + interacting_uid + " FROM " + table;
			System.out.println(sql_query);
			
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
	
	public static Map<EInterestType,Map<Long,Set<Long>>> EINT_2_UID_2_GROUPID = new HashMap<EInterestType,Map<Long,Set<Long>>>();
	public static Map<EInterestType,Map<Long,Set<Long>>> EINT_2_GROUPID_2_UID = new HashMap<EInterestType,Map<Long,Set<Long>>>();
	public static Interaction FRIEND_INTER = null;
	public static Map<Long,String> USER_NAMES = null; 
	
	public static Interaction getGroupInterAmongFriends(EInterestType type, int ub)
	throws SQLException {
		
		// Cache all friends and names
		if (FRIEND_INTER == null)
			FRIEND_INTER = UserUtil.getUserInteractions(EInteractionType.FRIENDS, EDirectionType.BIDIR);
		if (USER_NAMES == null)
			USER_NAMES = UserUtil.getUserNames();
		
		// Get interactions for (type, ub)
		Interaction interest_group_inter = new Interaction(); // currently treat interactions as undirected

		Map<Long,Set<Long>> UID_2_GROUPID = EINT_2_UID_2_GROUPID.get(type);
		Map<Long,Set<Long>> GROUPID_2_UID = EINT_2_GROUPID_2_UID.get(type);
		if (UID_2_GROUPID == null) {
			UID_2_GROUPID = UserUtil.getUser2InterestGroups(type);
			GROUPID_2_UID = UserUtil.getInterestGroup2Users(type);
			EINT_2_UID_2_GROUPID.put(type, UID_2_GROUPID);
			EINT_2_GROUPID_2_UID.put(type, GROUPID_2_UID);
		}
		
		int lb = 2; // Don't want a group of size < 2
		for (long uid : UID_2_GROUPID.keySet()) {
			Set<Long> groups = UID_2_GROUPID.get(uid);
			if (groups != null) 
				for (Long gid : groups) {
					Set<Long> other_gids = new HashSet<Long>(GROUPID_2_UID.get(gid));
					if (other_gids == null) // Nothing to retain
						continue;
					Set<Long> friend_interactions = FRIEND_INTER.getInteractions(uid);
					if (friend_interactions == null) // Retain nothing
						continue;
					//else 
					//	System.out.println("No friends for: " + uid + " -- " + USER_NAMES.get(uid));
					other_gids.retainAll(friend_interactions);
					if (other_gids.size() < lb || other_gids.size() > ub)
						continue;
					for (long uid2 : other_gids) { 
						if (uid2 == uid)
							continue;
						interest_group_inter.addInteraction(uid, uid2, EDirectionType.BIDIR);
						//System.out.println(uid + ":" + gid + ":" + uid2);
						//k++;
					}
				}
			//if (k > 100) System.exit(1);
		}
		
		return interest_group_inter;
	}

	public static Map<Long,String> getUserNames() 
		throws SQLException
	{
		HashMap<Long,String> userID2Name = new HashMap<Long,String>();
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT uid, name FROM linkrUser";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			userID2Name.put(result.getLong(1), result.getString(2));
		}
		statement.close();
		
		return userID2Name;
	}

	public static String GetInterestGroupTable(EInterestType type) {
		switch (type) {
			case GROUPS:               return "linkrGroups"; 
			case ACTIVITIES:           return "linkrActivities"; 
			case BOOKS:                return "linkrBooks"; 
			case FAVORITE_ATHLETES:    return "linkrFavoriteAthletes"; 
			case FAVORITE_TEAMS:       return "linkrFavoriteTeams"; 
			case INSPIRATIONAL_PEOPLE: return "linkrInspirationalPeople"; 
			case INTERESTS:            return "linkrInterests"; 
			case GENERAL_LIKES:        return "linkrLikes"; 
			case MOVIES:               return "linkrMovies";  
			case MUSIC:                return "linkrMusic"; 
			case SPORTS:               return "linkrSports"; 
			case TELEVISION:           return "linkrTelevision"; 
			case SCHOOL:               return "linkrEducation";
			case WORK:                 return "linkrWork";
			default: {
				System.out.println("ERROR: Illegal type -- " + type);
				System.exit(1);
			}
		}
		return null;
	}
	
	public static Map<Long,String> getGroupNames(EInterestType type) 
	throws SQLException
	{
		HashMap<Long,String> groupID2Name = new HashMap<Long,String>();
		if (type == EInterestType.SCHOOL || type == EInterestType.WORK)
			return groupID2Name; // No explicit names here without doing a table join
		
		String table = GetInterestGroupTable(type);
				
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT id, name from " + table;
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			groupID2Name.put(result.getLong(1), result.getString(2));
		}
		statement.close();
		
		return groupID2Name;
	}

	public static Map<Long, Integer> getGroupID2Size(EInterestType type) 
	throws SQLException {

		HashMap<Long, Integer> groupid2size = new HashMap<Long, Integer>();
		if (type == EInterestType.SCHOOL || type == EInterestType.WORK)
			return groupid2size; // No explicit names here without doing a table join

		String table = GetInterestGroupTable(type);
				
		Statement statement = SQLUtil.getStatement();
		String query = "select id, count(id) as ncount from " + table + " group by name order by ncount desc";

		ResultSet result = statement.executeQuery(query);
		while (result.next()) {
			long uid = result.getLong(1);
			int group_size = result.getInt(2);
			if (group_size > 1) // No need for groups of size 1
				groupid2size.put(uid, group_size);
		}
		statement.close();
		
		return groupid2size;
	}
	
	public static Map<Long, Set<Long>> getUser2InterestGroups(EInterestType type)
	throws SQLException {
		
		// Base case retrieval
		String table = GetInterestGroupTable(type);
		String target_uid = "uid";
		String target_id = "id";
		if (type == EInterestType.SCHOOL)
			target_id = "school_id";
		else if (type == EInterestType.WORK)
			target_id = "employer_id";
		
		HashMap<Long,Set<Long>> user2groups = new HashMap<Long,Set<Long>>();
		Statement statement = SQLUtil.getStatement();
		String query = "select " + target_uid + ", " + target_id + " from " + table + " order by " + target_uid;
		
		ResultSet result = statement.executeQuery(query);
		long last_uid = -1;
		Set<Long> group_ids = null;
		while (result.next()) {
			long uid = result.getLong(1);
			long group_id = result.getLong(2);
			if (uid != last_uid) { // Starting a new set for uid
				group_ids = new HashSet<Long>();
				user2groups.put(uid, group_ids);
			}
			last_uid = uid;
			group_ids.add(group_id);
		}
		statement.close();
		
		return user2groups;
	}
	
	public static Map<Long, Set<Long>> getInterestGroup2Users(EInterestType type)
	throws SQLException {
		
		// Base case retrieval
		String table = GetInterestGroupTable(type);
		String target_uid = "uid";
		String target_id = "id";
		if (type == EInterestType.SCHOOL)
			target_id = "school_id";
		else if (type == EInterestType.WORK)
			target_id = "employer_id";

		HashMap<Long,Set<Long>> group2users = new HashMap<Long,Set<Long>>();
		Statement statement = SQLUtil.getStatement();
		String query = "select " + target_uid + ", " + target_id + " from " + table + " order by " + target_id;
		
		ResultSet result = statement.executeQuery(query);
		long last_group_id = -1;
		Set<Long> uids = null;
		while (result.next()) {
			long uid = result.getLong(1);
			long group_id = result.getLong(2);
			if (group_id != last_group_id) { // Starting a new set for uid
				uids = new HashSet<Long>();
				group2users.put(group_id, uids);
			}
			last_group_id = group_id;
			uids.add(uid);
		}
		statement.close();
		
		return group2users;
	}
	
	public static class DemographicData {
		public int _lb;
		public int _ub;
		public Map<Long,Integer> _uid2index;
		public DemographicData(int lb, int ub, Map<Long,Integer> uid2index) {
			_lb = lb;
			_ub = ub;
			_uid2index = uid2index;
		}
	}
		
	public static DemographicData getUser2Demographic(EDemographicType type) 
	throws SQLException {
		
		Statement statement = SQLUtil.getStatement();
		DemographicData d = new DemographicData(
				Integer.MAX_VALUE, Integer.MIN_VALUE, new HashMap<Long,Integer>());
		
		if (type == EDemographicType.GENDER) {
			
			String userQuery = "SELECT uid, gender FROM linkrUser";
			ResultSet result = statement.executeQuery(userQuery);

			d._lb = 0;
			d._ub = 1;
			while (result.next()) {
			
				long uid = result.getLong("uid");
				int  gender = result.getString("gender").equals("male") ? 1 : 0;
				d._uid2index.put(uid, gender);
			}
		
		} else if (type == EDemographicType.AGE) {

			String userQuery = "SELECT uid, birthday FROM linkrUser";
			ResultSet result = statement.executeQuery(userQuery);

			while (result.next()) {
			
				long uid = result.getLong("uid");

				int birthYear = 0;
				String birthday = result.getString("birthday");
				if (birthday.length() == 10) {
					birthYear = Integer.parseInt(birthday.split("/")[2]);
				}
				if (birthYear > 1900 && birthYear <= 2012) {
					int age_group = (int)Math.floor((2012 - birthYear) / 5d);
					d._lb = Math.min(d._lb, age_group);
					d._ub = Math.max(d._ub, age_group);
					d._uid2index.put(uid, age_group);
				}
			}

		} else if (type == EDemographicType.EDUCATION_DEGREE) {

			String userQuery = "SELECT uid, type FROM linkrEducation";
			ResultSet result = statement.executeQuery(userQuery);

			d._lb = 0;
			d._ub = 2;
			while (result.next()) {
			
				long uid = result.getLong("uid");
				String degree = result.getString("type");
				Integer cur_degree = d._uid2index.get(uid);
				
				// Set degree to highest degree if cur_degree exists
				if (degree.equals("Graduate School")) // highest degree, always overrides
					d._uid2index.put(uid, 2);
				else if (degree.equals("College") && (cur_degree == null || cur_degree < 1))
					d._uid2index.put(uid, 1);
				else if (degree.equals("High School") && cur_degree == null)
					d._uid2index.put(uid, 0);
			}
		
		} else {
			System.out.println("Retrieval of EDemographicType '" + type + "' currently not supported.");
			//System.exit(1);
			return null;
		}
		
		return d;
	}

	public static Set<Long> getIDSubsetWithDemographic(Set<Long> src, DemographicData d, int type_id) {
		
		Set<Long> ret = new HashSet<Long>();
		
		for (long uid : src) {
			Integer type = d._uid2index.get(uid);
			if (type != null && type == type_id)
				ret.add(uid);
		}
		
		return ret;
	}
	
	public static Set<Long> getFriendUIDsWithDemographic(
			long uid, DemographicData d, int friend_type_target_id) throws SQLException {
		
		Set<Long> ret = new HashSet<Long>();
		
		// Cache all friends and names
		if (FRIEND_INTER == null)
			FRIEND_INTER = UserUtil.getUserInteractions(EInteractionType.FRIENDS, EDirectionType.BIDIR);

		Set<Long> friends = FRIEND_INTER.getInteractions(uid);
		if (friends == null) {
			//if (USER_NAMES == null)
			//	USER_NAMES = getUserNames();
			//System.out.println("WARNING: NO FRIENDS FOR: " + USER_NAMES.get(uid));
			return ret;
		}
		
		for (Long uid2 : friends) {
			Integer friend_type_id = d._uid2index.get(uid2);
			if (uid2 != uid && friend_type_id != null && friend_type_id == friend_type_target_id)
				ret.add(uid2);
		}
		
		return ret;
	}
	
	public static Map<Long, Double[]> getUserFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = 
			"SELECT uid, gender, birthday, location_id, hometown_id "
			+ "FROM linkrUser";
		
		ResultSet result = statement.executeQuery(userQuery);
		
		while (result.next()) {
			String sex = result.getString("gender");
			
			//We're only interested on the age for this one.
			int birthYear = 0;
			String birthday = result.getString("birthday");
			if (birthday.length() == 10) {
				birthYear = Integer.parseInt(birthday.split("/")[2]);
			}
			
			//double currentLocation = result.getLong("location_id") / 300000000000000.0;
			//double hometownLocation = result.getLong("hometown_id") / 300000000000000.0;
			
			//Features are normalized between 0 and 1
			Double[] feature = new Double[Configuration.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 1.0;
				feature[1] = 0.0;
			}
			else if ("female".equals(sex)){
				feature[0] = 0.0;
				feature[1] = 1.0;
			}
			else {
				feature[0] = 0.0;
				feature[1] = 0.0;
			}
			
			feature[2] = birthYear / 2012.0;
			
			//feature[0] = 0.0;
			//feature[1] = 0.0;
			//feature[2] = 0.0;
		
			//feature[2] = currentLocation;
			//feature[3] = hometownLocation;
			
			userFeatures.put(result.getLong("uid"), feature);
		}
		
		statement.close();
		
		return userFeatures;
	}
	
	public static Map<Long, Double[]> getUserFeatures(Set<Long> userIds)
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Statement statement = SQLUtil.getStatement();
		
		StringBuffer userQuery = new StringBuffer();
		userQuery.append("SELECT uid, gender, birthday, location_id, hometown_id FROM linkrUser WHERE uid IN (0");
		for (long id : userIds) {
			userQuery.append(",");
			userQuery.append(id);
		}
		userQuery.append(")");
		
		ResultSet result = statement.executeQuery(userQuery.toString());
		
		while (result.next()) {
			String sex = result.getString("gender");
			
			//We're only interested on the age for this one.
			int birthYear = 0;
			String birthday = result.getString("birthday");
			if (birthday.length() == 10) {
				birthYear = Integer.parseInt(birthday.split("/")[2]);
			}
			
			//double currentLocation = result.getLong("location_id") / 300000000000000.0;
			//double hometownLocation = result.getLong("hometown_id") / 300000000000000.0;
			
			//Features are normalized between 0 and 1
			Double[] feature = new Double[Configuration.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 1.0;
				feature[1] = 0.0;
			}
			else if ("female".equals(sex)){
				feature[0] = 0.0;
				feature[1] = 1.0;
			}
			else {
				feature[0] = 0.0;
				feature[1] = 0.0;
			}
			
			feature[2] = birthYear / 2012.0;
			
			//feature[0] = 0.0;
			//feature[1] = 0.0;
			//feature[2] = 0.0;
			
			//feature[2] = currentLocation;
			//feature[3] = hometownLocation;
			
			userFeatures.put(result.getLong("uid"), feature);
		}
		
		statement.close();
		
		return userFeatures;
	} 
	
	/**
	 * Gets all user friendship connections that are saved in the DB.
	 * Each user will have an entry in the HashMap and a HashSet that will contain the ids of 
	 * the user's friends.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Map<Long, Map<Long, Double>> getFriendships()
		throws SQLException
	{
		HashMap<Long, Map<Long, Double>> friendships = new HashMap<Long, Map<Long, Double>>();
		
		Statement statement = SQLUtil.getStatement();
		
		String friendQuery =
			"SELECT uid1, uid2 "
			+ "FROM linkrFriends";
		
		ResultSet result = statement.executeQuery(friendQuery);
		
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			friendships.get(uid1).put(uid2, 1.0);
			friendships.get(uid2).put(uid1, 1.0);
		}
		
		
		statement.close();
		
		
		return friendships;
	}

	/**
	 * Friendship measure which is a normalized sum of the user-user interaction. Values are normalized by the 
	 * maximum number of interactions. Should interactions all carry the same weight? Maybe something like getting 
	 * tagged in the same photo carry a heavier weight?
	 * 
	 * Interaction measure between user1 and user2 will be equal to user2 and user1.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Map<Long, Map<Long, Double>> getFriendInteractionMeasure(Set<Long> uids)
		throws SQLException
	{
		HashMap<Long, Map<Long, Double>> friendships = new HashMap<Long, Map<Long, Double>>();
		
		StringBuffer idBuf = new StringBuffer("(0");
		for (Long id : uids) {
			idBuf.append(",");
			idBuf.append(id);
		}
		idBuf.append(")");
		String idString = idBuf.toString();
		
		Statement statement = SQLUtil.getStatement();
		
		//First interaction is the friend links. Friend links are now just one kind of interaction
		String friendQuery =
			"SELECT uid1, uid2 "
			+ "FROM linkrFriends WHERE uid1 IN " + idString + " AND uid2 IN " + idString;
		
		ResultSet result = statement.executeQuery(friendQuery);
		
		double max_value = 0;
		
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			friendships.get(uid1).put(uid2, 1.0);
			friendships.get(uid2).put(uid1, 1.0);
		}
		
		// Comments on photos
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPhotoComments WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		// Likes on photos
		result = statement.executeQuery("SELECT uid, id FROM linkrPhotoLikes WHERE uid IN " + idString + " AND id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		// Times a user has posted a photo on someone else's wall
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPhotos WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Times that a user has been tagged in another user's photo
		//Also get the people that are tagged per photo. Users getting tagged in the same photo is a pretty good measure 
		//that they're friends.
		//
		HashMap<Long, HashSet<Long>> photoTags = new HashMap<Long, HashSet<Long>>();
		
		System.out.println("Joining photo tags");
		result = statement.executeQuery("SELECT uid1, uid2, photo_id FROM linkrPhotoTags WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			Long photoId = result.getLong("photo_id");
			
			if (uid1 == uid2) continue;
			//if (!uids.contains(uid1) || !uids.contains(uid2)) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
			
			HashSet<Long> tagged; //Users that were already tagged in this photo
			if (photoTags.containsKey(photoId)) {
				tagged = photoTags.get(photoId);
			}
			else {
				tagged = new HashSet<Long>();
				photoTags.put(photoId, tagged);
			}
			
			if (tagged.contains(uid2)) continue;
			
			//Given the new tagged user, increment the interaction count between the user and
			//all users that were already tagged in the photo.
			for (Long alreadyTagged : tagged) {		
				double tagVal = 1;
				
				if (friendships.get(alreadyTagged).containsKey(uid2)) {
					tagVal += friendships.get(alreadyTagged).get(uid2);
				}
				
				if (tagVal > max_value) max_value = tagVal;
				
				friendships.get(alreadyTagged).put(uid2, tagVal);
				friendships.get(uid2).put(alreadyTagged, tagVal);
			}
			
			tagged.add(uid2);
		}
		
		System.out.println("Done with photo tags");
		
		//Users posting on another user's wall
		System.out.println("linkrPost");
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPost WHERE application_id !=" + Constants.APPLICATION_ID + " AND uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users commenting on another user's posts
		System.out.println("linkrPostComments");
		result = statement.executeQuery("SELECT uid, from_id FROM linkrPostComments WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users liking another user's posts.
		System.out.println("linkrPostLikes");
		result = statement.executeQuery("SELECT uid, id FROM linkrPostLikes WHERE uid IN " + idString + " AND id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users being tagged in another user's posts.
		System.out.println("linkrPostTags");
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrPostTags");
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users that went to the same classes
		System.out.println("linkrSchoolClassesWith");
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSchoolClassesWith");// WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			if (!uids.contains(uid1) || !uids.contains(uid2)) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users that went to the same school
		System.out.println("linkrSchoolWith");
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSchoolWith"); // WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			if (!uids.contains(uid1) || !uids.contains(uid2)) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Users playing the same sports
		System.out.println("linkrSportsWith");
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSportsWith");// WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			if (!uids.contains(uid1) || !uids.contains(uid2)) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Posting videos into another user's wall
		System.out.println("linkrVideos");
		result = statement.executeQuery("SELECT uid, from_id FROM linkrVideos WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Commenting on another user's video
		System.out.println("linkrVideoComments");
		result = statement.executeQuery("SELECT uid, from_id FROM linkrVideoComments WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//Liking another user's video
		System.out.println("linkrVideoLikes");
		result = statement.executeQuery("SELECT uid, id FROM linkrVideoLikes WHERE uid IN " + idString + " AND id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's getting tagged in another user's video.
		//Also get the people that are tagged per video. Users getting tagged in the same video is a pretty good measure 
		//that they're friends.
		HashMap<Long, HashSet<Long>> videoTags = new HashMap<Long, HashSet<Long>>();
		System.out.println("Getting video tags");
		result = statement.executeQuery("SELECT t.uid2, l.uid, t.video_id FROM linkrVideos l, linkrVideoTags t WHERE t.video_id=l.id AND t.uid2 IN " + idString + " AND l.uid IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("l.uid");
			Long uid2 = result.getLong("t.uid2");
			Long videoId = result.getLong("t.video_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
			
			HashSet<Long> tagged; //Users that were already tagged in this photo
			if (videoTags.containsKey(videoId)) {
				tagged = videoTags.get(videoId);
			}
			else {
				tagged = new HashSet<Long>();
				videoTags.put(videoId, tagged);
			}
			
			if (tagged.contains(uid2)) continue;
			
			//Given the new tagged user, increment the interaction count between the user and
			//all users that were already tagged in the photo.
			for (Long alreadyTagged : tagged) {		
				double tagVal = 1;
				
				if (friendships.get(alreadyTagged).containsKey(uid2)) {
					tagVal += friendships.get(alreadyTagged).get(uid2);
				}
				
				if (tagVal > max_value) max_value = tagVal;
				
				friendships.get(alreadyTagged).put(uid2, tagVal);
				friendships.get(uid2).put(alreadyTagged, tagVal);
			}
			
			tagged.add(uid2);
		}
		System.out.println("Done with video tags");
		
		//User's working in the same project
		System.out.println("linkrWorkProjects");
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrWorkProjectsWith");// WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's working in the same company
		System.out.println("linkrWorkWith");
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrWorkWith");// WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's liking a persons's link.
		System.out.println("linkrLinkLikes");
		result = statement.executeQuery("SELECT uid, id FROM linkrLinkLikes WHERE uid IN " + idString + " AND id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's commenting on a person's link
		System.out.println("linkrLinkComments");
		result = statement.executeQuery("SELECT uid, from_id FROM linkrLinkComments WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		//User's posting a link on someone else's wall
		System.out.println("linkrLinks");
		result = statement.executeQuery("SELECT uid, from_id FROM linkrLinks WHERE uid IN " + idString + " AND from_id IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("uid");
			Long uid2 = result.getLong("from_id");
			
			if (uid1 == uid2) continue;
			
			double val = 1;
		
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashMap<Long, Double>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashMap<Long, Double>());
			}
			
			if (friendships.get(uid1).containsKey(uid2)) {
				val += friendships.get(uid1).get(uid2);
			}
			
			if (val > max_value) max_value = val;
			
			friendships.get(uid1).put(uid2, val);
			friendships.get(uid2).put(uid1, val);
		}
		
		/* NORMALIZING WITH THE AVERAGE */
		HashSet<Long> done = new HashSet<Long>();
		double total = 0;
		int count = 0;
		
		for (long uid1 : friendships.keySet()) {
			done.add(uid1);
			
			Map<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				if (done.contains(uid2)) continue;
				
				total += friendValues.get(uid2);
				count++;
			}
		}
		double average = total / count;
		
		for (long uid1 : friendships.keySet()) {
			Map<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= average;
				if (LinkRecommender.type.equals(Constants.SPECTRAL) || LinkRecommender.type.equals(Constants.HYBRID_SPECTRAL) || LinkRecommender.type.equals(Constants.SPECTRAL_COPREFERENCE)) {
					val = Math.log(1 + val);
				}
				else {
					val = Math.log(val);
				}
				friendValues.put(uid2, val);
			}
		}
		
		statement.close();
		return friendships;
	}
	
	public static Set<Long> getAppUsersWithAlgorithm(String algo)
		throws SQLException
	{
		HashSet<Long> ids = new HashSet<Long>();
		
		Statement statement = SQLUtil.getStatement();
		ResultSet result = statement.executeQuery("SELECT uid FROM trackUserUpdates WHERE is_app_user=1 AND algorithm='" + algo + "'");
		
		while (result.next()) {
			ids.add(result.getLong("uid"));
		}
		statement.close();
		
		return ids;
	}
}
