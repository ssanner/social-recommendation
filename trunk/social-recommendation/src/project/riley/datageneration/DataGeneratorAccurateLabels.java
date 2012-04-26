package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.nicta.lr.util.SQLUtil;

import project.riley.messagefrequency.UserUtil;

public class DataGeneratorAccurateLabels {

	static PrintWriter writer;
	static Set<Long> APP_USERS;
	static String[] directions = new String[]{"Incoming", "Outgoing"};					
	static String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
	static String[] interactionType = new String[]{"Comments", "Tags", "Likes"};

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

	public static void extractSpecificLikes() throws SQLException{
		String userQuery = "select uid, from_id, rating from trackRecommendedLinks where rating != 0;";
		Statement statement = SQLUtil.getStatement();

		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			long uid = result.getLong(1);
			long from_id = result.getLong(2);
			int rating = result.getInt(3);
			System.out.println(uid + " " + from_id + " " + rating);
		}							
	}

	public static void main(String[] args) throws FileNotFoundException, SQLException {
		APP_USERS = UserUtil.getAppUserIds();		
		writeHeader("datak1000.arff");
		extractSpecificLikes();				
		writer.close();
	}

}
