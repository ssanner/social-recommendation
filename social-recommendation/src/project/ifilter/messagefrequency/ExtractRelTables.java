package project.ifilter.messagefrequency;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

import util.Statistics;

import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInterestType;
import org.nicta.lr.util.ELikeType;
import org.nicta.lr.util.Interaction;

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
//
// TODO: Fix VIRTUAL
//       Fix plot order to go in reverse?
//       Drop bidirectional?
//       LATER: Add ALL_LIKES: update this code and Matlab including offsets :(

public class ExtractRelTables {

	public static DecimalFormat _df = new DecimalFormat("0.000");
	public static DecimalFormat _df2 = new DecimalFormat("0.00000");

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

		//ShowLikes("Scott");
		//ShowInteractions("Scott");
		//ShowGroupInterests();

		//ShowCondProbs();
		//ShowGroupInterestProbs();
		//ShowDemographicProbs();

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

	private static HashMap<Long, Integer> GetLikesForUIDSet(Set<Long> others, Map<Long, Set<Long>> id2likes) {

		HashMap<Long,Integer> likes = new HashMap<Long,Integer>();

		//Set<Long> uid_likes = id2likes.get(uid);
		//if (uid_likes != null)
		//	likes.addAll(uid_likes);

		if (others != null) for (long uid2 : others) {

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

		PrintStream log = new PrintStream(new FileOutputStream("frequency.txt"));
		Set<Long> id_set = APP_USERS;

		ELikeType ltype = ELikeType.ALL;
		BufferedReader br = new BufferedReader(new FileReader(MessageStringUtil.dictionaryFile));
		String dictWord;
		String word;
		int frequency;
		String[] wordAndFrequency;	
		StringBuilder builder;

		int K = 10;
		HashMap<String, Double>[] avgProbs = new HashMap[K-1];
		HashMap<String, Double>[] avgErr = new HashMap[K-1];
		for (int i = 0; i < (K-1); i++){
			avgProbs[i] = new HashMap<String, Double>();
			avgErr[i] = new HashMap<String, Double>();
		}

		while ((dictWord = br.readLine()) != null){

			// split word and frequency value pairs
			wordAndFrequency = dictWord.split("<>");
			builder = new StringBuilder();
			for(int i = 0; i < wordAndFrequency.length-1; i++) {
				builder.append(wordAndFrequency[i]);
			}
			word = builder.toString();
			frequency = Integer.parseInt(wordAndFrequency[wordAndFrequency.length-1]);			

			// word frequency constraint
			if (frequency > 500){					

				System.out.println(word + "*************************");

				for (EDirectionType dir : EDirectionType.values()) {

					// sort..
					Interaction i = UserUtil.getUserInteractions(word, dir);

					System.out.println("=========================");					
					Map<Long,Set<Long>> id2likes = UserUtil.getLikes(ltype);

					// Number of friends who also like the same thing
					double[] prob_at_k   = new double[10];
					double[] stderr_at_k = new double[10];
					for (int k = 1; k <= K; k++) {

						ArrayList<Double> probs = new ArrayList<Double>();
						for (long uid : id_set) {
							String uid_name = UID_2_NAME.get(uid);
							//if (!uid_name.contains(restriction))
							//	continue;
							HashMap<Long,Integer> other_likes_id2count = GetLikesInteractions(uid, i, id2likes);
							//P(like | friend likes) = P(like and friend likes) / P(friend likes)
							//                       = F(like and friend likes) / F(friend likes)
							Set<Long> other_likes_ids = ThresholdAtK(other_likes_id2count, k);
							Set<Long> tmp = id2likes.get(uid);
							Set<Long> likes_intersect_other_likes_ids = tmp == null ? null : new HashSet<Long>(tmp);
							if (likes_intersect_other_likes_ids == null && other_likes_ids.size() > 0) 
								probs.add(0d); // uid didn't like anything
							else if (other_likes_ids.size() > 0) {
								likes_intersect_other_likes_ids.retainAll(other_likes_ids);
								probs.add((double)likes_intersect_other_likes_ids.size() / (double)other_likes_ids.size());
							} // else (other_likes_ids.size() == 0) -- friends didn't like anything so undefined
						}
						if (probs.size() > 10) {
							String line = "** " + ltype + " likes | " + word + " word & " + dir + " & >" + k + " likes " + ": " +
									(_df.format(Statistics.Avg(probs)) + " +/- " + _df.format(Statistics.StdError95(probs)) + " #" + probs.size() + " [ " + _df.format(Statistics.Min(probs)) + ", " + _df.format(Statistics.Max(probs)) + " ]");
							
							System.out.println(line);
							prob_at_k[k-1]   = Statistics.Avg(probs);
							stderr_at_k[k-1] = Statistics.StdError95(probs);
							avgProbs[k-1].put(word, Statistics.Avg(probs));
							avgErr[k-1].put(word, Statistics.StdError95(probs));
						} else {
							prob_at_k[k-1]   = Double.NaN;
							stderr_at_k[k-1] = Double.NaN;
						}

					}
					//for (int k = 1; k < 10; k++) {
					//	System.out.println(k + ": " + prob_at_k.get(k-1) + "   ");
					//}
					//data.put((dir.index() - 1)*110 + (ltype.index() - 1)*22 + itype.index(), prob_at_k);
					//data.put(330 + (dir.index() - 1)*110 + (ltype.index() - 1)*22 + itype.index(), stderr_at_k);
					System.out.println("=========================");
				}
			}
		}
		//}

		
		// should be a function
		System.out.println("Sorting average probability..");
		log.println("Max average probability");
		log.flush();
		for (int i = 0; i < avgProbs.length; i++){
			Map<String, Double> sortedAvg = sortMap(true, avgProbs[i]);
			int show = 0;
			for (Map.Entry<String, Double> entry : sortedAvg.entrySet()) {
				if (show > 50){
					break;
				}
				String line = entry.getKey() + ", " + entry.getValue();
				System.out.println(entry.getKey() + ", " + entry.getValue());
				log.println(line);
				log.flush();
			}
		}
		
		System.out.println("Sorting average error..");
		log.println("Min average error");
		log.flush();
		for (int i = 0; i < avgErr.length; i++){
			Map<String, Double> sortedErr = sortMap(false, avgErr[i]);
			int show = 0;
			for (Map.Entry<String, Double> entry : sortedErr.entrySet()) {
				if (show > 50){
					break;
				}
				String line = entry.getKey() + ", " + entry.getValue();
				System.out.println(entry.getKey() + ", " + entry.getValue());
				log.println(line);
				log.flush();
			}
		}

		/*		// Export data
		System.out.print("\n\nExporting data...");
		PrintStream likes_data = new PrintStream(new FileOutputStream("likes_data.txt"));
		for (int i = 1; i <= 660; i++) {
			double[] arr = data.get(i);
			for (int k = 0; k < arr.length; k++) {
				likes_data.print((k > 0 ? "\t" : "") + (Double.isNaN(arr[k]) ? "NaN" : _df2.format(arr[k])));
			}
			likes_data.println();
		}
		likes_data.close();
		System.out.println("done.");
		 */
		log.close();
	}

	/*
	 * sort hashmap
	 */
	public static Map<String, Double> sortMap(final boolean asc, final Map<String, Double> map) {
		Map<String, Double> sortedMap = new TreeMap<String, Double>(new Comparator<String>() {
			public int compare(String o1, String o2) {
				if (asc){
					return map.get(o2).compareTo(map.get(o1));
				} else {
					return map.get(o1).compareTo(map.get(o2));
				}
			}
		});
		sortedMap.putAll(map);
		return sortedMap;
	}

	// Demographic analysis: gender, age group, degree, ...
	// 
	// want to produce full tables here, so get unique names for a column
	// check cross interactions... users who have trait and friends who have trait
	//
	// for basic info on gender, age group, degree, just produce tables directly
	// I think

	// Could do history here, also demographics: location, timezone, employment type, degree specialty
	//
	// Can be more careful here: ingoing, outgoing based on originating and destination type
	/*public static void ShowGroupInterestProbs() throws Exception {

		Set<Long> id_set = APP_USERS;

		// Compute tables of data
		for (EInterestType itype : EInterestType.values()) {

			System.out.println("*************************");

			// 10 different sizes
			int[] sizes = new int[] { 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024 };
			int MAX_K = 10;

			double[][] avg_stderr = new double[sizes.length][MAX_K*2];

			for (int sz_index = 0; sz_index < sizes.length; sz_index++) {

				int ub = sizes[sz_index];

				Interaction i = UserUtil.getGroupInterAmongFriends(itype, ub);

				System.out.println("=========[" + ub + "]==========");
				Map<Long,Set<Long>> id2likes = UserUtil.getLikes(ELikeType.ALL);

				// Number of friends who also like the same thing
				for (int k = 1; k <= MAX_K; k++) {

					ArrayList<Double> probs = new ArrayList<Double>();
					for (long uid : id_set) {
						String uid_name = UID_2_NAME.get(uid);
						//if (!uid_name.contains(restriction))
						//	continue;
						HashMap<Long,Integer> other_likes_id2count = GetLikesInteractions(uid, i, id2likes);
						//P(like | friend likes) = P(like and friend likes) / P(friend likes)
						//                       = F(like and friend likes) / F(friend likes)
						Set<Long> other_likes_ids = ThresholdAtK(other_likes_id2count, k);
						Set<Long> tmp = id2likes.get(uid);
						Set<Long> likes_intersect_other_likes_ids = tmp == null ? null : new HashSet<Long>(tmp);
						if (likes_intersect_other_likes_ids == null && other_likes_ids.size() > 0) 
							probs.add(0d); // uid didn't like anything
						else if (other_likes_ids.size() > 0) {
							likes_intersect_other_likes_ids.retainAll(other_likes_ids);
							probs.add((double)likes_intersect_other_likes_ids.size() / (double)other_likes_ids.size());
						} 
					}
					if (probs.size() > 10) {
						String line = "** " + itype + " group inter & < " + ub + " size & >=" + k + " likes " + ": " +
						(_df.format(Statistics.Avg(probs)) + " +/- " + _df.format(Statistics.StdError95(probs)) + " #" + probs.size() + " [ " + _df.format(Statistics.Min(probs)) + ", " + _df.format(Statistics.Max(probs)) + " ]");
						System.out.println(line);
						avg_stderr[sz_index][k-1]         = Statistics.Avg(probs);
						avg_stderr[sz_index][k-1 + MAX_K] = Statistics.StdError95(probs);
					} else {
						avg_stderr[sz_index][k-1]         = Double.NaN;
						avg_stderr[sz_index][k-1 + MAX_K] = Double.NaN;
					}

				}
				//for (int k = 1; k < 10; k++) {
				//	System.out.println(k + ": " + prob_at_k.get(k-1) + "   ");
				//}

				// Finish for this ub

			}

			// Export all data
			PrintStream data_out = new PrintStream(new FileOutputStream(itype + "_probs.txt"));
			for (int n = 0; n < avg_stderr.length; n++) {
				for (int k = 0; k < avg_stderr[n].length; k++) {
					double val = avg_stderr[n][k];
					String sval = Double.isNaN(val) ? "NaN" : _df2.format(val);
					data_out.print((k > 0 ? "\t" : "") + sval);
					System.out.print((k > 0 ? "\t" : "") + sval);
				}
				data_out.println();
				System.out.println();
			}
			data_out.close();
			System.out.println("=========================");
		}
	}*/

	// Could do history here, also demographics: location, timezone, employment type, degree specialty
	//
	// Can be more careful here: ingoing, outgoing based on originating and destination type?
	/*public static void ShowDemographicProbs() throws Exception {

		Set<Long> id_set = APP_USERS;
		Map<Long,Set<Long>> id2likes = UserUtil.getLikes(ELikeType.ALL);

		// Compute tables of data
		for (EDemographicType dtype : EDemographicType.values()) {

			recommendation.UserUtil.DemographicData d = UserUtil.getUser2Demographic(dtype); 
			if (d == null)
				continue;

			for (int k = 1; k <= 5; k++) {

				System.out.println("*************************");
				double[][] data_avg = new double[d._ub - d._lb + 1][d._ub - d._lb + 1];
				double[][] data_stderr = new double[d._ub - d._lb + 1][d._ub - d._lb + 1];
				for (int uid_type = d._lb; uid_type <= d._ub; uid_type++) {

					Set<Long> uids_with_type = UserUtil.getIDSubsetWithDemographic(id_set, d, uid_type);
					for (int uid2_type = d._lb; uid2_type <= d._ub; uid2_type++) {

						// Average values over each uid
						ArrayList<Double> probs = new ArrayList<Double>();
						for (long uid : uids_with_type) {

							Set<Long> uid2s_with_type = UserUtil.getFriendUIDsWithDemographic(uid, d, uid2_type);
							HashMap<Long,Integer> other_likes_id2count = GetLikesForUIDSet(uid2s_with_type, id2likes);

							Set<Long> other_likes_ids = ThresholdAtK(other_likes_id2count, k);
							Set<Long> tmp = id2likes.get(uid);
							Set<Long> likes_intersect_other_likes_ids = tmp == null ? null : new HashSet<Long>(tmp);
							if (likes_intersect_other_likes_ids == null && other_likes_ids.size() > 0) 
								probs.add(0d); // uid didn't like anything
							else if (other_likes_ids.size() > 0) {
								likes_intersect_other_likes_ids.retainAll(other_likes_ids);
								probs.add((double)likes_intersect_other_likes_ids.size() / (double)other_likes_ids.size());
							} 
						}
						if (probs.size() > 10) {
							String line = "** " + dtype + " demographic type (orig:" + uid_type + ", src likes:" + uid2_type + ") & >" + k + " likes " + ": " +
							(_df.format(Statistics.Avg(probs)) + " +/- " + _df.format(Statistics.StdError95(probs)) + " #" + probs.size() + " [ " + _df.format(Statistics.Min(probs)) + ", " + _df.format(Statistics.Max(probs)) + " ]");
							System.out.println(line);
							data_avg[uid_type - d._lb][uid2_type - d._lb] = Statistics.Avg(probs);
							data_stderr[uid_type - d._lb][uid2_type - d._lb] = Statistics.StdError95(probs);
						} else {
							data_avg[uid_type - d._lb][uid2_type - d._lb] = Double.NaN;
							data_stderr[uid_type - d._lb][uid2_type - d._lb] = Double.NaN;
						}

					}
					//for (int k = 1; k < 10; k++) {
					//	System.out.println(k + ": " + prob_at_k.get(k-1) + "   ");
					//}

					// Finish for this ub

				}

				// Export all data
				PrintStream data_out1 = new PrintStream(new FileOutputStream(dtype + "_" + k + "_prob.txt"));
				//PrintStream data_out2 = new PrintStream(new FileOutputStream(dtype + "_" + k + "_std.txt"));

				// Each row is a different uid type 
				for (int n = 0; n < data_avg.length; n++) {

					// Each col is a different uid2 type (src)
					for (int n2 = 0; n2 < data_avg[n].length; n2++) {
						double val1 = data_avg[n][n2];
						String sval1= Double.isNaN(val1) ? "NaN" : _df2.format(val1);
						double val2 = data_stderr[n][n2];
						String sval2 = Double.isNaN(val2) ? "NaN" : _df2.format(val2);
						data_out1.print((n2 > 0 ? "\t" : "") + sval1 + " +/- " + sval2);
						//data_out2.print((n2 > 0 ? "\t" : "") + sval2);
						System.out.print((n2 > 0 ? "\t" : "") + sval1 + " +/- " + sval2);
					}
					data_out1.println();
					//data_out2.println();
					System.out.println();
				}
				data_out1.close();
				//data_out2.close();
				System.out.println("=========================");
			}
		}
	}
	 */
	public static void ShowGroupInterests() throws SQLException {

		for (EInterestType type : EInterestType.values()) {

			// TODO: Just these affacted by
			Map<Long,Integer> GROUPID_2_SZ    = UserUtil.getGroupID2Size(type);
			Map<Long,String> GROUPID_2_NAME   = UserUtil.getGroupNames(type);
			Map<Long,Set<Long>> UID_2_GROUPID = UserUtil.getUser2InterestGroups(type);
			Map<Long,Set<Long>> GROUPID_2_UID = UserUtil.getInterestGroup2Users(type);

			System.out.println("*************************\n" + type);
			System.out.println("*************************");
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
	}

	public static void ShowLikes(String restriction) throws SQLException {

		for (ELikeType type : ELikeType.values()) {
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
	/*
	public static void ShowInteractions(String restriction) throws SQLException {

		for (EInteractionType type : EInteractionType.values()) {
			System.out.println("=========================");
			Interaction i = UserUtil.getUserInteractions(type, EDirectionType.BIDIR);
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
	}*/
}
