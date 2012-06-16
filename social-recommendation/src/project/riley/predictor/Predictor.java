package project.riley.predictor;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import project.riley.predictor.ArffData.DataEntry;
import util.Statistics;

public abstract class Predictor {

	DecimalFormat df3 = new DecimalFormat("#.###");
	public final static int CLASS_INDEX = 2;						// index of the class in the arff file

	// Note: SPS -- what was _arffData for?
	public ArffData _trainData;
	public ArffData _testData;
	
	public abstract void train();									// train the model
	public abstract int evaluate(DataEntry de);	// evaluate a new data entry based on trained model
	public abstract void clear();									// clear the model
	public abstract String getName();								// name of the classifier
	
	/*
	 * convert from arff to features array format
	 * 
	 * Note: SPS -- Following is bad style... the dataEntry has already stored
	 *              the parsed lines... why convert to a String and reparse it?
	 *              
	 *              Should also not pass size... it is determined here based on
	 *              dataEntry so I've removed this from the parameter list.
	 */
	public double[] getFeatures(DataEntry dataEntry){	
		double[] tmp = new double[dataEntry._entries.size() - 2];
		String line = dataEntry.toString();
		if (!line.startsWith("@") && line.length() > 0){			
			String[] parts = line.split(",");				// third item is class					
			tmp[0] = (double) yTo1nTo0(parts[2]);			// first 'feature' is class value
			for (int i = 3; i < parts.length; i++){			// first two parts of line are item id and user id
				tmp[(i-2)] = (double) yTo1nTo0(parts[i]);
			}		
		}
		return tmp;
	}	

	/*
	 * input is either 'n' or 'y'
	 */
	public int yTo1nTo0(String str){
		return (str.charAt(1) == 'n' ? 0 : 1);
	}

	/*
	 * Calculate measures for accuracy, precision, recall and f measure
	 */
	public int[] measures(ArrayList<DataEntry> data) {
		int[] measures = new int[4];
		int truePositive = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int correct = 0;
		for (DataEntry de : data) {
			int pred = evaluate(de); 												// predicted class
			int actual = ((Integer)((ArffData.DataEntry)de).getData(CLASS_INDEX)).intValue();	// actual class
			if (pred == actual) correct++;
			if (pred == actual && actual == 1) truePositive++;
			if (pred == 1 && actual == 0) falsePositive++;
			if (pred == 0 && actual == 1) falseNegative++;
		}
		measures[0] = correct;					 						// accuracy
		measures[1] = truePositive;										// true positive
		measures[2] = falsePositive;									// false positive
		measures[3] = falseNegative;									// false negative
		return measures;
	}

	/*
	 * Run tests on data
	 */
	public void runTests(String source_file, int num_folds) throws IOException {

		int correct = 0;									// correct classification
		int truePositive = 0;								// true positives
		int falsePositive = 0;								// false positives
		int falseNegative = 0;								// false negatives

		ArrayList<Double> accuracies = new ArrayList<Double>();
		ArrayList<Double> precisions = new ArrayList<Double>();
		ArrayList<Double> recalls    = new ArrayList<Double>();
		ArrayList<Double> fscores    = new ArrayList<Double>();

		System.out.println("Running " + getName() + " using " + source_file);	

		for (int i = 0; i < num_folds; i++){
			
			String trainName = source_file + ".train." + (i+1);
			String testName  = source_file + ".test."  + (i+1);
			_trainData = new ArffData(trainName);
			_testData  = new ArffData(testName);
			
			clear();
			train();												// build a classifier and train
			int[] testMeasures = measures(_testData._data);	// test data
			correct = testMeasures[0];
			truePositive = testMeasures[1];
			falsePositive = testMeasures[2];
			falseNegative = testMeasures[3];
			double precision = truePositive/(double)(truePositive + falsePositive);			// precision over folds
			double recall    = truePositive/(double)(truePositive + falseNegative);			// recall over folds														// recall over folds
		
			accuracies.add( correct / (double)_testData._data.size() );
			precisions.add( precision );
			recalls.add( recall );
			fscores.add(  2d * ((precision * recall)/(double)(precision + recall)) );
			
			//System.out.println("- Finished fold " + (i+1) + ", accuracy: " + df3.format( correct / (double)_testData._data.size() ));
		}

		System.out.println("Accuracy:  " + df3.format(Statistics.Avg(accuracies)) + "  +/-  " + df3.format(Statistics.StdError95(accuracies)));
		System.out.println("Precision: " + df3.format(Statistics.Avg(precisions)) + "  +/-  " + df3.format(Statistics.StdError95(precisions)));
		System.out.println("Recall:    " + df3.format(Statistics.Avg(recalls))    + "  +/-  " + df3.format(Statistics.StdError95(recalls)));
		System.out.println("F-Score:   " + df3.format(Statistics.Avg(fscores))    + "  +/-  " + df3.format(Statistics.StdError95(fscores)));
		System.out.println();
	}

}