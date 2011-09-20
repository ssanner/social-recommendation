package org.nicta.lr.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class LinkUtil 
{
	public static Map<Long, Long[]> getLinkPosters(Set<Long> ids)
		throws SQLException
	{
		HashMap<Long, Long[]> feature = new HashMap<Long, Long[]>();
		
		StringBuffer buf = new StringBuffer("SELECT from_id, uid, link_id FROM linkrLinks WHERE link_id IN (0");
		for (long id : ids) {
			buf.append(",");
			buf.append(id);
		}
		buf.append(")");
		
		Statement statement = SQLUtil.getStatement();
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
	
	public static Map<Long, String[]> getLinkText(Set<Long> ids)
		throws SQLException
	{
		HashMap<Long, String[]> feature = new HashMap<Long, String[]>();
		
		StringBuffer buf = new StringBuffer("SELECT message, description, link_id FROM linkrLinks WHERE link_id IN (0");
		for (long id : ids) {
			buf.append(",");
			buf.append(id);
		}
		buf.append(")");
		
		Statement statement = SQLUtil.getStatement();
		ResultSet result = statement.executeQuery(buf.toString());
		while (result.next()) {
			String[] link = new String[2];
			link[0] = result.getString("message");
			link[1] = result.getString("description");
			
			feature.put(result.getLong("link_id"), link);
		}
		
		statement.close();
		return feature;
	}

	
	public static Map<Long, Double[]> getLinkFeatures(Set<Long> limit)
		throws SQLException
	{
		HashMap<Long, Double[]> linkFeatures = new HashMap<Long, Double[]>();
		
		Statement statement = SQLUtil.getStatement();
		
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
			Double[] feature = new Double[Configuration.LINK_FEATURE_COUNT];
			
			feature[0] = result.getDouble("share_count") / 10000000;
			feature[1] = result.getDouble("like_count") / 10000000;
			feature[2] = result.getDouble("comment_count") / 10000000;
			
			linkFeatures.put(result.getLong("link_id"), feature);
		}
		
		statement.close();
		return linkFeatures;
	}
	
	public static Set<Long> getLinkIds()
		throws SQLException
	{
		HashSet<Long> linkIds = new HashSet<Long>();
		Statement statement = SQLUtil.getStatement();
		
		String itemQuery = "SELECT link_id FROM linkrLinks"
							+ " WHERE DATE(created_time) >= ADDDATE(CURRENT_DATE(), -" + Configuration.TRAINING_WINDOW_RANGE + ") ";
		
		ResultSet result = statement.executeQuery(itemQuery);
	
		while (result.next()) {
			linkIds.add(result.getLong("link_id"));
		}
		
		statement.close();
		return linkIds;
	}
	
	/**
	 * Given a list of links, get the list of users that have 'liked' that link on FB.
	 */
	public static Map<Long, Set<Long>> getLinkLikes(Map<Long, Long[]> links)
		throws SQLException
	{
		HashMap<Long, Set<Long>> linkLikes = new HashMap<Long, Set<Long>>();
		
		Statement statement = SQLUtil.getStatement();
		
		StringBuffer idBuf = new StringBuffer("(0");
		
		for (long linkId : links.keySet()) {
			Long[] feature = links.get(linkId);
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
				
			Set<Long> likes = linkLikes.get(linkId);	
			likes.add(feature[0]);
						
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
		
		statement.close();
		
		return linkLikes;
	}
}
