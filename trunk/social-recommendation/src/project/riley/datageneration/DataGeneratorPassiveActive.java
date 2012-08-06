package project.riley.datageneration;

/** Generate .arff file of (uid, link_id, like/dislike, vec(features)) from App data
 * 
 * @author Riley Kidd
 * @author Scott Sanner
 * 
 * Note: Currently ignoring posting a link as evidence of liking, but probably accounts
 *       for a negligible fraction of the data.
 */


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import org.nicta.lr.util.*;

import project.ifilter.messagefrequency.PredictiveWords;

public class DataGeneratorPassiveActive {

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
	public static String[] demographics_types = {"gender","locale","birthday","location"};
	public static String[] group_types = {"linkrActivities", "linkrBooks", "linkrFavoriteAthletes", "linkrFavoriteTeams", "linkrInspirationalPeople", "linkrInterests", "linkrMovies", "linkrMusic", "linkrSports", "linkrTelevision", "linkrSchoolWith", "linkrWorkWith"};
	public static String[] conversation_types = {"linkrLinkComments","linkrPhotoComments","linkrPostComments","linkrVideoComments"};

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
			}

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

		for (String demographic : demographics_types){
			_writer.println("@attribute 'demographic_" + demographic +  "' { " + NO + ", " + YES + " }");
		}

		for (String group : group_types){
			_writer.println("@attribute 'group_" + group +  "' { " + NO + ", " + YES + " }");
		}

		for (String conversation : conversation_types){
			_writer.println("@attribute 'conversation_" + EDirectionType.INCOMING + "_" + conversation +  "' { " + NO + ", " + YES + " }");
			_writer.println("@attribute 'conversation_" + EDirectionType.OUTGOING + "_" + conversation +  "' { " + NO + ", " + YES + " }");
		}

		_writer.println("@data");
	}

	/* SPS - why rewriting interactions code?  Already written
	 * SPS - don't make DB calls in inner loops... cache this data in a HashMap 
	 */

	/*
	 * Write known rating data
	 */
	public static void writeData(String filename, int interaction_threshold) throws Exception {

		writeHeader(filename);
		int size = 0;

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

					int count = 0;
					all_ratings++;
					if (rating == YES)
						yes_ratings++;
					else {
						if (SKIP_NEGATIVES && Math.random() < NEGATIVE_SKIP_PERCENT)
							continue; 
						no_ratings++;
					}

					//_writer.print(uid + "," + link_id + "," + rating);
					StringBuffer columns = new StringBuffer(uid + "," + link_id + "," + rating);

					// Now write columns
					for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
						//System.out.println("Writing feature: " + _featuresDir.get(feat_index) + "_" + _featuresInt.get(feat_index));
						Set<Long> alter_likes = _int_dir2uid_linkid.get(_featuresInt.get(feat_index)).get(_featuresDir.get(feat_index)).get(uid);
						String feat_value = alter_likes == null ? NO : (alter_likes.contains(link_id) ? YES : NO);
						//_writer.print("," + feat_value);
						columns.append("," + feat_value);
						if (feat_value == YES){
							count++;
						}
					}
					columns.append(getAppUserDemographics(link_id,uid));
					columns.append(getAppUserGroups(link_id,uid));
					columns.append(getAppConversationContent(link_id,uid));
					//_writer.println();
					if (count >= interaction_threshold)
						_writer.println(columns.toString());
						size++;
				}
			}
		}

		double total_ratings = yes_ratings + no_ratings;
		System.out.println("Number of possible ratings: " + all_ratings);
		System.out.println("Number of yes ratings: " + yes_ratings + " -- " + (100d*yes_ratings/total_ratings) + "%");
		System.out.println("Number of no ratings:  " + no_ratings  + " -- " + (100d*no_ratings/total_ratings) + "%");

		System.out.println("For interaction theshold of size " + interaction_threshold + " data set size " + size);
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

	/*
	 * Return demographics data for a specific user and link like id
	 */
	public static String getAppUserDemographics(long link_id, long uid) throws Exception {

		StringBuffer results = new StringBuffer();

		for (String demographic : demographics_types){
			String userQuery;
			if (demographic.equals("birthday")){
				// for birthday need to check for different birthday table entries, 02/1989 12/03/2001 04/11
				userQuery = "select count(*) from linkrUser lu inner join linkrLinkLikes ll on ll.link_id=" + link_id + " and ll.id != " + uid + " and ll.id=lu.uid and lu." + demographic + " != '' and (right(" + demographic + ",4) = right((select "  + demographic + " from linkrUser where uid = " + uid + "),4) and instr(right(" + demographic + ",4),'/')=0);";
			} else if (demographic.equals("location")){
				// for location join is on a slightly different condition(0) so just a new case
				userQuery = "select count(*) from linkrUser lu inner join linkrLinkLikes ll on ll.link_id=" + link_id + " and ll.id = lu.uid and lu.location_id != 0 and lu.uid != " + uid + " and lu.location_id=(select location_id from linkrUser where uid = " + uid + ");";
			} else {
				// otherwise join tables on 'same' conditions
				userQuery = "select count(*) from linkrUser lu inner join linkrLinkLikes ll on ll.link_id=" + link_id + " and ll.id != " + uid + " and ll.id=lu.uid and lu." + demographic + " != '' and lu." + demographic + "=(select " + demographic + " from linkrUser where uid = " + uid + ");";
			}

			results.append(featureValue(userQuery));
		}		

		return results.toString();
	}

	/*
	 * Return groups data for a specific user and link like id
	 */
	public static String getAppUserGroups(long link_id, long uid) throws Exception {

		//> this persons activities conditioned on activities size
		//select la.id from linkrActivities la inner join linkrActivities sz on la.uid=1605476473 and la.id = sz.id group by la.id having count(*) > 1 and count(*) < 10000;

		//> activities of people who like the link and aren't ^ person
		//select ll.id from linkrLinkLikes ll inner join linkrActivities la where ll.link_id = 162631113776237 and ll.id != 1605476473 and ll.id=la.uid;

		//> number of people who like the link and share the same activity in a size range
		//select count(*) from linkrLinkLikes ll inner join linkrActivities la where ll.link_id = 162631113776237 and ll.id != 1605476473 and ll.id=la.uid and la.uid = any(select la.id from linkrActivities la inner join linkrActivities sz on la.uid=1605476473 and la.id = sz.id group by la.id having count(*) > 1 and count(*) < 10000);

		int min_limit = 1;					// minimum group size
		int max_limit = Integer.MAX_VALUE;	// maximum group size

		StringBuffer results = new StringBuffer();				

		for (String group : group_types){
			String query;
			if (group.equals("linkrSchoolWith") || group.equals("linkrWorkWith")){
				//  select count(distinct(uid1)),count(distinct(uid2)) from linkrSchoolWith; << not bidirectional..?
				query = "select count(*) from " + group + " ls inner join linkrLinkLikes ll on ll.id != " + uid + " and ll.link_id=" + link_id + " and ls.uid2=ll.id;";
			} else {
				query = "select count(*) from linkrLinkLikes ll inner join " + group  + " la where ll.link_id = " + link_id + " and ll.id != " + uid + " and ll.id=la.uid and la.uid = any(select la.id from " + group  + " la inner join " + group  + " sz on la.uid=" + uid + " and la.id = sz.id group by la.id having count(*) > " + min_limit + " and count(*) < " + max_limit + ")";
			}
			results.append(featureValue(query));
		}	

		return results.toString();
	}

	/*
	 * return conversation content 
	 */
	public static String getAppConversationContent(long link_id, long uid) throws Exception{
		PredictiveWords.buildMessagesDictionary();

		StringBuffer results = new StringBuffer();
		
		ArrayList<String> topNWords = PredictiveWords.getTopN(3);
		for (String table : conversation_types){
			StringBuffer query = new StringBuffer("select count(*) from " + table + " where message like '%" + topNWords.get(0) /* assume at least one */ + "%' ");
			for (int i = 1; i < topNWords.size(); i++){
				query.append("or message like '%" + topNWords.get(i) + "%' ");
			}
			results.append(featureValue(query.toString() + "and uid = " + uid + ";"));		/* incoming */
			results.append(featureValue(query.toString() + "and from_id = " + uid + ";"));  /* outgoing */			
		}

		return results.toString();
	}

	/*
	 * return the result of a query, expecting a count(*) result, YES if count is more than 1, NO otherwise
	 */
	public static String featureValue(String query) throws Exception{
		Statement statement = SQLUtil.getStatement();		
		ResultSet result = statement.executeQuery(query);
		String feat_val = null;
		while (result.next()) {
			feat_val = "," + (result.getInt(1) > 0 ? YES : NO);
		}
		return feat_val;
	}

	public static void main(String[] args) throws Exception {
		//populateCachedData(true /* active */);
		//writeData("active_data.arff");
		//populateCachedData(false /* active */);
		//writeData("passive_data.arff");
		System.out.println(getAppConversationContent(162631113776237L,670845000));

	}

}
