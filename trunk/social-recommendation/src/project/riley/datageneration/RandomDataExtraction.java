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

	public static void runColumnWeightsTests() throws Exception{
		String[] names = {"interactions","demographics","traits","groups","pages","messages outgoing","messages incoming"};
		boolean[][] flags = {{true, false, false, false, false, false, false},
				{false, true, false, false, false, false, false},
				{false, false, true, false, false, false, false},
				{false, false, false, true, false, false, false},
				{false, false, false, false, true, false, false},
				{false, false, false, false, false, true, false},
				{false, false, false, false, false, false, true}};

		LogisticRegression[] lrs = {new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),
				new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),
				new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),
				new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),
				new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),
				new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),
				new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d)};


		for (int i = 0; i < names.length; i++){
			//System.out.println(names[i]);			
			NaiveBayes nb = new NaiveBayes(1.0d);
			LogisticRegression lr = lrs[i];

			Map<Integer,Double> nb_termWeightsyy = new HashMap<Integer,Double>();
			Map<Integer,Double> nb_positiveWeightsyy = new HashMap<Integer,Double>();
			Map<Integer,Double> nb_negativeWeightsyy = new HashMap<Integer,Double>();
			Map<Integer,Double> nb_neutralWeightsyy = new HashMap<Integer,Double>();

			Map<Integer,Double> nb_termWeightsyn = new HashMap<Integer,Double>();
			Map<Integer,Double> nb_positiveWeightsyn = new HashMap<Integer,Double>();
			Map<Integer,Double> nb_negativeWeightsyn = new HashMap<Integer,Double>();
			Map<Integer,Double> nb_neutralWeightsyn = new HashMap<Integer,Double>();

			Map<Integer,Double> lr_termWeights = new HashMap<Integer,Double>();
			Map<Integer,Double> lr_positiveWeights = new HashMap<Integer,Double>();
			Map<Integer,Double> lr_negativeWeights = new HashMap<Integer,Double>();
			Map<Integer,Double> lr_neutralWeights = new HashMap<Integer,Double>();

			ArffData nb_trainData_all = new ArffData();
			nb_trainData_all.setThreshold(0);
			nb_trainData_all.setFriendSize(0);
			nb_trainData_all.setFriends(false);
			nb_trainData_all.setInteractions(flags[i][0]);
			nb_trainData_all.setDemographics(flags[i][1]);
			nb_trainData_all.setTraits(flags[i][2]);
			nb_trainData_all.setGroups(flags[i][3], Launcher.NB_GROUPS_SIZE_OVERRIDE);
			nb_trainData_all.setPages(flags[i][4], Launcher.NB_PAGES_SIZE_OVERRIDE);
			nb_trainData_all.setOutgoingMessages(flags[i][5], Launcher.NB_OUTGOING_SIZE_OVERRIDE);
			nb_trainData_all.setIncomingMessages(flags[i][6], Launcher.NB_INCOMING_SIZE_OVERRIDE);
			nb_trainData_all.setFileName(Launcher.DATA_FILE);

			ArffData lr_trainData_all = new ArffData();
			lr_trainData_all.setThreshold(0);
			lr_trainData_all.setFriendSize(0);
			lr_trainData_all.setFriends(false);
			lr_trainData_all.setInteractions(flags[i][0]);
			lr_trainData_all.setDemographics(flags[i][1]);
			lr_trainData_all.setTraits(flags[i][2]);
			lr_trainData_all.setGroups(flags[i][3], Launcher.LR_GROUPS_SIZE_OVERRIDE);
			lr_trainData_all.setPages(flags[i][4], Launcher.LR_PAGES_SIZE_OVERRIDE);
			lr_trainData_all.setOutgoingMessages(flags[i][5], Launcher.LR_OUTGOING_SIZE_OVERRIDE);
			lr_trainData_all.setIncomingMessages(flags[i][6], Launcher.LR_INCOMING_SIZE_OVERRIDE);
			lr_trainData_all.setFileName(Launcher.DATA_FILE);

			for (int k = 0; k < Launcher.NUM_FOLDS; k++){												

				String trainName = Launcher.DATA_FILE + ".train." + (k+1);
				String testName  = Launcher.DATA_FILE + ".test."  + (k+1);

				ArffData nb_trainData = new ArffData();
				nb_trainData.setThreshold(0);
				nb_trainData.setFriendSize(0);
				nb_trainData.setFriends(false);
				nb_trainData.setInteractions(flags[i][0]);
				nb_trainData.setDemographics(flags[i][1]);
				nb_trainData.setTraits(flags[i][2]);
				nb_trainData.setGroups(flags[i][3], Launcher.NB_GROUPS_SIZE_OVERRIDE);
				nb_trainData.setPages(flags[i][4], Launcher.NB_PAGES_SIZE_OVERRIDE);
				nb_trainData.setOutgoingMessages(flags[i][5], Launcher.NB_OUTGOING_SIZE_OVERRIDE);
				nb_trainData.setIncomingMessages(flags[i][6], Launcher.NB_INCOMING_SIZE_OVERRIDE);
				nb_trainData.setFileName(trainName);

				ArffData nb_testData  = new ArffData();
				nb_testData.setFriends(false);
				nb_testData.setThreshold(0);
				nb_testData.setFriendSize(0);
				nb_testData.setInteractions(flags[i][0]);
				nb_testData.setDemographics(flags[i][1]);
				nb_testData.setTraits(flags[i][2]);
				nb_testData.setGroups(flags[i][3], Launcher.NB_GROUPS_SIZE_OVERRIDE);
				nb_testData.setPages(flags[i][4], Launcher.NB_PAGES_SIZE_OVERRIDE);
				nb_testData.setOutgoingMessages(flags[i][5], Launcher.NB_OUTGOING_SIZE_OVERRIDE);
				nb_testData.setIncomingMessages(flags[i][6], Launcher.NB_INCOMING_SIZE_OVERRIDE);
				nb_testData.setFileName(testName);

				ArffData lr_trainData = new ArffData();
				lr_trainData.setFriends(false);
				lr_trainData.setThreshold(0);
				lr_trainData.setFriendSize(0);
				lr_trainData.setInteractions(flags[i][0]);
				lr_trainData.setDemographics(flags[i][1]);
				lr_trainData.setTraits(flags[i][2]);
				lr_trainData.setGroups(flags[i][3], Launcher.LR_GROUPS_SIZE_OVERRIDE);
				lr_trainData.setPages(flags[i][4], Launcher.LR_PAGES_SIZE_OVERRIDE);
				lr_trainData.setOutgoingMessages(flags[i][5], Launcher.LR_OUTGOING_SIZE_OVERRIDE);
				lr_trainData.setIncomingMessages(flags[i][6], Launcher.LR_INCOMING_SIZE_OVERRIDE);
				lr_trainData.setFileName(trainName);

				ArffData lr_testData  = new ArffData();
				lr_testData.setFriends(false);
				lr_testData.setThreshold(0);
				lr_testData.setFriendSize(0);
				lr_testData.setInteractions(flags[i][0]);
				lr_testData.setDemographics(flags[i][1]);
				lr_testData.setTraits(flags[i][2]);
				lr_testData.setGroups(flags[i][3], Launcher.LR_GROUPS_SIZE_OVERRIDE);
				lr_testData.setPages(flags[i][4], Launcher.LR_PAGES_SIZE_OVERRIDE);
				lr_testData.setOutgoingMessages(flags[i][5], Launcher.LR_OUTGOING_SIZE_OVERRIDE);
				lr_testData.setIncomingMessages(flags[i][6], Launcher.LR_INCOMING_SIZE_OVERRIDE);
				lr_testData.setFileName(testName);

				nb._trainData = nb_trainData;
				nb._testData = nb_testData;
				nb.clear();
				nb.train();

				lr._trainData = lr_trainData;
				lr._testData = lr_testData;
				lr.clear();
				lr.train();	

				Map<Integer,Double>[] nb_results = getColumnWeightsNB(nb);
				nb_termWeightsyy = mergeMaps(nb_termWeightsyy, nb_results[0]);
				nb_positiveWeightsyy = mergeMaps(nb_positiveWeightsyy, nb_results[1]);
				nb_negativeWeightsyy = mergeMaps(nb_negativeWeightsyy, nb_results[2]);
				nb_neutralWeightsyy = mergeMaps(nb_neutralWeightsyy, nb_results[3]);

				nb_termWeightsyn = mergeMaps(nb_termWeightsyn, nb_results[4]);
				nb_positiveWeightsyn = mergeMaps(nb_positiveWeightsyn, nb_results[5]);
				nb_negativeWeightsyn = mergeMaps(nb_negativeWeightsyn, nb_results[6]);
				nb_neutralWeightsyn = mergeMaps(nb_neutralWeightsyn, nb_results[7]);

				Map<Integer,Double>[] lr_results = getColumnWeightsLR(lr);
				lr_termWeights = mergeMaps(lr_termWeights, lr_results[0]);
				lr_positiveWeights = mergeMaps(lr_positiveWeights, lr_results[1]);
				lr_negativeWeights = mergeMaps(lr_negativeWeights, lr_results[2]);
				lr_neutralWeights = mergeMaps(lr_neutralWeights, lr_results[3]);
			}									

			nb_termWeightsyy = normaliseMap(nb_termWeightsyy);
			nb_positiveWeightsyy = normaliseMap(nb_positiveWeightsyy);
			nb_negativeWeightsyy = normaliseMap(nb_negativeWeightsyy);
			nb_neutralWeightsyy = normaliseMap(nb_neutralWeightsyy);

			nb_termWeightsyn = normaliseMap(nb_termWeightsyn);
			nb_positiveWeightsyn = normaliseMap(nb_positiveWeightsyn);
			nb_negativeWeightsyn = normaliseMap(nb_negativeWeightsyn);
			nb_neutralWeightsyn = normaliseMap(nb_neutralWeightsyn);

			lr_termWeights = normaliseMap(lr_termWeights);
			lr_positiveWeights = normaliseMap(lr_positiveWeights);
			lr_negativeWeights = normaliseMap(lr_negativeWeights);
			lr_neutralWeights = normaliseMap(lr_neutralWeights);			

			System.out.println(names[i] + " naive bayes results");
			System.out.println("P(attribute = y | class = y)");
			System.out.println("Top results for all");
			sortMap(nb_termWeightsyy,nb_trainData_all);
			System.out.println("\nTop results for positive");
			sortMap(nb_positiveWeightsyy,nb_trainData_all);
			System.out.println("\nTop results for negative");
			sortMap(nb_negativeWeightsyy,nb_trainData_all);
			System.out.println("\nTop results for neutral");
			sortMap(nb_neutralWeightsyy,nb_trainData_all);
			System.out.println();

			System.out.println("P(attribute = y | class = y) / P(attribute = y | class = n)");
			System.out.println("Top results for all");
			sortMap(nb_termWeightsyn,nb_trainData_all);
			System.out.println("\nTop results for positive");
			sortMap(nb_positiveWeightsyn,nb_trainData_all);
			System.out.println("\nTop results for negative");
			sortMap(nb_negativeWeightsyn,nb_trainData_all);
			System.out.println("\nTop results for neutral");
			sortMap(nb_neutralWeightsyn,nb_trainData_all);
			System.out.println();

			System.out.println(names[i] + " logistic regression results");
			System.out.println("Top results for all");
			sortMap(lr_termWeights,lr_trainData_all);
			System.out.println("\nTop results for positive");
			sortMap(lr_positiveWeights,lr_trainData_all);
			System.out.println("\nTop results for negative");
			sortMap(lr_negativeWeights,lr_trainData_all);
			System.out.println("\nTop results for neutral");
			sortMap(lr_neutralWeights,lr_trainData_all);
			System.out.println();
		}							
	}

	public static Map<Integer,Double> mergeMaps(Map<Integer,Double> map1, Map<Integer,Double> map2){
		Map<Integer, Double> merged = new HashMap<Integer, Double>(map1); 
		for (Map.Entry<Integer, Double> entry : map2.entrySet()) {
			Double y = merged.get(entry.getKey()); 
			merged.put(entry.getKey(), entry.getValue() + (y == null ? 0 : y));
		} 
		return merged;
	}

	public static Map<Integer,Double> normaliseMap(Map<Integer,Double> map1){		
		for (Entry<Integer, Double> element : map1.entrySet()){
			map1.put(element.getKey(), (element.getValue()/Launcher.NUM_FOLDS));
		}
		return map1;
	}

	/*
	 * Extract weights from columns
	 */
	public static Map<Integer,Double>[] getColumnWeightsLR(LogisticRegression lr) throws Exception{		
		System.out.println("Using Predictor " + lr.getName());
		Map<Integer,Double>[] results = new HashMap[4];

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

		results[0] = termWeights;
		results[1] = positiveWeights;
		results[2] = negativeWeights;
		results[3] = neutralWeights;

		return results;

	}

	public static Map<Integer,Double>[] getColumnWeightsNB(NaiveBayes nb) throws Exception{

		System.out.println("Using Predictor " + nb.getName());
		Map<Integer,Double>[] results = new HashMap[8];

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

		results[0] = termWeightsyy;
		results[1] = positiveWeightsyy;
		results[2] = negativeWeightsyy;
		results[3] = neutralWeightsyy;

		results[4] = termWeightsyn;
		results[5] = positiveWeightsyn;
		results[6] = negativeWeightsyn;
		results[7] = neutralWeightsyn;

		return results;				
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
	static void sortMap(Map<Integer,Double> map, ArffData p) throws Exception{
		int display = 10;
		SortedMap sortedData = new TreeMap(new ValueComparer(map));

		//System.out.println(map);
		int count = 1;
		int offset = 2;
		sortedData.putAll(map);		
		for (Object key : sortedData.keySet()){
			//System.out.println(key);
			String attribute = p._attr.get(((Integer)key+offset)).toString().split(" ", 2)[0];

			int yes = 0;
			int uniqueYes = 0;
			Set<Double> users = new HashSet<Double>();
			for (DataEntry entry : p._data){
				if ((Integer)entry.getData((Integer) key+offset) > 0){
					yes++;
					//System.out.println((Double)entry.getData(0) + ":" + users.contains((Double)entry.getData(0)) + ":" + users.size());
					if (!users.contains((Double)entry.getData(0))){
						uniqueYes += (Integer)(entry.getData((Integer) key+offset));
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
		//NaiveBayes nb = new NaiveBayes(1.0d);
		//getColumnWeightsNB(nb, 15);

		//LogisticRegression lr = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		//getColumnWeightsLR(lr,15);

		//getFriendsCounts("active_all_1000_3.arff");
		//incomingOutgoingAnalysis("active_all_1000_3.arff");
		
		runColumnWeightsTests();	
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
