package project.riley.datageneration;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.nicta.lr.util.SQLUtil;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.Attribute;
import project.riley.predictor.Launcher;
import project.riley.predictor.NaiveBayes;
import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.NaiveBayes.ClassCondProb;
import project.riley.predictor.LogisticRegression;
import project.riley.predictor.Predictor;

public class RandomDataExtraction {

	static String FILE = "active_all_1000.arff";

	static boolean DEMOGRAPHICS = false;
	static boolean GROUPS = false;
	static boolean PAGES = false;
	static boolean TRAITS = false;
	static boolean MESSAGES = false;

	static int threshold = 0;
	static int groupsSize = 5;
	static int pagesSize = 5;
	static int messagesSize = 5;

	/*
	 * Extract birthdays in 5 year sets
	 */
	public static void getDemographicsInfo() throws Exception{
		String query = "select count(*), right(lu.birthday,4) from linkrUser lu where lu.uid in (SELECT distinct uid FROM trackRecommendedLinks) group by right(lu.birthday,4);";
		HashMap<Integer,Integer> bdayRanges = new HashMap<Integer,Integer>();

		Statement statement = SQLUtil.getStatement();		
		ResultSet result = statement.executeQuery(query);

		while (result.next()) {			
			int count = result.getInt(1);
			int year = result.getInt(2);
			int rounded = (year + 4) / 5 * 5;			

			//System.out.println(year + "," + rounded + ":" + count);

			if (bdayRanges.get(rounded) != null){
				bdayRanges.put(rounded, bdayRanges.get(rounded) + count);
			} else {
				bdayRanges.put(rounded, count);
			}	

		}

		for (Entry<Integer, Integer> bday : bdayRanges.entrySet()){
			int range = bday.getKey();
			int count = bday.getValue();

			System.out.println((range-4) + "-" + range + ":" + count);
		}		
	}	

	/*
	 * Extract gruops info
	 */
	public static void getGroupsInfo(String table) throws Exception{			

		//String query = "select count(*), id, name from linkrGroups group by id having count(*) > 10 and count(*) < 15 order by count(*) desc;";
		String query = "select count(*),id,name from " + table + " where uid in (select distinct uid from trackRecommendedLinks) group by id order by count(*) desc limit 25;";

		Statement statement = SQLUtil.getStatement();		
		ResultSet result = statement.executeQuery(query);

		while (result.next()) {			
			int count = result.getInt(1);
			String name = result.getString(3);			

			System.out.println(count + " - " + name);		

		}
	}

	/*
	 * extract traits info
	 */

	public static void getTraitsInfo() throws Exception{
		//select count(id),name from linkrActivities where uid in (select distinct uid from trackRecommendedLinks) group by id order by count(*) desc limit 10;
		int limit = 15;

		Statement statement = SQLUtil.getStatement();				

		String q = null;
		for (String trait : DataGeneratorPassiveActive.user_traits){
			if (trait.equals("linkrSchoolWith")){
				continue;
				//q = "select count(school_id),name from " + trait + " where uid1 in (select distinct uid from trackRecommendedLinks) or uid2 in (select distinct uid from trackRecommendedLinks) group by id order by count(*) desc limit " + limit + ";";
			} else if (trait.equals("linkrWorkWith")){				
				continue;
				//q = "select count(employer_id),name from " + trait + " where uid1 in (select distinct uid from trackRecommendedLinks) or uid2 in (select distinct uid from trackRecommendedLinks) group by id order by count(*) desc limit " + limit + ";";
			} else {
				q = "select count(id),name from " + trait + " where uid in (select distinct uid from trackRecommendedLinks) group by id order by count(*) desc limit " + limit + ";";
			}
			System.out.println(trait);
			ResultSet result = statement.executeQuery(q);
			while (result.next()) {			
				int count = result.getInt(1);
				String name = result.getString(2);			
				System.out.println(count + " - " + name);		
			}
			System.out.println();
		}
	}

	public static void getFriendsCounts(String name){				

		for (int i = 0; i <= 10; i++){			
			ArffData d = new ArffData();
			d.setFriends(true);
			d.setInteractions(false);
			d.setDemographics(false);
			d.setGroups(false,0);
			d.setPages(false,0);
			d.setTraits(false);
			d.setOutgoingMessages(false,0);
			d.setIncomingMessages(false,0);

			d.setFriendSize(i);
			d.setFileName(name);
			System.out.println("Friend threshold: " + i + "\t Size: " + d._data.size());
		}		

	}

	public static void incomingOutgoingAnalysis(String name){				

		ArffData in = new ArffData();
		in.setFriends(false);
		in.setInteractions(false);
		in.setDemographics(false);
		in.setGroups(false,0);
		in.setPages(false,0);
		in.setTraits(false);
		in.setOutgoingMessages(true,1000);
		in.setIncomingMessages(false,0);		
		in.setFileName(name);
		
		ArffData out = new ArffData();
		out.setFriends(false);
		out.setInteractions(false);
		out.setDemographics(false);
		out.setGroups(false,0);
		out.setPages(false,0);
		out.setTraits(false);
		out.setOutgoingMessages(false,1000);
		out.setIncomingMessages(true,1000);		
		out.setFileName(name);
		
		System.out.println("Outgoing size: " + out._attr.size());
		int count[] = new int[out._attr.size()*2];
		
		for (int i = 0; i < out._data.size(); i++){
			DataEntry de = out._data.get(i);
			for (int j = 3; j < de._entries.size(); j++){
				count[j-3] += (Integer) de.getData(j);
				//System.out.println(i + " " + out._attr.get(j) + " " + de.getData(j));
			}
		}
		
		for (int i = 0; i < in._data.size(); i++){
			DataEntry de = in._data.get(i);
			for (int j = 3; j < de._entries.size(); j++){
				count[1000+j-3] += (Integer) de.getData(j);
				//System.out.println(i + " " + in._attr.get(j) + " " + de.getData(j));
			}
		}
		
		for (int i = 3; i < out._attr.size(); i++){
			System.out.println(out._attr.get(i) + " " + count[i-3] + "\t" + in._attr.get(i) + " " + count[1000+i-3]);
		}

	}

	public static void main(String[] args) throws Exception {
		//incomingOutgoingAnalysis("active_all_1000_3.arff");
		getGroupsInfo("linkrGroups");
		System.out.println("------------------");
		getGroupsInfo("linkrLikes");
	}

}
