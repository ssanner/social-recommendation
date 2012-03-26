package naivebayes;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.util.ELikeType;
import org.nicta.lr.util.SQLUtil;

import messagefrequency.UserUtil;

public class DataGenerator {

	static PrintWriter writer;
	static Map<Long,Set<Long>> allLikes;
	static Set<Long> unionLikes;

	/*
	 * Extract all likes for all app users
	 */
	public static void extractData() throws SQLException{
		for (Long uid : allLikes.keySet()){
			System.out.println("Extracting user data for " + uid + ":" + allLikes.get(uid).size());
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
		System.out.println("Generating false data for " + uid + ":" + (remove.size()*9));
		Random r = new Random();
        Set<Long> localUnion = new HashSet<Long>(unionLikes);
		localUnion.removeAll(remove); 
		Long[] likesArray = (Long[]) localUnion.toArray(new Long[localUnion.size()]);		
		for (int i = 0; i < (remove.size() * 9); i++){ // 9 times as much false data
			writer.print(uid + " " + (Long) likesArray[r.nextInt(localUnion.size())] + " 0 ");
			buildFCols(uid, (Long) likesArray[r.nextInt(localUnion.size())]);
		}
	}
	
	public static void buildFCols(Long uid, Long lid) throws SQLException{
		Statement statement = SQLUtil.getStatement();

		String userQuery = "SELECT from_id FROM linkrPhotoComments WHERE uid = " + uid;
		boolean found = false;

		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			if (allLikes.get(result.getLong("from_id")).contains(lid)){
				writer.print("1");
				found = true;
				break;
			}
		}
		if (!found){
			writer.print("0");
		}
		statement.close();
		writer.println();
	}

	public static void main(String[] args) throws FileNotFoundException, SQLException {
		writer = new PrintWriter("data.txt");
		allLikes = UserUtil.getLikes(ELikeType.ALL);
		unionLikes = new HashSet<Long>();
		for (Long uid : allLikes.keySet()){
			unionLikes.addAll(allLikes.get(uid)); // union all likes data
		}
		extractData();
		writer.close();
	}

}
