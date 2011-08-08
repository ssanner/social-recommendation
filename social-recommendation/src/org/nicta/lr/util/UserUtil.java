package org.nicta.lr.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
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
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String userQuery = "SELECT uid FROM trackUserUpdates WHERE is_app_user=1";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			userIds.add(result.getLong("uid"));
		}
		statement.close();
		
		return userIds;
	}
	
	public static HashMap<Long, Double[]> getUserFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
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
			
			double currentLocation = result.getLong("location_id") / 300000000000000.0;
			double hometownLocation = result.getLong("hometown_id") / 300000000000000.0;
			
			//Features are normalized between 0 and 1
			Double[] feature = new Double[Constants.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 0.5;
			}
			else if ("female".equals(sex)){
				feature[0] = 1.0;
			}
			else {
				feature[0] = 0.0;
			}
			
			feature[1] = birthYear / 2012.0;
			
			feature[2] = currentLocation;
			feature[3] = hometownLocation;
			
			userFeatures.put(result.getLong("uid"), feature);
		}
		
		statement.close();
		
		return userFeatures;
	}
	
	public static HashMap<Long, Double[]> getUserFeatures(Set<Long> userIds)
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
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
			
			double currentLocation = result.getLong("location_id") / 300000000000000.0;
			double hometownLocation = result.getLong("hometown_id") / 300000000000000.0;
			
			//Features are normalized between 0 and 1
			Double[] feature = new Double[Constants.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 0.5;
			}
			else if ("female".equals(sex)){
				feature[0] = 1.0;
			}
			else {
				feature[0] = 0.0;
			}
			
			feature[1] = birthYear / 2012.0;
			
			feature[2] = currentLocation;
			feature[3] = hometownLocation;
			
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
	public static HashMap<Long, HashMap<Long, Double>> getFriendships()
		throws SQLException
	{
		HashMap<Long, HashMap<Long, Double>> friendships = new HashMap<Long, HashMap<Long, Double>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
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
	public static HashMap<Long, HashMap<Long, Double>> getFriendInteractionMeasure(Set<Long> uids)
		throws SQLException
	{
		HashMap<Long, HashMap<Long, Double>> friendships = new HashMap<Long, HashMap<Long, Double>>();
		
		StringBuffer idBuf = new StringBuffer("(0");
		for (Long id : uids) {
			idBuf.append(",");
			idBuf.append(id);
		}
		idBuf.append(")");
		String idString = idBuf.toString();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
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
		result = statement.executeQuery("SELECT l.uid, t.uid2, t.photo_id FROM linkrPhotos l, linkrPhotoTags t WHERE t.photo_id=l.id AND l.uid IN " + idString + " AND t.uid2 IN " + idString);
		while (result.next()) {
			Long uid1 = result.getLong("l.uid");
			Long uid2 = result.getLong("t.uid2");
			Long photoId = result.getLong("t.photo_id");
			
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
		/*
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
		*/
		
		//Users that went to the same classes
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSchoolClassesWith WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
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
		
		//Users that went to the same school
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSchoolWith WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
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
		
		//Users playing the same sports
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrSportsWith WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
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
		
		//Posting videos into another user's wall
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
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrWorkProjectsWith WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
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
		result = statement.executeQuery("SELECT uid1, uid2 FROM linkrWorkWith WHERE uid1 IN " + idString + " AND uid2 IN " + idString);
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
		
		/*
		NORMALIZING WITH THE MAX
		//Normalize all friendship values with the maximum friendship value
		for (long uid1 : friendships.keySet()) {
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= max_value;
				
				friendValues.put(uid2, val);
			}
		}
		*/
		
		/* NORMALIZING WITH THE AVERAGE */
		
		HashSet<Long> done = new HashSet<Long>();
		double total = 0;
		int count = 0;
		
		for (long uid1 : friendships.keySet()) {
			done.add(uid1);
			
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				if (done.contains(uid2)) continue;
				
				total += friendValues.get(uid2);
				count++;
			}
		}
		double average = total / count;
		
		for (long uid1 : friendships.keySet()) {
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= average;
				val = Math.log(val);
				
				friendValues.put(uid2, val);
			}
		}
		
		statement.close();
		return friendships;
	}
	
	/**
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static HashMap<Long, HashMap<Long, Double>> getFriendLikeSimilarity(Set<Long> uids)
		throws SQLException
	{
		HashMap<Long, HashMap<Long, Double>> friendships = new HashMap<Long, HashMap<Long, Double>>();
		
		double max_value = 0;
		
		StringBuffer idWhereBuf = new StringBuffer(" WHERE id IN (0");
		for (Long id : uids) {
			idWhereBuf.append(",");
			idWhereBuf.append(id);
		}
		idWhereBuf.append(")");
		
		String idWhere = idWhereBuf.toString();
		
		StringBuffer fromWhereBuf = new StringBuffer(" WHERE from_id IN (0");
		for (Long id : uids) {
			fromWhereBuf.append(",");
			fromWhereBuf.append(id);
		}
		fromWhereBuf.append(")");
		String fromWhere = fromWhereBuf.toString();
		
		StringBuffer uidWhereBuf = new StringBuffer(" AND uid IN (0");
		for (Long id : uids) {
			uidWhereBuf.append(",");
			uidWhereBuf.append(id);
		}
		uidWhereBuf.append(")");
		String uidWhere = uidWhereBuf.toString();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		HashMap<Long, HashSet<Long>> photoLiked = new HashMap<Long, HashSet<Long>>();
		
		ResultSet result = statement.executeQuery("SELECT id, photo_id FROM linkrPhotoLikes" + idWhere);
		while (result.next()) {
			Long photoId = result.getLong("photo_id");
			Long uid = result.getLong("id");
			
			HashSet<Long> liked;
			if (photoLiked.containsKey(photoId)) {
				liked = photoLiked.get(photoId);
			}
			else {
				liked = new HashSet<Long>();
				photoLiked.put(photoId, liked);
			}
			
			liked.add(uid);
		}
	
		result = statement.executeQuery("SELECT uid, from_id, id FROM linkrPhotos" + fromWhere + uidWhere);
		while (result.next()) {
			Long uid = result.getLong("uid");
			Long fromId = result.getLong("from_id");
			Long photoId = result.getLong("id");
			
			HashSet<Long> liked;
			if (photoLiked.containsKey(photoId)) {
				liked = photoLiked.get(photoId);
			}
			else {
				liked = new HashSet<Long>();
				photoLiked.put(photoId, liked);
			}
			
			liked.add(uid);
			liked.add(fromId);
		}
		
		HashMap<Long, HashSet<Long>> postLiked = new HashMap<Long, HashSet<Long>>();
		
		result = statement.executeQuery("SELECT id, post_id FROM linkrPostLikes" + idWhere);
		while (result.next()) {
			Long postId = result.getLong("post_id");
			Long uid = result.getLong("id");
			
			HashSet<Long> liked;
			if (postLiked.containsKey(postId)) {
				liked = postLiked.get(postId);
			}
			else {
				liked = new HashSet<Long>();
				postLiked.put(postId, liked);
			}
			
			liked.add(uid);
		}
		result = statement.executeQuery("SELECT post_id, uid, from_id FROM linkrPost" + fromWhere + uidWhere + " AND application_id=" + Constants.APPLICATION_ID);
		while (result.next()) {
			Long postId = result.getLong("post_id");
			Long uid = result.getLong("uid");
			Long fromId = result.getLong("from_id");
			
			HashSet<Long> liked;
			if (postLiked.containsKey(postId)) {
				liked = postLiked.get(postId);
			}
			else {
				liked = new HashSet<Long>();
				postLiked.put(postId, liked);
			}
			
			liked.add(uid);
			liked.add(fromId);
		}
		
		HashMap<Long, HashSet<Long>> videoLiked = new HashMap<Long, HashSet<Long>>();
		
		result = statement.executeQuery("SELECT id, video_id FROM linkrVideoLikes" + idWhere);
		while (result.next()) {
			Long videoId = result.getLong("video_id");
			Long uid = result.getLong("id");
			
			HashSet<Long> liked;
			if (videoLiked.containsKey(videoId)) {
				liked = videoLiked.get(videoId);
			}
			else {
				liked = new HashSet<Long>();
				videoLiked.put(videoId, liked);
			}
			
			liked.add(uid);
		}
		result = statement.executeQuery("SELECT id, uid, from_id FROM linkrVideos" + fromWhere + uidWhere);
		while (result.next()) {
			Long videoId = result.getLong("id");
			Long uid = result.getLong("uid");
			Long fromId = result.getLong("from_id");
			
			HashSet<Long> liked;
			if (videoLiked.containsKey(videoId)) {
				liked = videoLiked.get(videoId);
			}
			else {
				liked = new HashSet<Long>();
				videoLiked.put(videoId, liked);
			}
			
			liked.add(uid);
			liked.add(fromId);
		}
		
		HashMap<Long, HashSet<Long>> linkLiked = new HashMap<Long, HashSet<Long>>();
		
		result = statement.executeQuery("SELECT id, link_id FROM linkrLinkLikes" + idWhere);
		while (result.next()) {
			Long linkId = result.getLong("link_id");
			Long uid = result.getLong("id");
			
			HashSet<Long> liked;
			if (linkLiked.containsKey(linkId)) {
				liked = linkLiked.get(linkId);
			}
			else {
				liked = new HashSet<Long>();
				linkLiked.put(linkId, liked);
			}
			
			liked.add(uid);
		}
		result = statement.executeQuery("SELECT uid, from_id, link_id FROM linkrLinks" + fromWhere + uidWhere);
		while (result.next()) {
			Long linkId = result.getLong("link_id");
			Long uid = result.getLong("uid");
			Long fromId = result.getLong("from_id");
			
			HashSet<Long> liked;
			if (linkLiked.containsKey(linkId)) {
				liked = linkLiked.get(linkId);
			}
			else {
				liked = new HashSet<Long>();
				linkLiked.put(linkId, liked);
			}
			
			liked.add(uid);
			liked.add(fromId);
		}
		
		HashSet<HashSet<Long>> sameLiked = new HashSet<HashSet<Long>>();
		
		for (long linkId : linkLiked.keySet()) {
			HashSet<Long> liked = linkLiked.get(linkId);
			sameLiked.add(liked);
		}
		for (long videoId : videoLiked.keySet()) {
			HashSet<Long> liked = videoLiked.get(videoId);
			sameLiked.add(liked);
		}
		for (long photoId : photoLiked.keySet()) {
			HashSet<Long> liked = photoLiked.get(photoId);
			sameLiked.add(liked);
		}
		for (long postId : postLiked.keySet()) {
			HashSet<Long> liked = postLiked.get(postId);
			sameLiked.add(liked);
		}
		
		for (HashSet<Long> liked: sameLiked) {
			for (long uid1 : liked) {
				for (long uid2 : liked) {
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
			}
		}
		
		/*
		 * NORMALIZING WITH THE MAX
		for (long uid1 : friendships.keySet()) {
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= max_value;
				
				friendValues.put(uid2, val);
			}
		}
		*/
		
		/* NORMALIZING WITH THE AVERAGE */
		HashSet<Long> done = new HashSet<Long>();
		double total = 0;
		int count = 0;
		
		for (long uid1 : friendships.keySet()) {
			done.add(uid1);
			
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				if (done.contains(uid2)) continue;
				
				total += friendValues.get(uid2);
				count++;
			}
		}
		double average = total / count;
		
		for (long uid1 : friendships.keySet()) {
			HashMap<Long, Double> friendValues = friendships.get(uid1);
			
			for (long uid2 : friendValues.keySet()) {
				double val = friendValues.get(uid2);
				val /= average;
				val = Math.log(val);
				
				friendValues.put(uid2, val);
			}
		}
		
		statement.close();
		return friendships;
	}
	
	/**
	 * Calculates s=Ux where U is the latent matrix and x is the user vector.
	 * 
	 * @param matrix
	 * @param idColumns
	 * @param features
	 * @return
	 */
	public static HashMap<Long, Double[]> getUserTraitVectors(Double[][] matrix, 
														HashMap<Long, Double[]> idColumns,
														HashMap<Long, Double[]> features)
	{
		HashMap<Long, Double[]> traitVectors = new HashMap<Long, Double[]>();
		
		for (long id : features.keySet()) {
			Double[] feature = features.get(id);
			Double[] vector = new Double[Constants.K];
			Double[] idColumn = idColumns.get(id);
		
			for (int x = 0; x < Constants.K; x++) {
				vector[x] = 0.0;
		
				for (int y = 0; y < feature.length; y++) {
					vector[x] += matrix[x][y] * feature[y];
				}
		
				vector[x] += idColumn[x];
			}
		
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
}
