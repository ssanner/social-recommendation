package project.riley.datageneration;

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

import org.nicta.lr.util.SQLUtil;

import project.riley.messagefrequency.UserUtil;

/*
 * v1
 * Generate data for naive bayes model
 *  * build f(i) columns for each (user, like) item pair
	 * i = {ingoing,outgoing} X {post,photo,video,link} X {comment,tag,like}
	 * alters(i) = all users who have interacted with (user) via (i)
	 * column is set to 1 if any of the alters have also liked the item associated with the user otherwise 0
 */
public class DataGenerator {

	static PrintWriter writer;
	static Map<Long,Set<Long>> allLikes;
	static Set<Long> unionLikes;
	static Set<Long> APP_USERS;
	static String[] directions = new String[]{"Incoming", "Outgoing"};					
	static String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
	static String[] interactionType = new String[]{"Comments", "Tags", "Likes"};

	/*
	 * Extract all likes for all app users
	 */
	public static void extractData() throws SQLException{
		System.out.println("Extracting likes data for " + allLikes.size() + " app users");
		for (Long uid : allLikes.keySet()){
			System.out.println("User " + uid + " made " + allLikes.get(uid).size() + " likes");
			for (Long likes : allLikes.get(uid)){
				writer.print(uid + "," + likes + ",'y'");
				buildFCols(uid, likes);
			}
			generateData(uid, allLikes.get(uid));
		}
	}

	/*
	 * Generate false like data, 9x as much as true data
	 */
	public static void generateData(Long uid, Set<Long> remove) throws SQLException{
		Random r = new Random();
		Set<Long> localUnion = new HashSet<Long>(unionLikes); 	// duplicate total likes union
		localUnion.removeAll(remove); 							// remove all liked items for user
		Long[] likesArray = (Long[]) localUnion.toArray(new Long[localUnion.size()]);		
		for (int i = 0; i < (remove.size() * 9); i++){ 			// 9 times as much false data
			writer.print(uid + "," + (Long) likesArray[r.nextInt(localUnion.size())] + ",'n'");
			buildFCols(uid, (Long) likesArray[r.nextInt(localUnion.size())]);
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
	 * Extract likes for all app users
	 */
	public static void getAppUserLikes() throws SQLException{
		allLikes = new HashMap<Long,Set<Long>>();
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

		unionLikes = new HashSet<Long>();
		for (Long uid : allLikes.keySet()){
			unionLikes.addAll(allLikes.get(uid)); // union all likes data into one big set
		}

	}

	public static void main(String[] args) throws FileNotFoundException, SQLException {
		System.out.println("Generating data..");
		APP_USERS = UserUtil.getAppUserIds();
		writer = new PrintWriter("data.arff");		
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
		getAppUserLikes();
		extractData();		
		writer.close();
	}

}
