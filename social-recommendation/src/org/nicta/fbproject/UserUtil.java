package org.nicta.fbproject;

import java.util.HashMap;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.HashSet;

public class UserUtil
{	
	public static HashMap<Long, Double[]> getUserFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Connection conn = FBMethods.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String userQuery = 
			"SELECT uid, gender, birthday, location.name, hometown.name "
			+ "FROM linkruser "
			+ "LEFT JOIN linkrlocation location ON location_id=location.id "
			+ "LEFT JOIN linkrlocation hometown ON hometown_id=location.id";
		
		ResultSet result = statement.executeQuery(userQuery);
		
		while (result.next()) {
			String sex = result.getString("gender");
			int birthYear = 0;
			String birthday = result.getString("birthday");
			if (birthday.length() == 10) {
				birthYear = Integer.parseInt(birthday.split("/")[2]);
			}
			double currentLocation = 0;
			String currentLoc = result.getString("location.name");
			if (currentLoc != null) {
				for (int x = 0; x < FBConstants.COUNTRIES.length; x++) {
					if (currentLoc.contains("country," + FBConstants.COUNTRIES[x] + ",")) {
						currentLocation = x;
						break;
					}
				}
			}
			
			double hometownLocation = 0;
			String hometownLoc = result.getString("hometown.name");
			if (hometownLoc != null) {
				for (int x = 0; x < FBConstants.COUNTRIES.length; x++) {
					if (hometownLoc.contains("country," + FBConstants.COUNTRIES[x] + ",")) {
						hometownLocation = x;
						break;
					}
				}
			}
			
			Double[] feature = new Double[FBConstants.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 1.0;
			}
			else if ("female".equals(sex)){
				feature[0] = 2.0;
			}
			else {
				feature[0] = 0.0;
			}
			
			feature[1] = birthYear / 2012.0;
			
			feature[2] = currentLocation / FBConstants.COUNTRIES.length;
			feature[3] = hometownLocation / FBConstants.COUNTRIES.length;
			
			userFeatures.put(result.getLong("uid"), feature);
		}
		
		statement.close();
		conn.close();
		
		return userFeatures;
	}
	
	public static HashMap<Long, HashSet<Long>> getFriendships()
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> friendships = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = FBMethods.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String friendQuery =
			"SELECT uid1, uid2 "
			+ "FROM linkrfriends";
		
		ResultSet result = statement.executeQuery(friendQuery);
		
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashSet<Long>());
			}
			
			friendships.get(uid1).add(uid2);
		}
		
		statement.close();
		conn.close();
		
		return friendships;
	}
	
	public static boolean areFriends(Long uid1, Long uid2, HashMap<Long, HashSet<Long>> friendships)
	{
		if ((friendships.containsKey(uid1) && friendships.get(uid1).contains(uid2))
			|| (friendships.containsKey(uid2) && friendships.get(uid2).contains(uid1))) {
			return true;
		}
		
		return false;
	}
 }
