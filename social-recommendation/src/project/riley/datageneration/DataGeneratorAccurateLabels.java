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
						if (userLikes(result.getLong(1),lid)){	// if a user in alter set has liked the original item
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
	public static boolean userLikes(Long uid, Long lid) throws SQLException{
		Statement statement = SQLUtil.getStatement();

		String[] row = new String[]{"link_id", "post_id", "photo_id", "video_id"};
		String[] table = new String[]{"linkrLinkLikes", "linkrPostLikes", "linkrPhotoLikes", "linkrVideoLikes"};

		for (int i = 0; i < row.length; i++){
			String userQuery = "SELECT count(*) FROM " + table[i] + " WHERE uid = " + uid + " AND " + row[i] + " = " + lid;
			System.out.println(userQuery);
			ResultSet result = statement.executeQuery(userQuery);
			System.out.println(result.getInt(1));
			if (result.getInt(1) != 0){				
				return true;
			}
		}			
		return false;
	}


	public static void main(String[] args) throws FileNotFoundException, SQLException {
		writeHeader("accurateLabelsData.arff");
		extractSpecificLikes();	
		writeData();
		writer.close();
	}

}
