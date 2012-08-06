package project.riley.predictor;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class BayesianModelAveraging {

	public static ArffData arff;
	public static int CLASS_INDEX = 2;
	public static DecimalFormat df3 = new DecimalFormat("#.###");
	public static int[] predictions;
	
	/*
	 * n predictors which occur over x folds
	 */
	public static HashMap<String, int[][]> mapping;
	
	/*
	 * Set the arff data file and initialise array sizes
	 */
	public static void setArff(String f){
		arff = new ArffData(f);
		predictions = new int[arff._data.size()]; 
		mapping = new HashMap<String, int[][]>();
	}
	
	/*
	 * Add some predictors results for the data at the given index
	 */
	public static void addResult(String name, int index, int prediction){
		int[][] results = mapping.get(name);
		if (results == null){
			results = new int[2][arff._data.size()];
		}
		if (prediction == 1){
			results[1][index]++;
		} else {
			results[0][index]++;
		}
		mapping.put(name, results);		
	}
	
	/*
	 * calculate final results 
	 */
	public static void calculateFinalResults(){
		int[] predictor_ones = new int[arff._data.size()];							// predictor decision
		int[] predictor_zeros = new int[arff._data.size()];
		for (Map.Entry<String, int[][]> predictor : mapping.entrySet()){			// each predictor
			String name = predictor.getKey();
			int[][] results = predictor.getValue();
			System.out.print("Calclating results for " + name + ": ");
			for (int i = 0; i < arff._data.size(); i++){
				if (results[1][i] >= results[0][i]){								// top rated 1 or 0 from folds
					predictor_ones[i]++;
					System.out.print("(" + i + ":" + 1 + ")");
				} else {
					predictor_zeros[i]++;
					System.out.print("(" + i + ":" + 0 + ")");
				}
			}
			System.out.println();
		}
		System.out.print("Calculating total results:");
		for (int i = 0; i < arff._data.size(); i++){
			predictions[i] = (predictor_ones[i] >= predictor_zeros[i] ? 1 : 0);		// top rated from all predictors and folds
			System.out.print("(" + i + ":" + predictions[i] + ")");
		}
	}
	
	/*
	 * Display the results over each predictor
	 */
	public static void results(PrintWriter writer){
		
		calculateFinalResults();
		
		System.out.println("Bayesian model averaging results:");
		writer.println("Bayesian model averaging results:");
		
		int truePositive = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int correct = 0;
		
		for (int i = 0; i < arff._data.size(); i++) {
			int actual = ((Integer)((ArffData.DataEntry)arff._data.get(i)).getData(CLASS_INDEX)).intValue();
			int pred = predictions[i];
			if (pred == actual) correct++;
			if (pred == actual && actual == 1) truePositive++;
			if (pred == 1 && actual == 0) falsePositive++;
			if (pred == 0 && actual == 1) falseNegative++;
		}
		
		double accuracy  = (double) correct / arff._data.size();
		double precision = truePositive/(double)(truePositive + falsePositive);
		double recall    = truePositive/(double)(truePositive + falseNegative);
		double fscore    = 2d * ((precision * recall)/(double)(precision + recall));
		
		System.out.println("Accuracy:  " + df3.format(accuracy) /*+ "  +/-  " + df3.format(Statistics.StdError95(accuracies)) */);
		writer.println("Accuracy:  " + df3.format(accuracy) /*+ "  +/-  " + df3.format(Statistics.StdError95(accuracies))*/);
		System.out.println("Precision: " + df3.format(truePositive/(double)(truePositive + falsePositive)) /*+ "  +/-  " + df3.format(Statistics.StdError95(precisions))*/);
		writer.println("Precision: " + df3.format(truePositive/(double)(truePositive + falsePositive)) /*+ "  +/-  " + df3.format(Statistics.StdError95(precisions))*/);
		System.out.println("Recall:    " + df3.format(truePositive/(double)(truePositive + falseNegative))  /*  + "  +/-  " + df3.format(Statistics.StdError95(recalls))*/);
		writer.println("Recall:    " + df3.format(truePositive/(double)(truePositive + falseNegative))   /* + "  +/-  " + df3.format(Statistics.StdError95(recalls))*/);
		System.out.println("F-Score:   " + df3.format(fscore)   /* + "  +/-  " + df3.format(Statistics.StdError95(fscores))*/);
		writer.println("F-Score:   " + df3.format(fscore)    /*+ "  +/-  " + df3.format(Statistics.StdError95(fscores))*/);
		System.out.println();
		writer.println();
		
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		PrintWriter p = new PrintWriter("asd.txt");
		setArff("a.arff");
		addResult("a",0,1);
		addResult("a",1,1);
		addResult("a",2,1);
		addResult("a",3,1);
		addResult("a",3,0);
		addResult("a",3,0);
		addResult("b",3,1);
		addResult("b",3,1);
		addResult("b",3,1);
		addResult("c",3,0);
		results(p);
	}
	
}
