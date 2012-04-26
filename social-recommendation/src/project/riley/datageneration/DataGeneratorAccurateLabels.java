package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.nicta.lr.util.SQLUtil;

import project.riley.messagefrequency.UserUtil;

public class DataGeneratorAccurateLabels {

	static PrintWriter writer;
	static Set<Long> APP_USERS;
	static Map<Long,Set<Long>> allLikes;
	static String[] directions = new String[]{"Incoming", "Outgoing"};					
	static String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
	static String[] interactionType = new String[]{"Comments", "Tags", "Likes"};
	static HashMap<Long, HashMap<Long, String>> userLinks;

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
	 * Extract data for known ratings
	 */
	public static void extractSpecificLikes() throws SQLException{
		userLinks = new HashMap<Long, HashMap<Long, String>>();
		String userQuery = "select uid, link_id, rating from trackRecommendedLinks where rating != 0;";
		Statement statement = SQLUtil.getStatement();

		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			long uid = result.getLong(1);
			long link_id = result.getLong(2);
			int rating = result.getInt(3);
			HashMap<Long, String> userLikes = userLinks.get(uid);
			if (userLikes == null){
				userLikes = new HashMap<Long, String>();
			}
			// 0 = not rated, 1 = liked, 2 = not liked
			userLikes.put(link_id, (rating == 1 ? "y" : "n"));
			userLinks.put(uid, userLikes);
		}							
	}

	/*
	 * Write known rating data
	 */
	public static void writeData() throws SQLException{
		System.out.println("Extracting ratings data for " + userLinks.size() + " users");
		for (Entry<Long, HashMap<Long, String>> uid : userLinks.entrySet()){
			Long user = uid.getKey();
			HashMap<Long, String> userLikes = uid.getValue();
			System.out.println("User " + user + " made " + userLikes.size() + " ratings");
			for (Entry<Long, String> likes : userLikes.entrySet()){
				Long likeId = likes.getKey();
				String rating = likes.getValue();
				writer.print(user + "," + likeId + "," + "'" + rating + "'");
				buildFCols(user, likeId);
			}
		}
	}

	/*
	 * build f(i) columns for each (user, like) item pair
	 * i = {ingoing,outgoing} X {post,photo,video,link} X {comment,tag,like}
	 * alters(i) = all users who have interacted with (user) via (i)
	 * column is set to 1 if any of the alters have also liked the item associated with the user otherwise 0
	 */
	public static void buildFCols(Long uid, Long lid) throws SQLException{
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
						if (allLikes.containsKey(result.getLong(getRow))){
							if (allLikes.get(result.getLong(getRow)).contains(lid)){	// if a user in alter set has liked the original item
								writer.print(",'y'");
								found = true;
								break;
							}
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
	 * Extract likes for all users
	 */
	public static void getUserLikes() throws SQLException{
		allLikes = new HashMap<Long,Set<Long>>();
		Statement statement = SQLUtil.getStatement();

		String[] row = new String[]{"link_id", "post_id", "photo_id", "video_id"};
		String[] table = new String[]{"linkrLinkLikes", "linkrPostLikes", "linkrPhotoLikes", "linkrVideoLikes"};

		String userQuery = "select uid from trackRecommendedLinks where rating != 0;";
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			long uid = result.getLong(1);
			for (int i = 0; i < row.length; i++){
				String userQuery2 = "SELECT " + row[i] + " FROM " + table[i] + " WHERE id = " + uid;
				ResultSet result2 = statement.executeQuery(userQuery2);
				while (result2.next()) {
					long LIKED_ID = result2.getLong(1);
					Set<Long> likedIDs = allLikes.get(uid);
					if (likedIDs == null) {
						likedIDs = new HashSet<Long>();
						allLikes.put(uid, likedIDs);
					}
					likedIDs.add(LIKED_ID);
				}			
			}
		}
	}


	public static void main(String[] args) throws FileNotFoundException, SQLException {
		APP_USERS = UserUtil.getAppUserIds();		
		writeHeader("accurateLabelsData.arff");
		getUserLikes();
		extractSpecificLikes();	
		writeData();
		writer.close();
	}

}
