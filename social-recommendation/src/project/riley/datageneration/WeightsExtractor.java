package project.riley.datageneration;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.SQLUtil;

import project.riley.predictor.ArffData;
import project.riley.predictor.Launcher;
import project.riley.predictor.LogisticRegression;
import project.riley.predictor.NaiveBayes;
import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.NaiveBayes.ClassCondProb;
import util.Statistics;

public class WeightsExtractor {

	// this was a rush job..

	static PrintWriter writer;
	static String[] names = {"interactions","demographics","traits","groups","pages","messages outgoing","messages incoming"};
	static LogisticRegression[] lrs = 
		{new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d, /*maxent*/ true),						// inter
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),										// demo
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d, /*maxent*/ true),						// traits
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d, true),									// groups
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d),										// pages
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d, true),									// outgoing
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d, true),									// incoming
		new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d)};								
	static DecimalFormat df3 = new DecimalFormat("#.###");

	
	/*
	 * set up arff file based on flag
	 */
	public static ArffData getArff(int flag, int type, String name){
		// flag
		// 0 = none
		// 1 = interactions
		// 2 = demographics
		// 3 = traits
		// 4 = groups
		// 5 = pages
		// 6 = outgoing
		// 7 = incoming
		// 8 = all
		
		//type
		// 0 = lr
		// 1 = nb
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
		data.setInteractions(((flag == 1) ? true : false));
		data.setDemographics(((flag == 2) ? true : false));
		data.setTraits(((flag == 3 || flag == 8) ? true : false));
		data.setGroups(((flag == 4 || flag == 8) ? true : false), groupsize);
		data.setPages(((flag == 5 || flag == 8) ? true : false), pagesize);
		data.setOutgoingMessages(((flag == 6) ? true : false), outgoingsize);
		data.setIncomingMessages(((flag == 7) ? true : false), incomingsize);
		data.setFileName(name);

		return data;
	}	

	static int offset;
	static Double[][][][] results;
	static ArffData current;	
	
	public static void runColumnWeightsTests() throws Exception{
		writer = new PrintWriter("weights_results.txt");

		for (int i = 0; i < names.length; i++){			

			NaiveBayes nb = new NaiveBayes(1.0d);
			LogisticRegression lr = lrs[i];				

			ArffData nb_trainData = null;
			ArffData nb_testData = null;

			ArffData lr_trainData = null;
			ArffData lr_testData = null;	

			boolean set = false;

			for (int k = 0; k < Launcher.NUM_FOLDS; k++){												

				String trainName = Launcher.DATA_FILE + ".train." + (k+1);
				String testName  = Launcher.DATA_FILE + ".test."  + (k+1);

				nb_trainData = getArff((i+1), 1, trainName);
				nb_testData  = getArff((i+1), 1, testName); 

				lr_trainData = getArff((i+1), 0, trainName);
				lr_testData  = getArff((i+1), 0, testName);

				if (!set){
					// 1 = LR, 2 = NB_YY, 3 = NB_YN					
					//System.out.println(nb_trainData._attr.size());
					//System.out.println(lr_trainData._attr.size());
					results = new Double[4][2][Launcher.NUM_FOLDS][2500];
					current = getArff((i+1), 0, Launcher.DATA_FILE);
					set = true;
				}

				lr._trainData = lr_trainData;
				lr._testData = lr_testData;
				lr.clear();
				lr.train();	
				
				nb._trainData = nb_trainData;
				nb._testData = nb_testData;
				nb.clear();
				nb.train();

				getColumnWeightsLR(lr,k);
				getColumnWeightsNB(nb,k);
				
			}												
			
			System.out.println("Logistic");
			writer.println("Logistic");
			display(1,0);
			display(1,1);
			
			System.out.println("NB YY");
		 	writer.println("NB YY");
			display(2,0);
			display(2,1);
			
			System.out.println("NB YN");
			writer.println("NB YN");
			display(3,0);
			display(3,1);
			
			System.out.println();
			writer.println();
			
		}
		writer.close();
	}
	
	public static void display(int p, int t) throws Exception{
		Double[] avg = avg(p,t);
		Double[] std = stderr(p,t);
		int display = 10;
		System.out.println((t == 0) ? "Negative" : "Positive");
		writer.println((t == 0) ? "Negative" : "Positive");
		
		ArrayIndexComparator comparator = new ArrayIndexComparator(avg);
		Integer[] indexes = comparator.createIndexArray();
		Arrays.sort(indexes, comparator);
		
	/*	for (int i = 0; i < avg.length; i++)
			System.out.print(avg[i] + " ");
		System.out.println();
		
		for (int i = 0; i < indexes.length; i++)
			System.out.print(avg[indexes[i]] + "(" + indexes[i] + ") ");		
		System.out.println();*/
		
	
		for (int i = 0; i < avg.length; i++){
			
			int index = indexes[i];
			if (t == 1)
				index = indexes[avg.length-i-1];
			
			if (avg[index] == 0.0){
				break;
			}			
			
			String attribute = current._attr.get(index+3).name;
	
			int yes = 0;
			int uniqueYes = 0;
			Set<Double> users = new HashSet<Double>();
			for (DataEntry entry : current._data){
				Integer c = ((Integer)entry.getData(index+3));
				//System.out.println(entry._entries.size());
				if (c > 0){
					yes++;
					//System.out.println((Double)entry.getData(0) + ":" + users.contains((Double)entry.getData(0)) + ":" + users.size());
					if (!users.contains((Double)entry.getData(0))){
						uniqueYes++;
						users.add((Double)entry.getData(0));
					}
				}				
			}
			
			System.out.println((i+1) + "\t" + attribute + "\t\t" + df3.format(avg[index]) + " +/- " + df3.format(std[index]) + "\t\t yes(" + yes + ") " + "\t\t unique(" + uniqueYes + ")" +  (attribute.contains("group_") ? "\t\t" + getData("linkrGroups",attribute) : "") + (attribute.contains("page_") ? "\t\t" + getData("linkrLikes",attribute) : ""));
			writer.println((i+1) + "\t" + attribute + "\t\t" + df3.format(avg[index]) + " +/- " + df3.format(std[index]) + "\t\t yes(" + yes + ") " + "\t\t unique(" + uniqueYes + ")" +  (attribute.contains("group_") ? "\t\t" + getData("linkrGroups",attribute) : "") + (attribute.contains("page_") ? "\t\t" + getData("linkrLikes",attribute) : ""));
			if (--display < 1)
				break;
		}
		
	}

	public static Double[] avg(int predictor, int type){
		// predictor:  1 = LR, 2 = NB_YY, 3 = NB_YN
		// type: 1 = positive, 0 = negative
		int size = results[0][0][0].length;
		Double[] res = new Double[size];
		
		for (int i = 0; i < res.length; i++)
			res[i] = 0.0;
		
		double[] count = new double[size];

		for (int i = 0; i < Launcher.NUM_FOLDS; i++){
			for (int j = 0; j < size; j++){
				//System.out.println(i + " " + j + " " + results[predictor][type][i][j] + " " + res[j]);
				if (results[predictor][type][i][j] != null){
					res[j] += results[predictor][type][i][j];
					count[j]++;
				}				
			}
		}

		for (int i = 0; i < size; i++){
			//System.out.println(res[i] + " " + count[i]);
			//res[i] = Math.log(res[i]);
			if (count[i] != 0.0)
				res[i] /= count[i];
		}

		return res;
	}
	
	public static Double[] stderr(int predictor, int type){
		Double[] avg = avg(predictor, type);		
		int sz = avg.length;
		
		Double[] res = new Double[sz];
		for (int i = 0; i < res.length; i++)
			res[i] = 0.0;
		
		double[] ssq = new double[sz];
		double[] ct = new double[sz];
		
		for (int i = 0; i < Launcher.NUM_FOLDS; i++){
			for (int j = 0; j < sz; j++) {
				if (results[predictor][type][i][j] != null){
					double factor = (results[predictor][type][i][j] - avg[j]);
					ssq[j] += factor * factor;
					ct[j]++;
				}				
			}
		}	
		
		double stdev[] = new double[sz];
		for (int i = 0; i < sz; i++){
			stdev[i] = Math.sqrt(ssq[i] / (sz - 1)); 
			res[i] = Statistics.getTCoef95(sz) * (stdev[i] / Math.sqrt(sz));
		}

		return res;
	}

	/*
	 * Extract weights from columns
	 */	
	public static void getColumnWeightsLR(LogisticRegression lr, int fold) throws Exception{		

		//System.out.println(lr._betas[0].numDimensions() + " " + results[1][1][fold].length);
		for (int i = 0; i < lr._betas[0].numDimensions(); i++){
			//System.out.println(lr._trainData._attr.get(i+offset).name);			
			Double val = lr._betas[0].value(i);
			//System.out.println(val + " " + fold + " "+ i);
			if (val > 0.0){
				results[1][1][fold][i] = val;
//			} else if (val < 0.0){
			} else {
				results[1][0][fold][i] = val;
			} 
			//System.out.println(i + " " + lr._model.weightVectors()[0].value(i));
		}
	}

	public static void getColumnWeightsNB(NaiveBayes nb, int fold) throws Exception{

		for (int i = 0; i < nb._condProb.size(); i++) {

			ClassCondProb ccp = nb._condProb.get(i);

			ArffData.Attribute  a = nb._trainData._attr.get(ccp._attr_index);
			ArffData.Attribute ca = nb._trainData._attr.get(nb.CLASS_INDEX);

			if (ccp._attr_index != nb.CLASS_INDEX){

				//System.out.println(nb._trainData._attr.get(i+2).name);
				//System.out.println(i);

				/*System.out.println("P( " + a.name + " = " + a.getClassName(1) + " | " + ca.name + " = " + ca.getClassName(1) + 
						" ) = " + (Math.exp(ccp._logprob[1][1])));
				System.out.println("P( " + a.name + " = " + a.getClassName(1) + " | " + ca.name + " = " + ca.getClassName(0) + 
						" ) = " + (Math.exp(ccp._logprob[1][0])));*/
				Double yy = Math.exp(ccp._logprob[1][1]);
				Double yn = Math.exp(ccp._logprob[1][0]);

				if (yy > 0.5){
					results[2][1][fold][i-1] = yy - 0.5;
				}
				else if (yy < 0.5){
					results[2][0][fold][i-1] = yy - 0.5;
				}


				if ((yy / yn) > 0.5){
					results[3][1][fold][i-1] = (yy / yn) - 0.5;
				}
				else if ((yy / yn) < 0.5){
					results[3][0][fold][i-1] = (yy / yn) - 0.5;
				}
			}

		}		
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

	public static class ArrayIndexComparator implements Comparator<Integer>{
	    private final Double[] array;

	    public ArrayIndexComparator(Double[] array) {
	        this.array = array;
	    }

	    public Integer[] createIndexArray()
	    {
	        Integer[] indexes = new Integer[array.length];
	        for (int i = 0; i < array.length; i++){
	            indexes[i] = i; // Autoboxing
	        }
	        return indexes;
	    }

	    @Override
	    public int compare(Integer index1, Integer index2){
	        return array[index1].compareTo(array[index2]);
	    }
		
	}

	
}