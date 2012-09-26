package project.riley.datageneration;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
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
	public static void getGroupsInfo() throws Exception{			

		int minSize = 1;
		int maxSize = 10;

		//String query = "select count(*), id, name from linkrGroups group by id having count(*) > 10 and count(*) < 15 order by count(*) desc;";
		String query = "select count(*),id,name from linkrGroups where uid in (select distinct uid from trackRecommendedLinks) group by id having count(*) > " + minSize + " and count(*) < " + maxSize + " order by count(*) desc;";

		Statement statement = SQLUtil.getStatement();		
		ResultSet result = statement.executeQuery(query);

		System.out.println("Group ranges " + minSize + " to " + maxSize);
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

	/*
	 * Extract weights from columns
	 */
	public static void getColumnWeightsLR(LogisticRegression lr,int display) throws Exception{		
		//lr.runTests(FILE, /* file to use */ 10 /* folds to use */, threshold /* test threshold */, groupsSize /*groups size*/, pagesSize, messagesSize, new PrintWriter("a.txt") /* file to write */, DEMOGRAPHICS, GROUPS, PAGES, TRAITS, MESSAGES);

		System.out.println("Using Predictor " + lr.getName());

		Map<Integer,Double> termWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> positiveWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> negativeWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> neutralWeights = new HashMap<Integer,Double>();				

		for (int outcome = 0; outcome < lr._betas.length; outcome++) {
			//System.out.println("Classifier weights for outcome = " + outcome + " [" + lr._betas[outcome].numDimensions() + " features]");
			for (int i = 0; i < lr._betas[outcome].numDimensions(); i++) {
				double val = lr._betas[outcome].value(i);
				if (val > 0.0){
					positiveWeights.put(i, val);
				} else if (val < 0.0){
					negativeWeights.put(i, val);
				} else {
					neutralWeights.put(i, val);
				}
				termWeights.put(i, val);
				//System.out.println(i + ":" + lr._betas[outcome].value(i) + " ");				
			}
		}

		System.out.println("Top " + display + " results for all");
		sortMap(termWeights,display,lr);
		System.out.println("\nTop " + display + " results for positive");
		sortMap(positiveWeights,display,lr);
		System.out.println("\nTop " + display + " results for negative");
		sortMap(negativeWeights,display,lr);
		System.out.println("\nTop " + display + " results for neutral");
		sortMap(neutralWeights,display,lr);	
	}

	public static void getColumnWeightsNB(NaiveBayes nb, int display) throws Exception{
		//nb.runTests(FILE, /* file to use */ 10 /* folds to use */, threshold /* test threshold */, groupsSize /*groups size*/, pagesSize, messagesSize, new PrintWriter("a.txt") /* file to write */, DEMOGRAPHICS, GROUPS, PAGES, TRAITS, MESSAGES);

		System.out.println("Using Predictor " + nb.getName());

		Map<Integer,Double> termWeightsyy = new HashMap<Integer,Double>();
		Map<Integer,Double> positiveWeightsyy = new HashMap<Integer,Double>();
		Map<Integer,Double> negativeWeightsyy = new HashMap<Integer,Double>();
		Map<Integer,Double> neutralWeightsyy = new HashMap<Integer,Double>();

		Map<Integer,Double> termWeightsyn = new HashMap<Integer,Double>();
		Map<Integer,Double> positiveWeightsyn = new HashMap<Integer,Double>();
		Map<Integer,Double> negativeWeightsyn = new HashMap<Integer,Double>();
		Map<Integer,Double> neutralWeightsyn = new HashMap<Integer,Double>();

		for (int i = 0; i < nb._condProb.size(); i++) {
			ClassCondProb ccp = nb._condProb.get(i);

			ArffData.Attribute  a = nb._trainData._attr.get(ccp._attr_index);
			ArffData.Attribute ca = nb._trainData._attr.get(nb.CLASS_INDEX);

			if (ccp._attr_index != nb.CLASS_INDEX){
				/*System.out.println("P( " + a.name + " = " + a.getClassName(1) + " | " + ca.name + " = " + ca.getClassName(1) + 
						" ) = " + (Math.exp(ccp._logprob[1][1])));
				System.out.println("P( " + a.name + " = " + a.getClassName(1) + " | " + ca.name + " = " + ca.getClassName(0) + 
						" ) = " + (Math.exp(ccp._logprob[1][0])));*/
				double yy = Math.exp(ccp._logprob[1][1]);
				double yn = Math.exp(ccp._logprob[1][0]);

				if (yy > 0.5)
					positiveWeightsyy.put(i, yy);
				else if (yy < 0.5)
					negativeWeightsyy.put(i, yy);
				else
					neutralWeightsyy.put(i, yy);

				if ((yy / yn) > 0.5)
					positiveWeightsyn.put(i, (yy / yn));
				else if ((yy / yn) < 0.5)
					negativeWeightsyn.put(i, (yy / yn));
				else 
					neutralWeightsyn.put(i, (yy / yn));

				termWeightsyy.put(i,yy);
				termWeightsyn.put(i,yn);
			}

		}

		System.out.println("P(attribute = y | class = y)");
		System.out.println("Top " + display + " results for all");
		sortMap(termWeightsyy,display,nb);
		System.out.println("\nTop " + display + " results for positive");
		sortMap(positiveWeightsyy,display,nb);
		System.out.println("\nTop " + display + " results for negative");
		sortMap(negativeWeightsyy,display,nb);
		System.out.println("\nTop " + display + " results for neutral");
		sortMap(neutralWeightsyy,display,nb);	

		System.out.println("P(attribute = y | class = y) / P(attribute = y | class = n)");
		System.out.println("Top " + display + " results for all");
		sortMap(termWeightsyn,display,nb);
		System.out.println("\nTop " + display + " results for positive");
		sortMap(positiveWeightsyn,display,nb);
		System.out.println("\nTop " + display + " results for negative");
		sortMap(negativeWeightsyn,display,nb);
		System.out.println("\nTop " + display + " results for neutral");
		sortMap(neutralWeightsyn,display,nb);	
	}

	/*
	 * group info
	 */
	static String getData(String name, String attribute) throws Exception{
		String ret = "";	
		String group = attribute.split("_")[1];

		Statement statement = SQLUtil.getStatement();
		String query = "select count(*), name from " + name + " where id = " + group + ";";
		ResultSet result = statement.executeQuery(query);

		while (result.next()){
			ret = result.getString(2) + " " + result.getLong(1);
		}
		result.close();
		statement.close();

		return ret;
	}

	/*
	 * sort and display map
	 */
	static void sortMap(Map<Integer,Double> map, int display, Predictor p) throws Exception{
		SortedMap sortedData = new TreeMap(new ValueComparer(map));
		ArffData a = p._trainData;

		//System.out.println(map);
		int count = 1;
		sortedData.putAll(map);		
		for (Object key : sortedData.keySet()){
			String attribute = a._attr.get(((Integer)key+3)).toString().split(" ", 2)[0];

			int yes = 0;
			int uniqueYes = 0;
			Set<Double> users = new HashSet<Double>();
			for (DataEntry entry : a._data){
				if ((Integer)entry.getData((Integer) key+3) > 0){
					yes += (Integer)(entry.getData((Integer) key+3));
					//System.out.println((Double)entry.getData(0) + ":" + users.contains((Double)entry.getData(0)) + ":" + users.size());
					if (!users.contains((Double)entry.getData(0))){
						uniqueYes += (Integer)(entry.getData((Integer) key+3));
						users.add((Double)entry.getData(0));
					}
				}				
			}

			System.out.println(count + "\t" + map.get(key) + "\t" + attribute + "\t yes(" + yes + ") " + "\t unique(" + uniqueYes + ") " + (attribute.contains("group_") ? "\t" + getData("linkrGroups",attribute) : "") + (attribute.contains("page_") ? "\t" + getData("linkrLikes",attribute) : ""));
			if (count >= display)
				break;
			count++;
		}
		//System.out.println(sortedData);
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

	public static void main(String[] args) throws Exception {
		//NaiveBayes nb = new NaiveBayes(1.0d);
		//getColumnWeightsNB(nb, 15);

		//LogisticRegression lr = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		//getColumnWeightsLR(lr,15);
		
		getFriendsCounts("active_all_1000_3.arff");
	}

}

class ValueComparer implements Comparator {
	private Map _data = null;
	public ValueComparer (Map data){
		super();
		_data = data;
	}

	public int compare(Object o1, Object o2) {
		Double e1 = Math.abs((Double) _data.get(o1));
		Double e2 = Math.abs((Double) _data.get(o2));
		double compare = e2.compareTo(e1);
		if (compare == 0){
			Integer a = (Integer)o1;
			Integer b = (Integer)o2;
			return a.compareTo(b);
		}
		return (int) compare;
	}
}
