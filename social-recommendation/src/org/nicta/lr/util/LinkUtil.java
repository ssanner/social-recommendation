package org.nicta.lr.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class LinkUtil 
{
	/**
	 * Gets the link features. Link features are normalized between 0 and 1.
	 * Because we're doing online updating, get only the most recent links within a specified window.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Map<Long, Double[]> getLinkFeatures(boolean limit)
		throws SQLException
	{
		HashMap<Long, Double[]> linkFeatures = new HashMap<Long, Double[]>();
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String itemQuery = 
			"SELECT link_id, created_time, share_count, like_count, comment_count, total_count, uid, from_id "
			+ "FROM linkrLinks, linkrLinkInfo "
			+ "WHERE linkrLinks.link_hash = linkrLinkInfo.link_hash ";
		
		if (limit) {
			itemQuery += "AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.TRAINING_WINDOW_RANGE + "))";
		}
		
		ResultSet result = statement.executeQuery(itemQuery);
	
		while (result.next()) {
			Double[] feature = new Double[Constants.LINK_FEATURE_COUNT];
			
			feature[0] = result.getDouble("share_count") / 10000000;
			feature[1] = result.getDouble("like_count") / 10000000;
			feature[2] = result.getDouble("comment_count") / 10000000;
			//feature[3] = result.getDouble("uid") / Double.MAX_VALUE;
			//feature[4] = result.getDouble("from_id") / Double.MAX_VALUE;
			
			linkFeatures.put(result.getLong("link_id"), feature);
		}
		
		statement.close();
		return linkFeatures;
	}
	
	public static Map<Long, Long[]> getUnormalizedFeatures(Set<Long> ids)
		throws SQLException
	{
		HashMap<Long, Long[]> feature = new HashMap<Long, Long[]>();
		
		StringBuffer buf = new StringBuffer("SELECT from_id, uid, link_id FROM linkrLinks WHERE link_id IN (0");
		for (long id : ids) {
			buf.append(",");
			buf.append(id);
		}
		buf.append(")");
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		ResultSet result = statement.executeQuery(buf.toString());
		while (result.next()) {
			Long[] link = new Long[2];
			link[0] = result.getLong("from_id");
			link[1] = result.getLong("uid");
			
			feature.put(result.getLong("link_id"), link);
		}
		
		statement.close();
		return feature;
	}
	
	public static Map<Long, Double[]> getLinkFeatures(Set<Long> limit)
		throws SQLException
	{
		HashMap<Long, Double[]> linkFeatures = new HashMap<Long, Double[]>();
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		StringBuffer itemQuery = new StringBuffer(
			"SELECT link_id, created_time, share_count, like_count, comment_count, total_count, uid, from_id "
			+ "FROM linkrLinks, linkrLinkInfo "
			+ "WHERE linkrLinks.link_hash = linkrLinkInfo.link_hash AND link_id IN (0");
		for (long id : limit) {
			itemQuery.append(",");
			itemQuery.append(id);
		}
		itemQuery.append(")");
		
		ResultSet result = statement.executeQuery(itemQuery.toString());
	
		while (result.next()) {
			Double[] feature = new Double[Constants.LINK_FEATURE_COUNT];
			
			feature[0] = result.getDouble("share_count") / 10000000;
			feature[1] = result.getDouble("like_count") / 10000000;
			feature[2] = result.getDouble("comment_count") / 10000000;
			//feature[3] = result.getDouble("uid") / Double.MAX_VALUE;
			//feature[4] = result.getDouble("from_id") / Double.MAX_VALUE;
			
			linkFeatures.put(result.getLong("link_id"), feature);
		}
		
		statement.close();
		return linkFeatures;
	}
	
	public static Set<Long> getLinkIds(boolean limit)
		throws SQLException
	{
		HashSet<Long> linkIds = new HashSet<Long>();
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String itemQuery = 
			"SELECT link_id FROM linkrLinks";
		
		if (limit) {
			itemQuery += " WHERE DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.TRAINING_WINDOW_RANGE + "))";
		}
		
		ResultSet result = statement.executeQuery(itemQuery);
	
		while (result.next()) {
			linkIds.add(result.getLong("link_id"));
		}
		
		statement.close();
		return linkIds;
	}
	
	/**
	 * Given a list of links, get the list of users that have 'liked' that link on FB.
	 * 
	 * @param linkIds
	 * @return
	 * @throws SQLException
	 */
	public static Map<Long, Set<Long>> getLinkLikes(Map<Long, Long[]> links, boolean includeFrom)
		throws SQLException
	{
		HashMap<Long, Set<Long>> linkLikes = new HashMap<Long, Set<Long>>();
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		StringBuffer idBuf = new StringBuffer("(0");
		
		for (long linkId : links.keySet()) {
			if (includeFrom) {
				Long[] feature = links.get(linkId);
				
				if (!linkLikes.containsKey(linkId)) {
					linkLikes.put(linkId, new HashSet<Long>());
				}
				
				Set<Long> likes = linkLikes.get(linkId);
				
				likes.add(feature[0]);
			}
						
			idBuf.append(",");
			idBuf.append(linkId);
		}
		
		idBuf.append(")");
		String idString = idBuf.toString();
		
		String likeQuery = "SELECT id, link_id FROM linkrLinkLikes WHERE link_id IN " + idString;
		ResultSet result = statement.executeQuery(likeQuery.toString());
		
		while (result.next()) {
			long id = result.getLong("id");
			long linkId = result.getLong("link_id");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			Set<Long> likes = linkLikes.get(linkId);
			likes.add(id);
		}
		
		
		String clickQuery = "SELECT link_id, uid_clicked FROM linkrLinks l, trackLinkClicked t WHERE l.link=t.link AND link_id IN " + idString;
		result = statement.executeQuery(clickQuery);
		
		while (result.next()) {
			long linkId = result.getLong("link_id");
			long userId = result.getLong("uid_clicked");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			Set<Long> likes = linkLikes.get(linkId);
			
			likes.add(userId);
		}
		
		String scoreQuery = "SELECT l.link_id, t.uid FROM linkrLinks l, trackRecommendedLinks t WHERE l.link_id=t.link_id AND rating=1 AND l.link_id IN " + idString;
		result = statement.executeQuery(scoreQuery);
		
		while (result.next()) {
			long linkId = result.getLong("link_id");
			long userId = result.getLong("uid");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			Set<Long> likes = linkLikes.get(linkId);
			
			likes.add(userId);
		}
		
		statement.close();
		
		return linkLikes;
	}
}
