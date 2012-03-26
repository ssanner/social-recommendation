package naivebayes;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.util.ELikeType;

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
			for (Long likes : allLikes.get(uid)){
				writer.println(uid + " " + likes + " 1");
			}
			generateData(uid, allLikes.get(uid));
		}
	}

	/*
	 * Generate false like data, 9x as much as true data
	 */
	public static void generateData(Long pid, Set<Long> remove){
		Random r = new Random();
        Set<Long> localUnion = new HashSet<Long>(unionLikes);
		localUnion.removeAll(remove);
		Long[] likesArray = (Long[]) localUnion.toArray();
		for (int i = 0; i < (remove.size() * 9); i++){ // 9 times as much false data
			writer.println(pid + " " + likesArray[r.nextInt(localUnion.size())] + " 0");
		}
	}
	
	public static void buildFCols(){
		
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
