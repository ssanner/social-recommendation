package project.riley.datageneration;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.nicta.lr.util.SQLUtil;

import project.riley.predictor.ArffData;
import project.riley.predictor.NaiveBayes;
import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.LogisticRegression;
import project.riley.predictor.Predictor;

import com.aliasi.matrix.Vector;

public class RandomDataExtraction {

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
		lr.runTests("active_all_1000.arff", /* file to use */ 10 /* folds to use */, 0 /* test threshold */, 800 /*groups size*/, 0, 0, new PrintWriter("a.txt") /* file to write */, true, true, true, true, true);

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
		sortMap(termWeights,display);
		System.out.println("\nTop " + display + " results for positive");
		sortMap(positiveWeights,display);
		System.out.println("\nTop " + display + " results for negative");
		sortMap(negativeWeights,display);
		System.out.println("\nTop " + display + " results for neutral");
		sortMap(neutralWeights,display);	
	}

	public static void getColumnWeightsNB(NaiveBayes nb, int display) throws Exception{
		nb.runTests("active_all_1000.arff", /* file to use */ 10 /* folds to use */, 0 /* test threshold */, 800 /*groups size*/, 0, 0, new PrintWriter("a.txt") /* file to write */, true, true, true, true, true);
		
		System.out.println("Using Predictor " + nb.getName());

		Map<Integer,Double> termWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> positiveWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> negativeWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> neutralWeights = new HashMap<Integer,Double>();

		System.out.println(nb);
		
		System.out.println("Top " + display + " results for all");
		sortMap(termWeights,display);
		System.out.println("\nTop " + display + " results for positive");
		sortMap(positiveWeights,display);
		System.out.println("\nTop " + display + " results for negative");
		sortMap(negativeWeights,display);
		System.out.println("\nTop " + display + " results for neutral");
		sortMap(neutralWeights,display);	

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
	static void sortMap(Map<Integer,Double> map, int display) throws Exception{
		SortedMap sortedData = new TreeMap(new ValueComparer(map));
		ArffData a = new ArffData("active_all_1000.arff",0, 800, 0, 0, true, true, true, true, true);

		//System.out.println(map);
		int count = 1;
		sortedData.putAll(map);		
		for (Object key : sortedData.keySet()){
			String attribute = a._attr.get(((Integer)key+3)).toString().split(" ", 2)[0];

			int yes = 0;
			int uniqueYes = 0;
			HashSet<Double> users = new HashSet<Double>();
			for (DataEntry entry : a._data){
				yes += (Integer)(entry.getData((Integer) key+3));				
				if (!users.contains(entry.getData(0))){
					uniqueYes += (Integer)(entry.getData((Integer) key+3));
					users.add((Double) entry.getData(0));
				}
			}

			System.out.println(count + "\t" + map.get(key) + "\t" + attribute + "\t yes(" + yes + ") " + "\t unique(" + uniqueYes + ") " + (attribute.contains("group_") ? "\t" + getData("linkrGroups",attribute) : "") + (attribute.contains("page_") ? "\t" + getData("linkrLikes",attribute) : ""));
			if (count >= display)
				break;
			count++;
		}
		//System.out.println(sortedData);
	}

	public static void main(String[] args) throws Exception {
		NaiveBayes nb = new NaiveBayes(1.0d);
		getColumnWeightsNB(nb, 15);
		
		LogisticRegression lr = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		//getColumnWeightsLR(lr,15);
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
