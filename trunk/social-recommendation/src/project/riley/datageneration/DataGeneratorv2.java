package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.nicta.lr.util.SQLUtil;

import project.riley.messagefrequency.UserUtil;

/*
 * v2
 * Generate data for naive bayes model
 * find top k most liked items
 */
public class DataGeneratorv2 {

	static PrintWriter writer;
	static Set<Long> APP_USERS;
	static String[] directions = new String[]{"Incoming", "Outgoing"};					
	static String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
	static String[] interactionType = new String[]{"Comments", "Tags", "Likes"};
	static String[] likesRow = new String[]{"post_id", "photo_id", "video_id", "link_id"};
	static String[] likesTable = new String[]{"linkrPostLikes", "linkrPhotoLikes", "linkrVideoLikes", "linkrLinkLikes"};

	/*
	 * Write arff header data
	 */
	public static void writeHeader(String fileName) throws FileNotFoundException{
		writer = new PrintWriter(fileName);		
		writer.println("@relation app-data");
		writer.println("@attribute 'Uid' numeric");
		writer.println("@attribute 'Item' numeric");
		writer.println("@attribute 'Class' { 'n' , 'y' }");
		for (String direction : directions){
			for (String interaction : interactionMedium){
				for (int i = 0; i < interactionType.length; i++){
					if (interaction.equals("Link") && interactionType[i].equals("Tags")){
						continue; // no link tags data
					}
					writer.println("@attribute '" + direction + "-" + interaction + "-" + interactionType[i] + "' { 'n', 'y' }");
				}
			}
		}
		writer.println("@data");
	}

	/*
	 * Extract likes for all app users
	 */
	public static Map<Long,Set<Long>> getAppUserLikes() throws SQLException{
		Map<Long,Set<Long>> allLikes = new HashMap<Long,Set<Long>>();
		Statement statement = SQLUtil.getStatement();

		String[] row = new String[]{"link_id", "post_id", "photo_id", "video_id"};
		String[] table = new String[]{"linkrLinkLikes", "linkrPostLikes", "linkrPhotoLikes", "linkrVideoLikes"};

		for (Long uid : APP_USERS){
			for (int i = 0; i < row.length; i++){
				String userQuery = "SELECT " + row[i] + " FROM " + table[i] + " WHERE id = " + uid;
				ResultSet result = statement.executeQuery(userQuery);
				while (result.next()) {
					long LIKED_ID = result.getLong(1);
					Set<Long> likedIDs = allLikes.get(uid);
					if (likedIDs == null) {
						likedIDs = new HashSet<Long>();
						allLikes.put(uid, likedIDs);
					}
					likedIDs.add(LIKED_ID);
				}			
			}
		}
		statement.close();		
		return allLikes;
	}

	/*
	 * Each liked items count of likes from app user base
	 */
	public static TreeMap<Long, Integer> topLiked(Map<Long,Set<Long>> allLikes){		
		final HashMap<Long, Integer> topLiked = new HashMap<Long,Integer>();
		for (Long uid : allLikes.keySet()){
			for (Long likes : allLikes.get(uid)){
				Integer totalLiked = topLiked.get(likes);
				if (totalLiked == null){
					topLiked.put(likes, 1);
				} else {
					int update = totalLiked + 1;
					topLiked.put(likes, update);
				}
			}
		}

		Comparator<Long> vc = new Comparator<Long>(){
			@Override
			public int compare(Long a, Long b) {
				int compare = topLiked.get(b) - topLiked.get(a);
				if (compare == 0) return a.compareTo(b);
				else return compare;
			}						
		};

		TreeMap<Long, Integer> sortedLikes = new TreeMap(vc);
		sortedLikes.putAll(topLiked);

		/*for (Long key : sortedLikes.keySet()){
			System.out.println(key + ":" + topLiked.get(key));
		}		*/
		return sortedLikes;	
	}

	/*
	 * Write data to file, yes if user liked top item, no otherwise
	 */
	public static void writeData(int k, Map<Long,Set<Long>> allLikes, TreeMap<Long,Integer> topLikes) throws SQLException{
		for (Long like : topLikes.keySet()){
			//System.out.println(key + " " + topLikes.get(key));
			for (Long uid : allLikes.keySet()){
				char data = 'n';
				if (allLikes.get(uid).contains(like)){
					data = 'y';
				}
				writer.print(uid + "," + like + ",'" + data + "'");				
				buildFCols(uid, like, allLikes);
			}
			k--;
			if (k <= 0) break;
		}
	}

	/*
	 * build f(i) columns for each (user, like) item pair
	 * i = {ingoing,outgoing} X {post,photo,video,link} X {comment,tag,like}
	 * alters(i) = all users who have interacted with (user) via (i)
	 * column is set to 1 if any of the alters have also liked the item associated with the user otherwise 0
	 */
	public static void buildFCols(Long uid, Long lid, Map<Long,Set<Long>> allLikes) throws SQLException{
		Statement statement = SQLUtil.getStatement();

		String[] row = new String[]{"from_id", "uid1", "id"};			// tables have different names for in/out cols
		String[] where = new String[]{"uid", "uid2", "uid"};
		String getRow;
		String getWhere;

		for (String direction : directions){
			for (String interaction : interactionMedium){
				for (int i = 0; i < interactionType.length; i++){
					if (interaction.equals("Link") && interactionType[i].equals("Tags")){
						continue; // no link tags data
					} 

					if (direction.equals("Outgoing")){		// outgoing order
						getRow = row[i];
						getWhere = where[i];
					} else {								// incoming order
						getRow = where[i];
						getWhere = row[i];
					}								

					// select incoming/outgoing data for different interaction types
					String userQuery = "SELECT " + getRow + " FROM linkr" + interaction + interactionType[i] + " WHERE " + getWhere + " = " + uid;
					boolean found = false;

					ResultSet result = statement.executeQuery(userQuery);
					while (result.next()) {
						if (userLikes(result.getLong(1),lid,likesRow[i],likesTable[i])){	// if a user in alter set has liked the original item
							writer.print(",'y'");
							found = true;
							break;
						}
					}
					if (!found){														// if no user has liked the original item
						writer.print(",'n'");
					}

				}
			}
		}
		statement.close();
		writer.println();
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
	
	public static void main(String[] args) throws FileNotFoundException, SQLException {
		int k = 1000;

		APP_USERS = UserUtil.getAppUserIds();		
		writeHeader("datak1000.arff");

		Map<Long,Set<Long>> allLikes = getAppUserLikes();
		System.out.println("Extracting likes data for " + allLikes.size() + " app users");
		
		TreeMap<Long,Integer> topLikes = topLiked(allLikes);
		System.out.println(topLikes.size() + " unique likes found for app users");
		
		System.out.println("Writing data for top " + k + " likes");
		writeData(k, allLikes, topLikes);
		
		writer.close();
	}

}
