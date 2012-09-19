package project.riley.datageneration;

/** Generate .arff file of (uid, link_id, like/dislike, vec(features)) from App data
 * 
 * @author Riley Kidd
 * @author Scott Sanner
 * 
 * Note: Currently ignoring posting a link as evidence of liking, but probably accounts
 *       for a negligible fraction of the data.
 */

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.nicta.lr.util.*;

import project.ifilter.messagefrequency.PredictiveWords;

public class DataGeneratorPassiveActive {

	public static final String YES = "'y'".intern();
	public static final String NO  = "'n'".intern();
	public static final String PRE = ",".intern();
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
	public static Map<Long, Map<Long, Double>> friendships = null; 
	public static Map<Long,Set<Long>> _uid2linkids_likes = null;
	public static Map<Long,Set<Long>> _uid2linkids_dislikes = null;
	public static Map<EInteractionType,Map<EDirectionType,Map<Long,Set<Long>>>> _int_dir2uid_linkid = null;

	public static HashMap<Long, Set<Long>> additionalLinkFeatures = new HashMap<Long, Set<Long>>();

	public static ArrayList<String> topNWords;
	public static int stepSize = 1000;

	public static Set<Long> linksSeen = new HashSet<Long>();
	public static Set<Long> usersSeen = new HashSet<Long>();

	public static Map<Long,String> userGenders = new HashMap<Long,String>();
	public static Map<Long,Integer> userBirthdays = new HashMap<Long,Integer>();
	public static Map<Long,String> userLocale = new HashMap<Long,String>();

	public static Map<Long,Set<Long>> userGroupMemberships = new HashMap<Long,Set<Long>>();
	public static Map<Long,Set<Long>> userPageMemberships = new HashMap<Long,Set<Long>>();

	public static Map<String, HashMap<Long, Set<Long>>> userTraits = new HashMap<String,HashMap<Long,Set<Long>>>();	

	public static int topGroupsN = 1000;
	public static int topWordsN = 1000;
	public static int topPagesN = 1000;

	/*
	 * Generate data for accurately labeled data from NICTA app
	 */
	public static PrintWriter _writer;

	public static void populateCachedData(boolean active_likes) throws Exception {

		topNWords = PredictiveWords.getTopN(topWordsN);
		// For all uids in the DB, get their set of LINK likes 
		_uid2all_passive_linkids_likes = UserUtil.getLikes(ELikeType.LINK);
		friendships = UserUtil.getFriendships();

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
				linksSeen.add(link_id);
				usersSeen.add(uid);
			}
			result.close();
			statement.close();

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

				//System.out.println("**********************\n" + _featuresInt + "\n" + _featuresDir);
				//System.out.println("**********************\n" + itype + "_" + dir + " #alters = " + i.getAllInteractions().size());

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

					//System.out.println("-- count of set of links liked by alters under uid,itype,dir: " + other_likes_ids.size());
				}
			}
		}

		extractLinkFeatures();
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

		result.close();
		statement.close();

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

		result.close();
		statement.close();

		return link_list;
	}

	/*
	 * Write arff header data
	 */
	public static String[] demographics_types = new String[]{"isMale","isFemale","sameGender","sameBirthRange","sameLocale"};
	public static String[] user_traits = {"linkrActivities", "linkrBooks", "linkrFavoriteAthletes", "linkrFavoriteTeams", "linkrInspirationalPeople", "linkrInterests", "linkrMovies", "linkrMusic", "linkrSports", "linkrTelevision", "linkrSchoolWith", "linkrWorkWith"};
	public static void writeHeader(String fileName) throws Exception {
		System.out.println("Writing to " + fileName);
		_writer = new PrintWriter(fileName);		
		_writer.println("@relation app-data");
		_writer.println("@attribute 'Uid' numeric");
		_writer.println("@attribute 'Item' numeric");
		_writer.println("@attribute 'Class' { 'n' , 'y' }");

		_writer.println("@attribute 'FriendLiked' { 'n' , 'y' }");

		for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
			_writer.println("@attribute '" + 
					_featuresDir.get(feat_index) + "_" + 
					_featuresInt.get(feat_index) + "' { 'n', 'y' }");
		}		

		for (String demographic : demographics_types){
			_writer.println("@attribute 'demographic_" + demographic +  "' { " + NO + ", " + YES + " }");
		}

		for (Long group_id : topNGroupsSet){
			_writer.println("@attribute 'group_" + group_id +  "' { " + NO + ", " + YES + " }");
		}

		for (Long page_id : topNPagesSet){
			_writer.println("@attribute 'page_" + page_id +  "' { " + NO + ", " + YES + " }");
		}

		for (String trait : user_traits){
			_writer.println("@attribute 'trait_" + trait +  "' { " + NO + ", " + YES + " }");
		}   

		for (int i = 0; i < topNWords.size(); i++){
			_writer.println("@attribute 'conversation_outgoing_" + i + "_" + topNWords.get(i) +  "' { " + NO + ", " + YES + " }");
		}

		for (int i = 0; i < topNWords.size(); i++){
			_writer.println("@attribute 'conversation_incoming_" + i + "_" + topNWords.get(i) +  "' { " + NO + ", " + YES + " }");
		}

		_writer.println("@data");
	}

	/*
	 * Write known rating data
	 */
	public static void writeData(String filename) throws Exception {

		writeHeader(filename);

		long yes_ratings = 0;
		long no_ratings  = 0;
		long all_ratings = 0;

		for (String rating : RATINGS) {

			Map<Long,Set<Long>> uid2links =	(rating == YES ? _uid2linkids_likes : _uid2linkids_dislikes); 

			for (Entry<Long, Set<Long>> entry : uid2links.entrySet()){
				Long uid = entry.getKey();
				Set<Long> link_ids = entry.getValue();
				//System.out.println("User " + ExtractRelTables.UID_2_NAME.get(uid) + " made " + link_ids.size() + " " + rating + " ratings");
				for (Long link_id : link_ids){

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

					boolean friendLikes = false;
					if (friendships.get(uid) != null){
						for (Entry<Long, Double> friend : friendships.get(uid).entrySet()){
							if (_uid2all_passive_linkids_likes.get(friend.getKey()) != null && _uid2all_passive_linkids_likes.get(friend.getKey()).contains(link_id)){
								friendLikes = true;
								break;
							}
						}
					}	
					columns.append("," + (friendLikes == true ? YES : NO));

					// Now write columns
					for (int feat_index = 0; feat_index < _featuresInt.size(); feat_index++) {
						//System.out.println("Writing feature: " + _featuresDir.get(feat_index) + "_" + _featuresInt.get(feat_index));
						Set<Long> alter_likes = _int_dir2uid_linkid.get(_featuresInt.get(feat_index)).get(_featuresDir.get(feat_index)).get(uid);
						String feat_value = alter_likes == null ? NO : (alter_likes.contains(link_id) ? YES : NO);
						//_writer.print("," + feat_value);
						columns.append("," + feat_value);
					}
					columns.append(additionalUserColumns(link_id,uid));
					//_writer.println();

					_writer.println(columns.toString());
				}
			}
		}

		double total_ratings = yes_ratings + no_ratings;
		//System.out.println("Number of possible ratings: " + all_ratings);
		//System.out.println("Number of yes ratings: " + yes_ratings + " -- " + (100d*yes_ratings/total_ratings) + "%");
		//System.out.println("Number of no ratings:  " + no_ratings  + " -- " + (100d*no_ratings/total_ratings) + "%");

		_writer.close();
	}

	/*
	 * count of cols
	 */
	public static int getCount(String q) throws Exception{
		Statement statement = SQLUtil.getStatement();			
		ResultSet result = statement.executeQuery(q);
		int count = -1;
		while (result.next()){
			count = result.getInt(1);
		}
		result.close();

		return count;
	}
	
	/*
	 * Extract user data for users who liked a given link
	 */
	public static void extractLinkFeatures() throws Exception{
		Statement statement = SQLUtil.getStatement();

		String linksToGet = setToString(linksSeen);;
		System.out.println("Extracting links info for: (" + linksToGet + ")");				

		int limit = 0;		
		int count = getCount("select count(*) from linkrLinkLikes ll join linkrUser lu where ll.link_id in (" + linksToGet + ") and ll.id=lu.uid;");

		while (limit <= (count-1)){
			String query = "select ll.link_id, lu.uid from linkrLinkLikes ll join linkrUser lu where ll.link_id in (" + linksToGet + ") and ll.id=lu.uid limit " + limit + "," + (limit+stepSize) + ";";
			ResultSet result = statement.executeQuery(query);

			Set<Long> ls;
			while (result.next()){
				Long link_id = result.getLong(1);
				Long uid = result.getLong(2);
				usersSeen.add(uid);

				ls = additionalLinkFeatures.get(link_id);
				if (ls == null){
					System.out.println("\t New link with likes: " + link_id);
					ls = new HashSet<Long>();
				}
				ls.add(uid);
				additionalLinkFeatures.put(link_id, ls);			
			}		

			result.close();
			limit += stepSize;
		}

		statement.close();

		extractUserFeatures();

	}

	/*
	 * Extract additional user info for each app user
	 */
	public static void extractUserFeatures() throws Exception{						
		Statement statement = SQLUtil.getStatement();

		String usersToGet = setToString(usersSeen);
		System.out.println("Extracting users info for: (" + usersToGet + ")");

		int limit = 0;		
		int count = getCount("select count(*) from linkrUser where uid in ( " + usersToGet + ");");

		while (limit <= (count-1)){
			String query = "select uid, gender, right(birthday,4), locale from linkrUser where uid in ( " + usersToGet + ") limit " + limit + "," + (limit+stepSize) + ";";
			ResultSet result = statement.executeQuery(query);

			while (result.next()){		
				Long uid = result.getLong(1);
				String gender = result.getString(2);
				int birthday = result.getInt(3);
				String locale = result.getString(4);
				userGenders.put(uid,gender);
				userBirthdays.put(uid, birthday);
				userLocale.put(uid, locale);

				System.out.println("\t New user data added:" + result.getLong(1));
			}

			result.close();
			limit += stepSize;
		}
		statement.close();

		extractGroups();
		extractPages();
		extractTraits();
		extractMessagesHack();
		//extractMessages();
	}

	public static String setToString(Set<Long> set){
		StringBuilder contains = new StringBuilder();
		for (Long user : set){
			contains.append(user + ",");
		}

		return contains.toString().substring(0, contains.length()-1);
	}

	/* 
	 * Extract user gruops info
	 */
	public static void extractGroups() throws Exception {
		topNGroups();

		Statement statement = SQLUtil.getStatement();
		String usersToGet = setToString(usersSeen);

		System.out.println("\t -> Extracting groups data");

		//mysql> select count(*), id, name from linkrGroups group by id having count(*) > 10 and count(*) < 15 order by count(*) desc;		
		int limit = 0;		
		int count = getCount("select count(*) from linkrGroups where uid in (" + usersToGet + ");");

		while (limit <= (count-1)){		
			String query = "select uid, id from linkrGroups where uid in (" + usersToGet + ") limit " + limit + "," + (limit+stepSize) + ";";
			ResultSet result = statement.executeQuery(query);

			while (result.next()){
				Long uid = result.getLong(1);
				Long group = result.getLong(2);
				Set<Long> groups = (userGroupMemberships.get(uid) == null ? new HashSet<Long>() : userGroupMemberships.get(uid));
				groups.add(group);
				userGroupMemberships.put(uid, groups);
			}
			result.close();
			limit += stepSize;
		}

		statement.close();
	}

	/*
	 * Extract top user groups
	 */
	static ArrayList<Long> topNGroupsSet = new ArrayList<Long>();
	public static void topNGroups() throws Exception{

		String usersToGet = setToString(usersSeen);
		System.out.println("\t -> Extracting top groups data");

		Statement statement = SQLUtil.getStatement();
		String query = "select id from linkrGroups where uid in (" + usersToGet + ") group by id order by count(*) desc limit " + topGroupsN + ";";
		ResultSet result = statement.executeQuery(query);

		while (result.next()){
			topNGroupsSet.add(result.getLong(1));
		}
		result.close();

		statement.close();
	}

	/* 
	 * Extract user pages info
	 */
	public static void extractPages() throws Exception {
		topNPages();

		Statement statement = SQLUtil.getStatement();
		String usersToGet = setToString(usersSeen);

		System.out.println("\t -> Extracting pages data");

		//mysql> select count(*), id, name from linkrGroups group by id having count(*) > 10 and count(*) < 15 order by count(*) desc;		
		int limit = 0;		
		int count = getCount("select count(*) from linkrLikes where uid in (" + usersToGet + ");");

		while (limit <= (count-1)){		
			String query = "select uid, id from linkrLikes where uid in (" + usersToGet + ") limit " + limit + "," + (limit+stepSize) + ";";
			ResultSet result = statement.executeQuery(query);

			while (result.next()){
				Long uid = result.getLong(1);
				Long page = result.getLong(2);
				Set<Long> pages = (userPageMemberships.get(uid) == null ? new HashSet<Long>() : userPageMemberships.get(uid));
				pages.add(page);
				userPageMemberships.put(uid, pages);
			}
			result.close();
			limit += stepSize;
		}

		statement.close();
	}

	/*
	 * Extract top user groups
	 */
	static ArrayList<Long> topNPagesSet = new ArrayList<Long>();
	public static void topNPages() throws Exception{
		String usersToGet = setToString(usersSeen);

		System.out.println("\t -> Extracting top pages data");

		Statement statement = SQLUtil.getStatement();
		String query = "select id from linkrLikes where uid in (" + usersToGet + ") group by id order by count(*) desc limit " + topPagesN + ";";
		ResultSet result = statement.executeQuery(query);

		while (result.next()){
			topNPagesSet.add(result.getLong(1));
		}
		result.close();

		statement.close();
	}

	/*
	 * extract user traits
	 */
	public static void extractTraits() throws Exception{
		String usersToGet = setToString(usersSeen);
		Statement statement = SQLUtil.getStatement();

		System.out.println("\t -> Extracting user traits data");		

		// {"linkrActivities", "linkrBooks", "linkrFavoriteAthletes", "linkrFavoriteTeams", "linkrInspirationalPeople", "linkrInterests", "linkrMovies", "linkrMusic", "linkrSports", "linkrTelevision", "linkrSchoolWith", "linkrWorkWith"};
		for (String trait : user_traits){
			int limit = 0;		
			int count;
			String q;

			if (trait.equals("linkrSchoolWith")){
				q = "select uid1, school_id from " + trait + " where uid1 in (" + usersToGet + ")";
				count = getCount("select count(*) from " + trait + " where uid1 in (" + usersToGet + ");");
			} else if (trait.equals("linkrWorkWith")){
				q = "select uid1, employer_id from " + trait + " where uid1 in (" + usersToGet + ")";
				count = getCount("select count(*) from " + trait + " where uid1 in (" + usersToGet + ");");
			} else {
				q = "select uid, id from " + trait + " where uid in (" + usersToGet + ")";
				count = getCount("select count(*) from " + trait + " where uid in (" + usersToGet + ");");
			}

			while (limit <= (count-1)){
				ResultSet result = statement.executeQuery(q + " limit " + limit + "," + (limit+stepSize) + ";");

				while (result.next()){
					Long uid = result.getLong(1);
					Long traitId = result.getLong(2);

					HashMap<Long, Set<Long>> AllTraits = (userTraits.get(trait) == null ? new HashMap<Long, Set<Long>>() : userTraits.get(trait));
					Set<Long> traitsSet =  (AllTraits.get(uid) == null ? new HashSet<Long>() : AllTraits.get(uid));
					traitsSet.add(traitId);
					AllTraits.put(uid,traitsSet);
					userTraits.put(trait,AllTraits);
				}

				result.close();
				limit += stepSize;				
			}
		}		

		statement.close();
	}

	/*
	 * Extract user messages
	 */

	public static void extractMessagesHack() throws Exception{
		System.out.println("\t -> Extracting messages data");
		UserInfoHack.getMessageInfo();				
	}

	/*
	 * build additional columns
	 */

	public static String additionalUserColumns(long link_id, long uid) throws Exception{
		StringBuilder results = new StringBuilder();

		// flags 
		boolean sameGender = false, sameBirthday = false, sameLocale = false, 
				sameActivities = false, sameBooks = false, sameFavoriteAthletes = false, 
				sameFavoriteTeams = false, sameInspirationalPeople = false, sameInterests = false, sameMovies = false, 
				sameMusic = false, sameSports = false, sameTelevision = false, sameSchool = false, sameWork = false;

		boolean[] sameGroups = new boolean[topGroupsN];
		boolean[] samePages = new boolean[topPagesN];

		boolean[] wordsOutgoing = new boolean[topWordsN];
		boolean[] wordsIncoming = new boolean[topWordsN];

		// likee info
		if (additionalLinkFeatures.get(link_id) != null){
			for (long likeeId : additionalLinkFeatures.get(link_id)){
				// skip self
				if (likeeId == uid){
					continue;
				}		

				// demographics
				if (!sameGender)
					sameGender = (userGenders.get(uid).equals(userGenders.get(likeeId))) ? true : false;

				if (!sameBirthday){
					int rounded = (userBirthdays.get(uid) + 4) / 5 * 5;
					if (userBirthdays.get(likeeId) >= (rounded-4) && userBirthdays.get(likeeId) <= rounded){
						sameBirthday = true;				
					}
					//System.out.println("\t" + birthday + ":" + (birthday >= (rounded-4) && birthday <= rounded));				
				}				

				if (!sameLocale)
					sameLocale = (userLocale.get(uid).equals(userLocale.get(likeeId))) ? true : false;

				//traits
				if (!sameActivities)
					sameActivities = sameTraits("linkrActivities",uid,likeeId);

				if (!sameBooks)
					sameBooks = sameTraits("linkrBooks",uid,likeeId);

				if (!sameFavoriteAthletes)
					sameFavoriteAthletes = sameTraits("linkrFavoriteAthletes",uid,likeeId);

				if (!sameFavoriteTeams)
					sameFavoriteTeams = sameTraits("linkrFavoriteTeams",uid,likeeId);

				if (!sameInspirationalPeople)
					sameInspirationalPeople = sameTraits("linkrInspirationalPeople",uid,likeeId);

				if (!sameInterests)
					sameInterests = sameTraits("linkrInterests",uid,likeeId);

				if (!sameMovies)
					sameMovies = sameTraits("linkrMovies",uid,likeeId);

				if (!sameMusic)
					sameMusic = sameTraits("linkrMusic",uid,likeeId);

				if (!sameSports)
					sameSports = sameTraits("linkrSports",uid,likeeId);

				if (!sameTelevision)
					sameTelevision = sameTraits("linkrTelevision",uid,likeeId);

				if (!sameSchool)
					sameSchool = sameTraits("linkrSchoolWith",uid,likeeId);

				if (!sameWork)
					sameWork = sameTraits("linkrWorkWith",uid,likeeId);

				//groups
				for (int i = 0; i < sameGroups.length; i++){
					if (!sameGroups[i] && userGroupMemberships.get(uid) != null && userGroupMemberships.get(likeeId) != null){
						sameGroups[i] = userGroupMemberships.get(uid).contains(topNGroupsSet.get(i)) && userGroupMemberships.get(likeeId).contains(topNGroupsSet.get(i));
					}
				}

				//pages
				for (int i = 0; i < samePages.length; i++){
					if (!samePages[i] && userPageMemberships.get(uid) != null && userPageMemberships.get(likeeId) != null){
						samePages[i] = userPageMemberships.get(uid).contains(topNPagesSet.get(i)) && userPageMemberships.get(likeeId).contains(topNPagesSet.get(i));
					}
				}

				// messages
				for (int i = 0; i < wordsOutgoing.length; i++){
					if (!wordsOutgoing[i] && UserInfoHack.getSeenOutgoing(uid, likeeId) != null)
						wordsOutgoing[i] = UserInfoHack.getSeenOutgoing(uid, likeeId)[i];
				}

				for (int i = 0; i < wordsIncoming.length; i++){
					if (!wordsIncoming[i] && UserInfoHack.getSeenIncoming(uid, likeeId) != null)
						wordsIncoming[i] = UserInfoHack.getSeenIncoming(uid, likeeId)[i];
				}
			}
		}

		//demographics
		results.append(PRE + ((userGenders.get(uid).equals("male")) ? YES : NO));		// user is male
		results.append(PRE + ((userGenders.get(uid).equals("female")) ? YES : NO));		// user is female
		results.append(PRE + (sameGender ? YES : NO)); 									// same gendered user has(nt) liked link		
		results.append(PRE + (sameBirthday ? YES : NO));								// same birthday range user has(nt) liked link
		results.append(PRE + (sameLocale ? YES : NO));									// same localed user has(nt) liked link

		// groups
		for (int i = 0; i < sameGroups.length; i++){										
			results.append(PRE + (sameGroups[i] ? YES : NO));
		}

		// pages
		for (int i = 0; i < samePages.length; i++){										
			results.append(PRE + (samePages[i] ? YES : NO));
		}

		//traits
		results.append(PRE + (sameActivities ? YES : NO));
		results.append(PRE + (sameBooks ? YES : NO));
		results.append(PRE + (sameFavoriteAthletes ? YES : NO));
		results.append(PRE + (sameFavoriteTeams ? YES : NO));
		results.append(PRE + (sameInspirationalPeople ? YES : NO));
		results.append(PRE + (sameInterests ? YES : NO));
		results.append(PRE + (sameMovies ? YES : NO));
		results.append(PRE + (sameMusic ? YES : NO));
		results.append(PRE + (sameSports ? YES : NO));
		results.append(PRE + (sameTelevision ? YES : NO));
		results.append(PRE + (sameSchool ? YES : NO));
		results.append(PRE + (sameWork ? YES : NO));

		//conversation
		for (int i = 0; i < topWordsN; i++){
			results.append(PRE + (wordsOutgoing[i] ? YES : NO));
		}

		for (int i = 0; i < topWordsN; i++){
			results.append(PRE + (wordsIncoming[i] ? YES : NO));
		}

		return results.toString();
	}

	/*
	 * return whether two hashsets share the same value
	 */
	public static boolean sameTraits(String trait, Long uid, Long likeeId){
		Map<Long,Set<Long>> traitsSet = userTraits.get(trait);
		if (traitsSet == null)
			return false;
		
		Set<Long> user = traitsSet.get(uid);
		Set<Long> likee = traitsSet.get(likeeId);
		if (user == null || likee == null)
			return false;
		
		for (Long userTrait : user){
			if (likee.contains(userTrait))
				return true;
		}
		return false;
	}

}
