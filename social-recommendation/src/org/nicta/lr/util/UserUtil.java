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
	
	public static Map<Long,Set<Long>> getLikes(LikeType type)
		throws SQLException
	{
		// uid -> set(likeIDs)
		HashMap<Long,Set<Long>> user2likeIDs = new HashMap<Long,Set<Long>>();
		
		Statement statement = SQLUtil.getStatement();
		
		String table = null;
		String liked_id = null;
		switch (type) {
			case LINK:  table = "linkrlinklikes";  liked_id = "link_id"; break;
			case POST:  table = "linkrpostlikes";  liked_id = "post_id"; break;
			case PHOTO: table = "linkrphotolikes"; liked_id = "photo_id"; break;
			case VIDEO: table = "linkrvideolikes"; liked_id = "video_id"; break;
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
	
	/**
	 * 
	 * @param type -- see InteractionType
	 * @param complete_colikes -- currently unused... whether to complete all pairs of people
	 *                            liking same item
	 * @return
	 * @throws SQLException
	 */
	public static Interaction getUserInteractions(
			InteractionType type, boolean complete_colikes) 
		throws SQLException
	{
		if (complete_colikes) {
			System.out.println("ERROR: UserUtil.getUserInteractions(..., complete_colikes=true) currently unsupported");
			System.exit(1);
		}
		
		if (type == InteractionType.GROUPS_SZ_2_5 || type == InteractionType.GROUPS_SZ_6_10 || type == InteractionType.GROUPS_SZ_11_25 || 
			type == InteractionType.GROUPS_SZ_26_50 || type == InteractionType.GROUPS_SZ_51_100 || type == InteractionType.GROUPS_SZ_101_500 || 
			type == InteractionType.GROUPS_SZ_500_PLUS) {
			return getUserInteractionsByGroup(type, complete_colikes);
		}
		
		Interaction i = new Interaction(); // currently treat interactions as undirected

		String table = null;
		String target_uid = null;
		String interacting_uid = null;
		switch (type) {
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
		}
		
		String sql_query = "SELECT " + target_uid + ", " + interacting_uid + " FROM " + table;
		
		Statement statement = SQLUtil.getStatement();
		ResultSet result = statement.executeQuery(sql_query);
		while (result.next()) {
			long TARGET_ID = result.getLong(1);
			long FROM_ID = result.getLong(2);
			i.addInteraction(TARGET_ID, FROM_ID);
		}
		statement.close();
		
		return i;
	}
	
	public static Interaction getUserInteractionsByGroup
		(InteractionType type, boolean complete_colikes)
	throws SQLException {
		
		Interaction i = new Interaction(); // currently treat interactions as undirected

		Map<Long,Set<Long>> UID_2_GROUPID = UserUtil.getUser2Groups();
		Map<Long,Set<Long>> GROUPID_2_UID = UserUtil.getGroup2Users();
		
		int lb = 1;
		int ub = Integer.MAX_VALUE;
		switch (type) {
			case GROUPS_SZ_2_5:      lb = 2;   ub = 5;   break;
			case GROUPS_SZ_6_10:     lb = 6;   ub = 10;  break;
			case GROUPS_SZ_11_25:    lb = 11;  ub = 25;  break; 
			case GROUPS_SZ_26_50:    lb = 26;  ub = 50;  break;
			case GROUPS_SZ_51_100:   lb = 51;  ub = 100; break;
			case GROUPS_SZ_101_500:  lb = 101; ub = 500; break; 
			case GROUPS_SZ_500_PLUS: lb = 500; break;
			default: {
				System.out.println("ERROR: Illegal type -- " + type);
				System.exit(1);
			}
		}
		
		//int k = 0;
		for (long uid : UID_2_GROUPID.keySet()) {
			Set<Long> groups = UID_2_GROUPID.get(uid);
			if (groups != null) 
				for (Long gid : groups) {
					Set<Long> other_gids = GROUPID_2_UID.get(gid);
					if (other_gids == null || other_gids.size() < lb || other_gids.size() > ub)
						continue;
					for (long uid2 : other_gids) { 
						if (uid2 == uid)
							continue;
						i.addInteraction(uid, uid2);
						//System.out.println(uid + ":" + gid + ":" + uid2);
						//k++;
					}
				}
			//if (k > 100) System.exit(1);
		}
		
		return i;
	}

	public static Map<Long,String> getUserNames() 
		throws SQLException
	{
		HashMap<Long,String> userID2Name = new HashMap<Long,String>();
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT uid, name FROM linkruser";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			userID2Name.put(result.getLong(1), result.getString(2));
		}
		statement.close();
		
		return userID2Name;
	}

	public static Map<Long,String> getGroupNames() 
	throws SQLException
	{
		HashMap<Long,String> groupID2Name = new HashMap<Long,String>();
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT id, name FROM linkrgroups";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			groupID2Name.put(result.getLong(1), result.getString(2));
		}
		statement.close();
		
		return groupID2Name;
	}

	public static Map<Long, Integer> getGroupID2Size() 
	throws SQLException {
		HashMap<Long, Integer> groupid2size = new HashMap<Long, Integer>();
		
		Statement statement = SQLUtil.getStatement();
		String query = "select id, count(id) as ncount from linkrgroups group by name order by ncount desc";

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
	
	public static Map<Long, Set<Long>> getUser2Groups()
	throws SQLException {
		HashMap<Long,Set<Long>> user2groups = new HashMap<Long,Set<Long>>();
		Statement statement = SQLUtil.getStatement();
		String query = "select uid, id from linkrgroups order by uid";
		
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
	
	public static Map<Long, Set<Long>> getGroup2Users()
	throws SQLException {
		HashMap<Long,Set<Long>> group2users = new HashMap<Long,Set<Long>>();
		Statement statement = SQLUtil.getStatement();
		String query = "select uid, id from linkrgroups order by id";
		
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
