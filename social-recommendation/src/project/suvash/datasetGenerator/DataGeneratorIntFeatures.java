package project.suvash.datasetGenerator;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import org.nicta.lr.util.*;
import org.omg.CORBA.FREE_MEM;

public class DataGeneratorIntFeatures {

	public static final String YES = "'y'".intern();
	public static final String NO  = "'n'".intern();
	public static final String[] RATINGS = new String[]{ YES, NO };
	
	public static final int LINK_FREQ_THRESHOLD = 1;
	
	public static boolean SKIP_NEGATIVES = false;
	public static double  NEGATIVE_SKIP_PERCENT = 0.98d;
	
	/*
	 * For caching data
	 */
	public static ArrayList<EInteractionType> _featuresInt = null;
	public static ArrayList<EDirectionType>   _featuresDir = null;
	public static Map<Long,Set<Long>> _uid2all_passive_linkids_likes = null;
	public static Map<Long,Set<Long>> _uid2linkids_likes = null;
	public static Map<Long,Set<Long>> _uid2linkids_dislikes = null;
	public static Map<EInteractionType,Map<EDirectionType,Map<Long,Set<Long>>>> _int_dir2uid_linkid = null;
	
	/*
	 * Generate data for accurately labeled data from NICTA app
	 */
	public static PrintWriter _writer;
	
	public static void populateCachedData(boolean active_likes) throws SQLException {

		// For all uids in the DB, get their set of LINK likes 
		_uid2all_passive_linkids_likes = UserUtil.getLikes(ELikeType.LINK);
		
		if (active_likes) {
			
			SKIP_NEGATIVES = false;
			
			////////////////////////////////////////////////////////////////////////
			// Get dataset of likes / dislikes for active data
			////////////////////////////////////////////////////////////////////////

			_uid2linkids_likes = new HashMap<Long,Set<Long>>();
			_uid2linkids_dislikes = new HashMap<Long,Set<Long>>();
			
			String userQuery = "select uid, link_id, rating from trackRecommendedLinks where rating != 0;"; // 0 = not rated
			Statement statement = SQLUtil.getStatement();

			ResultSet result = statement.executeQuery(userQuery);
			int counter = 0;
			while (result.next()) {
				long uid = result.getLong(1);
				long link_id = result.getLong(2);
				// 0 = not rated, 1 = liked, 2 = not liked
				int rating = result.getInt(3);
				
				// SPS - cannot do this yet... 0 should be thrown out first
				// userLikes.put(link_id, (rating == 1 ? "y" : "n"));
				if (rating < 1 || rating > 2)
					continue;
				boolean is_liked = (rating == 1);
				
				Set<Long> userLikes = 
					(is_liked ? _uid2linkids_likes : _uid2linkids_dislikes).get(uid);
				if (userLikes == null){
					userLikes = new HashSet<Long>();
					(is_liked ? _uid2linkids_likes : _uid2linkids_dislikes).put(uid, userLikes);
				}
				userLikes.add(link_id);
				counter++;
			}
			System.out.println(counter);
		} else { // !active
			
			////////////////////////////////////////////////////////////////////////
			// Get dataset of likes / dislikes for passive data
			////////////////////////////////////////////////////////////////////////

			SKIP_NEGATIVES = true; // too many negatives, take one out of every NEGATIVE_SKIP_RATE
			
			//_uid2linkids_likes = _uid2all_passive_linkids_likes;
			_uid2linkids_likes = new HashMap<Long,Set<Long>>();
			_uid2linkids_dislikes = new HashMap<Long,Set<Long>>();
			Set<Long> app_user_uids = UserUtil.getAppUserIds();
		
			// Get some popular and app user links
			//HashSet<Long> popular_links = new HashSet<Long>(getLinksSortedByPopularity(LINK_FREQ_THRESHOLD));
			//HashSet<Long> app_user_links = getUnionOfLikedLinks(app_user_uids);
			//System.out.println("Popular link set size (> " + LINK_FREQ_THRESHOLD + " likes): " + popular_links.size());
			//System.out.println("App user link likes size: " + app_user_links.size());
			
			HashSet<Long> popular_app_user_links = new HashSet<Long>(getAppUserLinksSortedByPopularity(LINK_FREQ_THRESHOLD)); //new HashSet<Long>();
			//popular_app_user_links.addAll(popular_links);
			//popular_app_user_links.retainAll(app_user_links);			
			System.out.println("Popular App user link likes size: " + popular_app_user_links.size());
			
			for (Long uid : app_user_uids) {
				Set<Long> links_liked = _uid2all_passive_linkids_likes.get(uid);
				System.out.println(ExtractRelTables.UID_2_NAME.get(uid) + " has liked #links = " + (links_liked == null ? 0 : links_liked.size()));
				
				Set<Long> links_liked_to_set = _uid2linkids_likes.get(uid);
				Set<Long> links_disliked_to_set = _uid2linkids_dislikes.get(uid);
				if (links_liked_to_set == null) {
					links_liked_to_set = new HashSet<Long>();
					links_disliked_to_set = new HashSet<Long>();
					_uid2linkids_likes.put(uid, links_liked_to_set);
					_uid2linkids_dislikes.put(uid, links_disliked_to_set);					
				}
				
				// If a user has not liked a link in popular_app_user_links, consider it a dislike,
				// otherwise a like.
				for (Long link_id : popular_app_user_links) {
					if (links_liked == null || !links_liked.contains(link_id))
						links_disliked_to_set.add(link_id);
					else
						links_liked_to_set.add(link_id);
				}
			}
		}
		
		////////////////////////////////////////////////////////////////////////
		// For every user and interaction type/dir, get set of items liked by 
		// that set of alters (threshold for now at 1)
		//
		// Later, this will make it fast to generate features with one link set 
		// containment check
		////////////////////////////////////////////////////////////////////////
		
		// A parallel list of all Interaction X Direction generated 
		_featuresInt = new ArrayList<EInteractionType>();
		_featuresDir = new ArrayList<EDirectionType>();
		
		// Interaction -> Direction -> UID -> Set<Link_IDs>
		_int_dir2uid_linkid = new HashMap<EInteractionType,Map<EDirectionType,Map<Long,Set<Long>>>>();
		for (EInteractionType itype : EInteractionType.values()) {
			
			// Don't need interactions beyond real
			if (itype == EInteractionType.REAL)
				break;
			
			Map<EDirectionType,Map<Long,Set<Long>>> dir2rest = _int_dir2uid_linkid.get(itype);
			if (dir2rest == null) {
				dir2rest = new HashMap<EDirectionType,Map<Long,Set<Long>>>();
				_int_dir2uid_linkid.put(itype, dir2rest);
			}
				
			for (EDirectionType dir : EDirectionType.values()) {
	
				if (dir == EDirectionType.BIDIR)
					continue;
				else if (itype == EInteractionType.FRIENDS && dir == EDirectionType.OUTGOING)
					continue;
				
				// Maintain feature list in parallel arrays of int/dir
				_featuresInt.add(itype);
				_featuresDir.add(dir);
				
				Map<Long,Set<Long>> uid2rest = dir2rest.get(dir);
				if (uid2rest == null) {
					uid2rest = new HashMap<Long,Set<Long>>();
					dir2rest.put(dir, uid2rest);
				}

				Interaction i = UserUtil.getUserInteractions(itype, dir);
	
				System.out.println("**********************\n" + _featuresInt + "\n" + _featuresDir);
				System.out.println("**********************\n" + itype + "_" + dir + " #alters = " + i.getAllInteractions().size());

				for (Long uid : _uid2linkids_likes.keySet()) {
										
					// ** Note: these are passive likes on Facebook!
					Set<Long> alters = i.getInteractions(uid);
					//System.out.println("User id : " + Long.toString(uid) + " item: "+ alters);
					System.out.println(ExtractRelTables.UID_2_NAME.get(uid) + " " + itype + "_" + dir + " #alters = " + (alters == null ? 0 : alters.size()));					
					HashMap<Long,Integer> other_likes_id2count = ExtractRelTables.GetLikesInteractions(uid, i, _uid2all_passive_linkids_likes);
					//P(like | friend likes) = P(like and friend likes) / P(friend likes)
					//                       = F(like and friend likes) / F(friend likes)
					Set<Long> other_likes_ids = ExtractRelTables.ThresholdAtK(other_likes_id2count, /*k*/1);
					if (other_likes_ids == null)
						other_likes_ids = new HashSet<Long>();
					uid2rest.put(uid, other_likes_ids);
					 
					System.out.println("-- count of set of links liked by alters under uid,itype,dir: " + other_likes_ids.size());
				}
			}
		}
	}
	
	
	public static HashSet<Long> getUnionOfLikedLinks(Set<Long> app_user_uids) {
		HashSet<Long> liked_links = new HashSet<Long>();
		for (Long uid : app_user_uids) {
			Set<Long> uid_liked_links = _uid2all_passive_linkids_likes.get(uid);
			if (uid_liked_links != null)
				liked_links.addAll(uid_liked_links);
		}
		return liked_links;
	}

	
	public static ArrayList<Long> getLinksSortedByPopularity(int freq_threshold) throws SQLException {
		
		String userQuery = "select link_id, count(*) from linkrLinkLikes group by link_id order by count(*) desc;";
		Statement statement = SQLUtil.getStatement();
		ArrayList<Long> link_list = new ArrayList<Long>();
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			long link_id = result.getLong(1);
			long count   = result.getLong(2);
			if (count >= freq_threshold)
				link_list.add(link_id);
			else 
				break;
			//System.out.println(link_id + ":\t" + count);
		}

		return link_list;
	}
	
	
	public static ArrayList<Long> getAppUserLinksSortedByPopularity(int freq_threshold) throws SQLException {
		
		String userQuery = "select link_id, count(*) from linkrLinkLikes where uid in (select distinct uid from trackRecommendedLinks) group by link_id order by count(*) desc;";
		Statement statement = SQLUtil.getStatement();
		ArrayList<Long> link_list = new ArrayList<Long>();
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			long link_id = result.getLong(1);
			long count   = result.getLong(2);
			if (count >= freq_threshold)
				link_list.add(link_id);
			else 
				break;
			//System.out.println(link_id + ":\t" + count);
		}

		return link_list;
	}

	/*
	 * Write arff header data
	 */
	public static void writeHeader(String fileName) throws Exception {
		_writer = new PrintWriter(fileName);		
		_writer.println("@relation app-data");
		_writer.println("@attribute 'Uid' numeric");
		_writer.println("@attribute 'Item' numeric");
		_writer.println("@attribute 'Class' { 'n' , 'y' }");
		for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
			_writer.println("@attribute '" + 
					_featuresDir.get(feat_index) + "_" + 
					_featuresInt.get(feat_index) + "' { 'n', 'y' }");
		}
			
		_writer.println("@data");
	}

	/* SPS - why rewriting interactions code?  Already written
	 * SPS - don't make DB calls in inner loops... cache this data in a HashMap 
	 */
	
	/*
	 * Write known rating data
	 */
	public static void writeData(String filename) throws Exception {
		
		writeHeader(filename);
		
		System.out.println("Extracting ratings data for " + _uid2linkids_likes.size() + " users");
		
		long yes_ratings = 0;
		long no_ratings  = 0;
		long all_ratings = 0;

		for (String rating : RATINGS) {

			Map<Long,Set<Long>> uid2links = 
				(rating == YES ? _uid2linkids_likes : _uid2linkids_dislikes); 
			
			for (Entry<Long, Set<Long>> entry : uid2links.entrySet()){
				Long uid = entry.getKey();
				Set<Long> link_ids = entry.getValue();
				System.out.println("User " + ExtractRelTables.UID_2_NAME.get(uid) + " made " + link_ids.size() + " " + rating + " ratings");
				for (Long link_id : link_ids){
					
					all_ratings++;
					if (rating == YES)
						yes_ratings++;
					else {
						if (SKIP_NEGATIVES && Math.random() < NEGATIVE_SKIP_PERCENT)
							continue; 
						no_ratings++;
					}
					
					_writer.print(uid + "," + link_id + "," + rating);
					
					// Now write columns		ResultSet result = statement.executeQuery(userQuery);

					for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
						//System.out.println("Writing feature: " + _featuresDir.get(feat_index) + "_" + _featuresInt.get(feat_index));
						Set<Long> alter_likes = _int_dir2uid_linkid.
							get(_featuresInt.get(feat_index)).get(_featuresDir.get(feat_index)).get(uid);
						String feat_value = alter_likes == null ? NO :
							(alter_likes.contains(link_id) ? YES : NO);
						_writer.print("," + feat_value);
					}
					_writer.println();
				}
			}
		}
		
		double total_ratings = yes_ratings + no_ratings;
		System.out.println("Number of possible ratings: " + all_ratings);
		System.out.println("Number of yes ratings: " + yes_ratings + " -- " + (100d*yes_ratings/total_ratings) + "%");
		System.out.println("Number of no ratings:  " + no_ratings  + " -- " + (100d*no_ratings/total_ratings) + "%");
		
		_writer.close();
	}

	/*
	 * Return whether a specific user likes an item
	 */
	public static boolean userLikes(Long uid, Long lid, String row, String table) throws SQLException{
		Statement statement = SQLUtil.getStatement();
		String userQuery = "SELECT count(*) FROM " + table + " WHERE uid = " + uid + " AND " + row + " = " + lid;
		ResultSet result = statement.executeQuery(userQuery);
		result.next();			
		return (result.getInt(1) == 0 ? false : true);
	}
	
	
	public static HashMap<Long, HashSet<Long>> getLinkrUsersFriends() throws SQLException{
		HashMap<Long, HashSet<Long>> user2friends = new HashMap<Long, HashSet<Long>>();
		Statement statement = SQLUtil.getStatement();
		String Query = "SELECT uid1,uid2 FROM linkrFriends";
		ResultSet result = statement.executeQuery(Query);
		while (result.next()) {
			long user = result.getLong(1);
			long friend = result.getLong(2);
			if(!user2friends.containsKey(user)){
				HashSet<Long> friends = new HashSet<Long>();
				user2friends.put(user, friends);
			}
			user2friends.get(user).add(friend);
		}
		return user2friends;
	}

	
	public static HashMap<String, HashSet<Long>> getTrackRecommendedLinkUrls2likers(
			HashMap<Long, String> linkid2url,
			HashMap<Long, HashSet<Long>> linkId2likers
			){
		HashMap<String, HashSet<Long>> urls2likers = new HashMap<String, HashSet<Long>>();
		for(Long link_id: linkId2likers.keySet()){
			String url = linkid2url.get(link_id);
			if(!urls2likers.containsKey(url)){
				HashSet<Long> likers = new HashSet<Long>();
				urls2likers.put(url, likers);
			}
			urls2likers.get(url).addAll(linkId2likers.get(link_id));
		}
		return urls2likers;
	}
	
	
	public static HashMap<String, HashSet<Long>> getTrackRecommenedLinkUrls2LinkIds()throws SQLException{
		
		HashSet<Long> trackRecommendedLinks = new HashSet<Long>();
		HashMap<String, HashSet<Long>> linkUrl2linkIds = new HashMap<String, HashSet<Long>>();
		Statement statement = SQLUtil.getStatement();
		String Query = "SELECT distinct link_id FROM trackRecommendedLinks where rating != 0"; //restrict to links that are labelled
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			long link_id = result.getLong(1);
			trackRecommendedLinks.add(link_id);
		}
		
		Query = "SELECT link_id, link FROM linkrLinks";
		result = statement.executeQuery(Query);
		while (result.next()) {
			long linkId = result.getLong(1);
			String linkUrl = result.getString(2);
			if(!trackRecommendedLinks.contains(linkId)) {
				continue;
			}

			if(!linkUrl2linkIds.containsKey(linkUrl)){
				HashSet<Long> linkIds = new HashSet<Long>();
				linkUrl2linkIds.put(linkUrl, linkIds);
			}
			linkUrl2linkIds.get(linkUrl).add(linkId);
		}
		return linkUrl2linkIds;
	}
	

	private static HashMap<Long, String> getLinkId2url() throws SQLException{
		HashSet<Long> trackRecommendedLinks = new HashSet<Long>();
		HashMap<Long, String> linkid2url = new HashMap<Long, String>();
		Statement statement = SQLUtil.getStatement();
		String Query = "SELECT distinct link_id FROM trackRecommendedLinks where rating != 0"; //restrict to links that are labelled
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			long link_id = result.getLong(1);
			trackRecommendedLinks.add(link_id);
		}
		
		Query = "SELECT link_id, link FROM linkrLinks";
		result = statement.executeQuery(Query);
		while (result.next()) {
			long linkId = result.getLong(1);
			String linkUrl = result.getString(2);
			if(!trackRecommendedLinks.contains(linkId)) {
				continue;
			}
			linkid2url.put(linkId,linkUrl);
		}
		return linkid2url;
	}

	public static HashMap<Long, HashSet<Long>> getLinkrlinklikers() throws SQLException{
		HashSet<Long> trackRecommendedLinks = new HashSet<Long>();
		HashMap<Long, HashSet<Long>> link2likers = new HashMap<Long, HashSet<Long>>();
		Statement statement = SQLUtil.getStatement();
		String Query = "SELECT distinct link_id FROM trackRecommendedLinks where rating != 0";
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			long link_id = result.getLong(1);
			trackRecommendedLinks.add(link_id);
		}
		
		System.out.println(trackRecommendedLinks.size());
		Query = "SELECT link_id, uid, id FROM linkrLinkLikes";
		result = statement.executeQuery(Query);
		while (result.next()) {
			long linkId = result.getLong(1);
			long postedBy = result.getLong(2);
			long likedBy = result.getLong(3);
			if(!trackRecommendedLinks.contains(linkId)) {
				//System.out.format("%d not contained\n",linkId);
				continue;
			}

			if(!link2likers.containsKey(linkId)){
				HashSet<Long> likers = new HashSet<Long>();
				//likers.add(postedBy); //Assumption: people posting links implicitly likes the posted link
				link2likers.put(linkId, likers);
			}
			link2likers.get(linkId).add(likedBy);
		}
		return link2likers;
	}
	
	
	public static HashMap<Long, HashMap<Long, HashSet<Long>>> getLink2Uid2Friends(
			HashMap<Long, HashSet<Long>> usersFriends,
			HashMap<Long, HashSet<Long>> linkLikers,
			boolean onlyLikedData) throws SQLException
	{
		HashMap<Long, HashMap<Long, HashSet<Long>>> link2uid2friends = new HashMap<Long, HashMap<Long,HashSet<Long>>>();
		Statement statement = SQLUtil.getStatement();
		String Query = "SELECT uid, link_id, rating FROM trackRecommendedLinks where rating != 0";
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			long uid = result.getLong(1);
			long link_id = result.getLong(2);
			long rating = result.getLong(3);
			if(rating == 2 && onlyLikedData) continue;
			HashSet<Long> friends = usersFriends.get(uid);
			HashSet<Long> likers = linkLikers.get(link_id);
			if(friends == null){
				continue;
			}
			if(likers == null){
				continue;
			}
			HashSet<Long> temp = new HashSet<Long>(likers);
			temp.retainAll(friends);
			
			if(!link2uid2friends.containsKey(link_id)){
				HashMap<Long, HashSet<Long>> uid2friends = new HashMap<Long, HashSet<Long>>();
				link2uid2friends.put(link_id, uid2friends);
			}
			
			link2uid2friends.get(link_id).put(uid,temp);
		}
		return link2uid2friends;
	}
	
	
	public static int countItemslikedByKFriends( HashMap<Long, HashMap<Long, HashSet<Long>>> link2uid2friends, int k){
		int count = 0;
		for(Long link_id : link2uid2friends.keySet()){
			for(Long uid  : link2uid2friends.get(link_id).keySet()){
				if(link2uid2friends.get(link_id).get(uid).size() >= k){
					count++;
				}
			}
		}
		return count;
	}
	
	
	public static HashMap<Long, HashSet<Long>> getTrackRecommendedUsersMembership(String table) throws SQLException{
		Long gid;
		Long uid;
		HashSet<Long> groups = new HashSet<Long>();
		HashMap<Long, HashSet<Long>> groupMembership = new HashMap<Long, HashSet<Long>>();
		Statement statement = SQLUtil.getStatement();
		String Query = "select id, uid from " + table +" where uid in (select distinct uid from trackRecommendedLinks where rating != 0);";
		ResultSet result = statement.executeQuery(Query);
		
		while(result.next()){
			groups.add(result.getLong(1));
		}
				
		StringBuffer buf = new StringBuffer("SELECT id, uid FROM "+table+" WHERE id IN (0");
		for (Long id : groups) {
			buf.append(",");
			buf.append(id.toString());
		}
		buf.append(")");
		statement.close();
		
		Statement statement1 = SQLUtil.getStatement();
		ResultSet  result1 = statement1.executeQuery(buf.toString());

		while(result1.next()){
			gid = result1.getLong(1);
			uid = result1.getLong(2);
			if(!groupMembership.containsKey(gid)){
				groupMembership.put(gid,new HashSet<Long>());
			}
			groupMembership.get(gid).add(uid);
		}
		return groupMembership;
	}
	
	
	public static HashMap<Long,HashMap<Long, ArrayList<Long>>> getFeaturesDataset(HashMap<Long, HashSet<Long>> groupMembership) throws SQLException{
		HashMap<Long,HashMap<Long, ArrayList<Long>>> features = new HashMap<Long,HashMap<Long, ArrayList<Long>>>();
		Long id;
		Long uid;
		Long from_id;
		Integer rating;
		Statement statement = SQLUtil.getStatement();
		String Query = "select link_id, uid, from_id, rating from trackRecommendedLinks where rating != 0;";
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			id = result.getLong(1);
			uid = result.getLong(2);
			from_id = result.getLong(3);
			rating = result.getInt(4);
			if(rating == 2) rating = 0; // rating 2 means dislike
			ArrayList<Long> feature = new ArrayList<Long>();
			feature.add(new Long(rating));
			for(Long gid: groupMembership.keySet()){
				HashSet<Long> members = groupMembership.get(gid);
				if(members.contains(uid) && members.contains(from_id)){
					feature.add(1l);
				}
				else{
					feature.add(0l);
				}
			}
			if(!features.containsKey(uid)){
				features.put(uid, new HashMap<Long, ArrayList<Long>>());
			}
			features.get(uid).put(id, feature);
		}
		return features;
 	}
	
	//filter out the like data without any form of membership information
	public static HashMap<Long,HashMap<Long, ArrayList<Long>>> filterNoInteraction(HashMap<Long,HashMap<Long, ArrayList<Long>>> dataset){
		HashMap<Long,HashMap<Long, ArrayList<Long>>> filtered = new HashMap<Long, HashMap<Long,ArrayList<Long>>>();
		int count = 0;
		for(Long uid: dataset.keySet()){
			for(Long link_id: dataset.get(uid).keySet()){
				ArrayList<Long> features = dataset.get(uid).get(link_id);
				HashSet<Long> members = new HashSet<Long>(features.subList(1, features.size()));
				if(features.get(0) == 1l && members.size() == 1 && members.contains(0l)){
					continue;
				}
				if(!filtered.containsKey(uid)){
					HashMap<Long, ArrayList<Long>> link_id2features = new HashMap<Long, ArrayList<Long>>();
					filtered.put(uid, link_id2features);
				}
				filtered.get(uid).put(link_id, features);
			}
		}
		return filtered;
	}
	
	public static HashMap<Long,HashMap<Long, ArrayList<Long>>> getFeaturesDataset(HashMap<Long, HashSet<Long>> groupMembership,
			HashMap<String, HashSet<Long>> url2likers, HashMap<Long, String> linkid2url, boolean isBoolean) throws SQLException{
		
		HashMap<Long,HashMap<Long, ArrayList<Long>>> features = new HashMap<Long,HashMap<Long, ArrayList<Long>>>();
		Long id;
		Long uid;
		Long from_id;
		Integer rating;
		Statement statement = SQLUtil.getStatement();
		String Query = "select link_id, uid, from_id, rating from trackRecommendedLinks where rating != 0;";
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			id = result.getLong(1);
			uid = result.getLong(2);
			from_id = result.getLong(3);
			rating = result.getInt(4);
			
			if(rating == 2) rating = 0; // rating 2 means dislike
			ArrayList<Long> feature = new ArrayList<Long>();
			feature.add(new Long(rating));
//			ArrayList<Long> featureprime = new ArrayList<Long>();
			
			String url = linkid2url.get(id);
			HashSet<Long> likers = url2likers.get(url);
			if(likers == null) continue; //no one in linkr database liked this link
			for(Long gid: groupMembership.keySet()){
				HashSet<Long> members = groupMembership.get(gid);
				
				HashSet<Long> temp = new HashSet<Long>(likers);
				temp.retainAll(members);
				if(isBoolean){
					feature.add(temp.size() > 0 ? 1l: 0l);
				}
				else{
					feature.add(new Long(temp.size()));
//					if(members.contains(uid)){
//						feature.add(new Long(temp.size()));
//						featureprime.add(0l);						
//					}
//					else{
//						featureprime.add(new Long(temp.size()));
//						feature.add(0l);
//					}
				}
			}
			if(!features.containsKey(uid)){
				features.put(uid, new HashMap<Long, ArrayList<Long>>());
			}
			//feature.addAll(featureprime);
			features.get(uid).put(id, feature);
		}
		return features;
 	}
	
	public static HashMap<Long, Integer> getGroupId2size(HashMap<Long, HashSet<Long>> groupMembership) throws SQLException{
		HashMap<Long, Integer> gid2size = new HashMap<Long, Integer>();
		for(Long gid: groupMembership.keySet()){
			gid2size.put(gid,groupMembership.get(gid).size());
		}
		return gid2size;
	}
	
	public static HashMap<Long,HashMap<Long, Integer>> getActiveDataset() throws SQLException{
		Long uid;
		Long link_id;
		Integer rating;
		
		Statement statement = SQLUtil.getStatement();
		String Query = "SELECT uid, link_id, rating FROM trackRecommendedLinks where rating != 0";
		ResultSet result = statement.executeQuery(Query);
		HashMap<Long,HashMap<Long, Integer>> dataset= new HashMap<Long, HashMap<Long,Integer>>();

		while(result.next()){
			uid = result.getLong(1);
			link_id = result.getLong(2);
			rating = result.getInt(3);
			if(rating != 1){
				rating = 0; 
			}
			if(!dataset.containsKey(uid)){
				HashMap<Long, Integer> link_id2like = new HashMap<Long, Integer>();
				dataset.put(uid, link_id2like);
			}
			dataset.get(uid).put(link_id, rating);
		}
		return dataset;
	}
	
	
	public static void writeArffHeader(PrintWriter writer, int features_size){
		writer.println("@relation app-data");
		writer.println("@attribute 'Uid' numeric");
		writer.println("@attribute 'Item' numeric");
		writer.println("@attribute 'Class' { 'n' , 'y' }");
		for(int i = 1; i <= features_size - 1 ; i++ ){
			writer.println("@attribute 'Feat_"+ Integer.toString(i) +"' { 'n' , 'y' }");
		}
		writer.println("@data");
	}

	public static void writeArffHeader(PrintWriter writer, int features_size, String FeatureType){
		writer.println("@relation app-data");
		writer.println("@attribute 'Uid' numeric");
		writer.println("@attribute 'Item' numeric");
		writer.println("@attribute 'Class' {'0', '1'}");
		for(int i = 1; i <= features_size - 1 ; i++ ){
			writer.println("@attribute 'Feat_"+ Integer.toString(i) +"' "+FeatureType);
		}
		writer.println("@data");
	}
	
	public static void writeArffHeader(PrintWriter writer, int features_size, int[] membership_size, String FeatureType){
		writer.println("@relation app-data");
		writer.println("@attribute 'Uid' numeric");
		writer.println("@attribute 'Item' numeric");
		writer.println("@attribute 'Class' {'0', '1'}");
		for(int i = 1; i <= features_size - 1 ; i++ ){
			writer.println("@attribute 'Feat_"+ Integer.toString(i) +"_'"+Integer.toBinaryString(membership_size[i-1]) +" "+FeatureType);
		}
		writer.println("@data");
	}
	
	public static void writeArffData(HashMap<Long,HashMap<Long, ArrayList<Long>>> dataset, String outfile, boolean isBoolean) throws Exception{
		PrintWriter writer = new PrintWriter(outfile);		
		int feature_size = 0;
		for(Long uid:dataset.keySet()){
			for(Long link_id: dataset.get(uid).keySet()){
				feature_size = dataset.get(uid).get(link_id).size();
				break;
			}
			break;
		}
		
		if(isBoolean)
			writeArffHeader(writer, feature_size);
		else
			writeArffHeader(writer, feature_size, "numeric");

		for(Long uid:dataset.keySet()){
			for(Long link_id: dataset.get(uid).keySet()){
				StringBuffer data = new StringBuffer();
				data.append(uid.toString()+","+link_id.toString());
				int count = 0;
				for(Long feature: dataset.get(uid).get(link_id)){
					String tmp;
					if(isBoolean){
						tmp = (feature == 0 ?",'n'" : ",'y'");
					}
					else{
						if(count == 0){
							tmp = ",'"+feature+"'";
							count = 1;
						}
						else
							tmp = ","+feature;
					}
					data.append(tmp);
				}
				writer.println(data.toString());
			}
		}
		
		writer.close();
	}
	
	
	public static void writeArffData(HashMap<Long,HashMap<Long, ArrayList<Long>>> dataset, String outfile) throws Exception{
		PrintWriter writer = new PrintWriter(outfile);		
		int feature_size = 0;
		for(Long uid:dataset.keySet()){
			for(Long link_id: dataset.get(uid).keySet()){
				feature_size = dataset.get(uid).get(link_id).size();
				break;
			}
			break;
		}
		writeArffHeader(writer, feature_size);
		for(Long uid:dataset.keySet()){
			for(Long link_id: dataset.get(uid).keySet()){
				StringBuffer data = new StringBuffer();
				data.append(uid.toString()+","+link_id.toString());
				for(Long feature: dataset.get(uid).get(link_id)){
					if(feature == 0)
						data.append(",'n'");
					else
						data.append(",'y'");
				}
				writer.println(data.toString());
			}
		}
		
		writer.close();
	}
	
	
	public static HashMap<Long,HashMap<Long, ArrayList<Long>>> mergeDatasets(ArrayList<HashMap<Long,HashMap<Long, ArrayList<Long>>> > memberships){
		
		HashMap<Long,HashMap<Long, ArrayList<Long>>> mergedDatasets = new HashMap<Long, HashMap<Long,ArrayList<Long>>>();
		HashMap<Long,HashMap<Long, ArrayList<Long>>> firstDataset = memberships.get(0);
		for(Long uid: firstDataset.keySet()){
			for(Long link_id: firstDataset.get(uid).keySet()){
				ArrayList<Long> dataset = new ArrayList<Long>();
				boolean start = true;
				for(int i = 0; i < memberships.size(); i++){
					if(start){
						dataset.addAll(memberships.get(i).get(uid).get(link_id));
						start = false;
					}
					else{
						int length =memberships.get(i).get(uid).get(link_id).size();
						dataset.addAll(memberships.get(i).get(uid).get(link_id).subList(1, length));

					}
				}
				if(!mergedDatasets.containsKey(uid)){
					mergedDatasets.put(uid, new HashMap<Long, ArrayList<Long>>());
				}
				mergedDatasets.get(uid).put(link_id, dataset);
			}
		}
		return mergedDatasets;
	}
	
	public static HashMap<Long, Long> getGroupSize(int threshold) throws SQLException{
		Long gid;
		Long size;
		HashMap<Long, Long> group2size = new HashMap<Long, Long>();
		Statement statement = SQLUtil.getStatement();
		String Query = "select id, Count(uid) as MEMBERS from linkrGroups group by id order by MEMBERS desc";
		ResultSet result = statement.executeQuery(Query);
		while(result.next()){
			gid = result.getLong(1);
			size = result.getLong(2);
			if (size <= threshold) break;
			group2size.put(gid, size);
		}
		return group2size;
		
	}
	
	public static void main(String[] args) throws Exception {
		populateCachedData(true /* active */);
		writeData("active_data.arff");
//		populateCachedData(false /* active */);
//		writeData("passive_data.arff");
		
//		HashMap<Long, HashSet<Long>> usersFriends = getLinkrUsersFriends();
//		HashMap<Long, HashSet<Long>> linkLikers = getLinkrlinklikers();
//		HashMap<Long, HashMap<Long, HashSet<Long>>> onlyLikedData =  getLink2Uid2Friends(usersFriends, linkLikers, true);
//		HashMap<Long, HashMap<Long, HashSet<Long>>> allData =  getLink2Uid2Friends(usersFriends, linkLikers, false);
//		for(int i = 0; i <= 3; i++){
//			int likes = countItemslikedByKFriends(onlyLikedData,i); 
//			int total = countItemslikedByKFriends(allData,i); 
//			double pLike = likes / (double) total;
//			System.out.format("Threashold : %d pLike: %f Total: %d\n", i, pLike, total);
//
//		}

//		HashMap<Long,HashMap<Long, Integer>> activeDataset = getActiveDataset();
//		writeCsvData(activeDataset, "active_data.csv");
		
		
//		HashMap<Long, HashSet<Long>> groupMembership = getTrackRecommendedUsersMembership("linkrGroups");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> groupDataset = getFeaturesDataset(groupMembership);
//		
//		HashMap<Long, HashSet<Long>> activityMembership = getTrackRecommendedUsersMembership("linkrActivities");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> activityDataset = getFeaturesDataset(activityMembership);
//
//		HashMap<Long, HashSet<Long>> athletesMembership = getTrackRecommendedUsersMembership("linkrFavoriteAthletes");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> athletesDataset = getFeaturesDataset(athletesMembership);
//
//		HashMap<Long, HashSet<Long>> booksMembership = getTrackRecommendedUsersMembership("linkrFavoriteAthletes");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> booksDataset = getFeaturesDataset(booksMembership);
//
//		HashMap<Long, HashSet<Long>> interestsMembership = getTrackRecommendedUsersMembership("linkrInterests");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> interestsDataset = getFeaturesDataset(interestsMembership);
//
//		HashMap<Long, HashSet<Long>> moviesMembership = getTrackRecommendedUsersMembership("linkrMovies");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> moviesDataset = getFeaturesDataset(moviesMembership);
//
//		HashMap<Long, HashSet<Long>> musicMembership = getTrackRecommendedUsersMembership("linkrMusic");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> musicDataset = getFeaturesDataset(musicMembership);
//
//		HashMap<Long, HashSet<Long>> sportsMembership = getTrackRecommendedUsersMembership("linkrSports");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> sportsDataset = getFeaturesDataset(sportsMembership);
//
//		HashMap<Long, HashSet<Long>> teamsMembership = getTrackRecommendedUsersMembership("linkrFavoriteTeams");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> teamsDataset = getFeaturesDataset(teamsMembership);
//
//		HashMap<Long, HashSet<Long>> tvMembership = getTrackRecommendedUsersMembership("linkrTelevision");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> tvDataset = getFeaturesDataset(tvMembership);
//
//		ArrayList<HashMap<Long,HashMap<Long, ArrayList<Long>>>> datasets = new ArrayList<HashMap<Long,HashMap<Long,ArrayList<Long>>>>();
//		datasets.add(groupDataset);
//		datasets.add(activityDataset);
//		datasets.add(athletesDataset);
//		datasets.add(booksDataset);
//		datasets.add(interestsDataset);
//		datasets.add(moviesDataset);
//		datasets.add(musicDataset);
//		datasets.add(sportsDataset);
//		datasets.add(teamsDataset);
//		datasets.add(tvDataset);
//		
//		HashMap<Long,HashMap<Long, ArrayList<Long>>>  dataset = mergeDatasets(datasets);

//		writeArffData(groupDataset, "active_group_data.arff");

//		HashMap<Long, HashSet<Long>> linkid2likers = getLinkrlinklikers();
//		HashMap<Long,String> linkid2url = getLinkId2url();
//		HashMap<String, HashSet<Long>> linkUrls2likers = getTrackRecommendedLinkUrls2likers(linkid2url, linkid2likers);
//		
//		HashMap<Long, HashSet<Long>> groupMembership = getTrackRecommendedUsersMembership("linkrGroups");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> groupDataset = getFeaturesDataset(groupMembership, linkUrls2likers, linkid2url, true);
//		HashMap<Long, Long> groupSizes = getGroupSize(0);
		
//		for(Long gid: groupMembership.keySet()){
//			System.out.format("%d,",groupSizes.get(gid));
//		}
//		System.out.println();
		
//		writeArffData(groupDataset, "active_group_membership_binary_data.arff",true);
//		
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> filteredGroupDataset = filterNoInteraction(groupDataset);		
//		writeArffData(filteredGroupDataset, "active_group_membership_integer_filtered_data.arff",false);
		
//		HashMap<Long, HashSet<Long>> pageMembership = getTrackRecommendedUsersMembership("linkrLikes");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> pagesDataset = getFeaturesDataset(pageMembership,linkUrls2likers, linkid2url, true);
//		writeArffData(pagesDataset, "active_page_membership_integer_data.arff",false);
//		
//		
//		HashMap<Long, HashSet<Long>> activityMembership = getTrackRecommendedUsersMembership("linkrActivities");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> activityDataset = getFeaturesDataset(activityMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> athletesMembership = getTrackRecommendedUsersMembership("linkrFavoriteAthletes");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> athletesDataset = getFeaturesDataset(athletesMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> booksMembership = getTrackRecommendedUsersMembership("linkrFavoriteAthletes");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> booksDataset = getFeaturesDataset(booksMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> interestsMembership = getTrackRecommendedUsersMembership("linkrInterests");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> interestsDataset = getFeaturesDataset(interestsMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> moviesMembership = getTrackRecommendedUsersMembership("linkrMovies");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> moviesDataset = getFeaturesDataset(moviesMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> musicMembership = getTrackRecommendedUsersMembership("linkrMusic");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> musicDataset = getFeaturesDataset(musicMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> sportsMembership = getTrackRecommendedUsersMembership("linkrSports");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> sportsDataset = getFeaturesDataset(sportsMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> teamsMembership = getTrackRecommendedUsersMembership("linkrFavoriteTeams");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> teamsDataset = getFeaturesDataset(teamsMembership,linkUrls2likers, linkid2url, true);
//
//		HashMap<Long, HashSet<Long>> tvMembership = getTrackRecommendedUsersMembership("linkrTelevision");
//		HashMap<Long,HashMap<Long, ArrayList<Long>>> tvDataset = getFeaturesDataset(tvMembership,linkUrls2likers, linkid2url, true);
//
//		ArrayList<HashMap<Long,HashMap<Long, ArrayList<Long>>>> datasets = new ArrayList<HashMap<Long,HashMap<Long,ArrayList<Long>>>>();
//		datasets.add(groupDataset);
//		datasets.add(pagesDataset);
//		datasets.add(activityDataset);
//		datasets.add(athletesDataset);
//		datasets.add(booksDataset);
//		datasets.add(interestsDataset);
//		datasets.add(moviesDataset);
//		datasets.add(musicDataset);
//		datasets.add(sportsDataset);
//		datasets.add(teamsDataset);
//		datasets.add(tvDataset);
//		
//		HashMap<Long,HashMap<Long, ArrayList<Long>>>  dataset = mergeDatasets(datasets);
//		writeArffData(dataset, "active_groups_interests_binary_data.arff",true);
	}


}
