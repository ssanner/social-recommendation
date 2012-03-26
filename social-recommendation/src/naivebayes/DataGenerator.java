package naivebayes;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.util.SQLUtil;

import messagefrequency.UserUtil;

public class DataGenerator {

	static PrintWriter writer;
	static Map<Long,Set<Long>> allLikes;
	static Set<Long> unionLikes;
	public static Set<Long> APP_USERS;

	/*
	 * Extract all likes for all app users
	 */
	public static void extractData() throws SQLException{
		System.out.println("Extracting likes data for " + allLikes.size() + " app users");
		for (Long uid : allLikes.keySet()){
			for (Long likes : allLikes.get(uid)){
				writer.print(uid + " " + likes + " 1 ");
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
		Set<Long> localUnion = new HashSet<Long>(unionLikes);
		localUnion.removeAll(remove); 
		Long[] likesArray = (Long[]) localUnion.toArray(new Long[localUnion.size()]);		
		for (int i = 0; i < (remove.size() * 9); i++){ // 9 times as much false data
			writer.print(uid + " " + (Long) likesArray[r.nextInt(localUnion.size())] + " 0 ");
			buildFCols(uid, (Long) likesArray[r.nextInt(localUnion.size())]);
		}
	}

	/*
	 * build f cols for each user and like
	 */
	public static void buildFCols(Long uid, Long lid) throws SQLException{
		Statement statement = SQLUtil.getStatement();


		String[] direction = new String[]{"Incoming", "Outgoing"};
		String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
		String[] interactionType = new String[]{"Comments", "Tags", "Likes"};

		for (String interaction : interactionMedium){
			for (String type : interactionType){
				if (interaction.equals("Link") && type.equals("Tags")){
					continue;
				}
				String userQuery = "SELECT from_id FROM linkr" + interaction + type + " WHERE uid = " + uid;
				
				System.out.println(userQuery);
				
				boolean found = false;

				ResultSet result = statement.executeQuery(userQuery);
				while (result.next()) {
					if (allLikes.containsKey(result.getLong("from_id"))){
						if (allLikes.get(result.getLong("from_id")).contains(lid)){
							writer.print("1");
							found = true;
							break;
						}
					}
				}
				if (!found){
					writer.print("0");
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
			unionLikes.addAll(allLikes.get(uid)); // union all likes data
		}

	}

	public static void main(String[] args) throws FileNotFoundException, SQLException {
		APP_USERS = UserUtil.getAppUserIds();
		writer = new PrintWriter("data.txt");
		getAppUserLikes();
		extractData();
		writer.close();
	}

}
