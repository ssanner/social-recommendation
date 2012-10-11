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

import org.nicta.lr.util.SQLUtil;

import project.riley.predictor.ArffData;
import project.riley.predictor.Launcher;
import project.riley.predictor.LogisticRegression;
import project.riley.predictor.NaiveBayes;
import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.NaiveBayes.ClassCondProb;

public class WeightsExtractor {

	static PrintWriter writer;
	static String[] names = {"interactions","demographics","traits","groups","pages","messages outgoing","messages incoming"};
	static LogisticRegression[] lrs = 
		{new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d, /*maxent*/ true),						// inter
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),										// demo
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d, /*maxent*/ true),						// traits
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d, true),									// groups
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),										// pages
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d, true),									// outgoing
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d, true)};								// incoming

	/*
	 * set up arff file based on flag
	 */
	public static ArffData getArff(int flag, int type, String name){
		// 0 = none
		// 1 = interactions
		// 2 = demographics
		// 3 = traits
		// 4 = groups
		// 5 = pages
		// 6 = outgoing
		// 7 = incoming
		// 8 = all
		int groupsize;
		int pagesize;
		int outgoingsize;
		int incomingsize;
		if (type == 1){
			groupsize = Launcher.NB_GROUPS_SIZE_OVERRIDE;
			pagesize = Launcher.NB_PAGES_SIZE_OVERRIDE;
			outgoingsize = Launcher.NB_OUTGOING_SIZE_OVERRIDE;
			incomingsize = Launcher.NB_INCOMING_SIZE_OVERRIDE;
		} else {
			groupsize = Launcher.LR_GROUPS_SIZE_OVERRIDE;
			pagesize = Launcher.LR_PAGES_SIZE_OVERRIDE;
			outgoingsize = Launcher.LR_OUTGOING_SIZE_OVERRIDE;
			incomingsize = Launcher.LR_INCOMING_SIZE_OVERRIDE;
		}
		ArffData data = new ArffData();
		data.setThreshold(0);
		data.setFriendSize(0);
		data.setFriends(false);
		data.setInteractions(((flag == 1 || flag == 8) ? true : false));
		data.setDemographics(((flag == 2 || flag == 8) ? true : false));
		data.setTraits(((flag == 3 || flag == 8) ? true : false));
		data.setGroups(((flag == 4 || flag == 8) ? true : false), groupsize);
		data.setPages(((flag == 5 || flag == 8) ? true : false), pagesize);
		data.setOutgoingMessages(((flag == 6 || flag == 8) ? true : false), outgoingsize);
		data.setIncomingMessages(((flag == 7 || flag == 8) ? true : false), incomingsize);
		data.setFileName(name);

		return data;
	}	

	static int offset;
	public static void runColumnWeightsTests() throws Exception{
		writer = new PrintWriter("weights_results.txt");
		
		for (int i = 0; i < names.length; i++){			
			
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

			ArffData nb_trainData = null;
			ArffData nb_testData = null;
			
			ArffData lr_trainData = null;
			ArffData lr_testData = null;	

			for (int k = 0; k < Launcher.NUM_FOLDS; k++){												

				String trainName = Launcher.DATA_FILE + ".train." + (k+1);
				String testName  = Launcher.DATA_FILE + ".test."  + (k+1);

				nb_trainData = getArff(i+1, 1, trainName);
				nb_testData  = getArff(i+1, 1, testName); 

				lr_trainData = getArff(i+1, 0, trainName);
				lr_testData  = getArff(i+1, 0, testName);				
				
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

			offset = 2;
			System.out.println("Trained " + names[i] + " using " + lr_trainData.getSetFlag());
			writer.println("Trained " + names[i] + " using " + lr_trainData.getSetFlag());
			System.out.println("P(attribute = y | class = y)");
			writer.println("P(attribute = y | class = y)");
			System.out.println("Top results for all");
			writer.println("Top results for all");
			sortMap(nb_termWeightsyy,nb_trainData);
			System.out.println("\nTop results for positive");
			writer.println("\nTop results for positive");
			sortMap(nb_positiveWeightsyy,nb_trainData);
			System.out.println("\nTop results for negative");
			writer.println("\nTop results for negative");
			sortMap(nb_negativeWeightsyy,nb_trainData);
			System.out.println("\nTop results for neutral");
			writer.println("\nTop results for neutral");
			sortMap(nb_neutralWeightsyy,nb_trainData);
			System.out.println();
			writer.println();

			System.out.println("P(attribute = y | class = y) / P(attribute = y | class = n)");
			writer.println("P(attribute = y | class = y) / P(attribute = y | class = n)");
			System.out.println("Top results for all");
			writer.println("Top results for all");
			sortMap(nb_termWeightsyn,nb_trainData);
			System.out.println("\nTop results for positive");
			writer.println("\nTop results for positive");
			sortMap(nb_positiveWeightsyn,nb_trainData);
			System.out.println("\nTop results for negative");
			writer.println("\nTop results for negative");
			sortMap(nb_negativeWeightsyn,nb_trainData);
			System.out.println("\nTop results for neutral");
			writer.println("\nTop results for neutral");
			sortMap(nb_neutralWeightsyn,nb_trainData);
			System.out.println();
			writer.println();
			
			offset = 3;
			System.out.println("Regression");
			writer.println("Regression");
			System.out.println("Top results for all");
			writer.println("Top results for all");
			sortMap(lr_termWeights,lr_trainData);
			System.out.println("\nTop results for positive");
			writer.println("\nTop results for positive");
			sortMap(lr_positiveWeights,lr_trainData);
			System.out.println("\nTop results for negative");
			writer.println("\nTop results for negative");
			sortMap(lr_negativeWeights,lr_trainData);
			System.out.println("\nTop results for neutral");
			writer.println("\nTop results for neutral");
			sortMap(lr_neutralWeights,lr_trainData);
			System.out.println();
			writer.println();
		}
		writer.close();
	}

	/*
	 * merge two maps together
	 */
	public static Map<Integer,Double> mergeMaps(Map<Integer,Double> map1, Map<Integer,Double> map2){
		Map<Integer, Double> merged = new HashMap<Integer, Double>(map1); 
		for (Map.Entry<Integer, Double> entry : map2.entrySet()) {
			Double y = merged.get(entry.getKey()); 
			merged.put(entry.getKey(), entry.getValue() + (y == null ? 0 : y));
		} 
		return merged;
	}

	/*
	 * normalise maps over folds
	 */
	public static Map<Integer,Double> normaliseMap(Map<Integer,Double> map1){
		
		/*
		 * features get classified differently, sometimes +'ve sometimes -'ve sometimes neutral..
		 * so just stick with a summation for now..
		 */
		
		/*for (Entry<Integer, Double> element : map1.entrySet()){
			map1.put(element.getKey(), (element.getValue()/Launcher.NUM_FOLDS));
		}*/
		return map1;
	}

	/*
	 * Extract weights from columns
	 */
	public static Map<Integer,Double>[] getColumnWeightsLR(LogisticRegression lr) throws Exception{		
		Map<Integer,Double>[] results = new HashMap[4];

		Map<Integer,Double> termWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> positiveWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> negativeWeights = new HashMap<Integer,Double>();
		Map<Integer,Double> neutralWeights = new HashMap<Integer,Double>();				

		for (int i = 0; i < lr._betas[0].numDimensions(); i++){
			double val = lr._betas[0].value(i);
			if (val > 0.0){
				positiveWeights.put(i, val);
			} else if (val < 0.0){
				negativeWeights.put(i, val);
			} else {
				neutralWeights.put(i, val);
			}
			termWeights.put(i, val);
			//System.out.println(i + " " + lr._model.weightVectors()[0].value(i));
		}		

		results[0] = termWeights;
		results[1] = positiveWeights;
		results[2] = negativeWeights;
		results[3] = neutralWeights;

		return results;

	}

	public static Map<Integer,Double>[] getColumnWeightsNB(NaiveBayes nb) throws Exception{
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
	 * sort and display map
	 */
	static void sortMap(Map<Integer,Double> map, ArffData p) throws Exception{
		int display = 15;
		SortedMap sortedData = new TreeMap(new ValueComparer(map));

		//System.out.println(map);
		int count = 1;
		//int offset = 2;
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

	public static void main(String[] args) throws Exception {
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