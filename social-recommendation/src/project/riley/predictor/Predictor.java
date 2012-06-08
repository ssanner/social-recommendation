package project.riley.predictor;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import project.riley.predictor.ArffData.DataEntry;

public abstract class Predictor {

	String[] likesType = {"pl","al"};	// naming convention {pl||al}_train_fold{0..9}.arff
	int folds = 10;						// number of folds

	ArffData _arffData = null;
	ArffData _testData = null;
	ArffData _trainData = null;	

	DecimalFormat df3 = new DecimalFormat("#.###");
	public int _classIndex = 2;										// index of the class file

	public abstract void train();									// train the model
	public abstract int evaluate(DataEntry de, double threshold);	// evaluate a new data entry based on trained model
	public abstract void clear();									// clear the model
	public abstract String getName();								// name of the classifier

	/*
	 * convert from arff to features array format
	 */
	public double[] getFeatures(DataEntry dataEntry, int size){	
		double[] tmp = new double[size];
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
	public double[] measures(ArrayList<DataEntry> data, double threshold){
		double[] measures = new double[4];
		int truePositive = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int correct = 0;
		for (DataEntry de : data) {
			int pred = evaluate(de, threshold); 												// predicted class
			int actual = ((Integer)((ArffData.DataEntry)de).getData(_classIndex)).intValue();	// actual class
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
	public void runTests() throws IOException{
		for (String like : likesType){
			int correct = 0;									// correct classification
			int truePositive = 0;								// true positives
			int falsePositive = 0;								// false positives
			int falseNegative = 0;								// false negatives

			for (int i = 0; i < folds; i++){
				String trainName = like + "_train_fold" + i + ".arff";		// naming convention {pl||al}_train_fold{0..9}.arff
				String testName = like + "_test_fold" + i + ".arff";		// naming convention {pl||al}_test_fold{0..9}.arff
				System.out.println("Running " + getName() + " using " + trainName);	

				_testData = new ArffData(testName);						// testing data set
				_trainData = new ArffData(trainName);					// training data set	
				_arffData = _trainData;									// used by naive bayes
				
				clear();
				train();												// build a classifier and train
				double testMeasures[] = measures(_testData._data,0.5);	// test data
				correct += testMeasures[0];
				truePositive += testMeasures[1];
				falsePositive += testMeasures[2];
				falseNegative += testMeasures[3];
			}
			int totalDataSize = _testData._data.size() + _trainData._data.size();					// total data size
			double precision = (double)truePositive/(double)(truePositive + falsePositive);			// precision over folds
			double recall = (double)truePositive/(double)(truePositive + falseNegative);			// recall over folds														// recall over folds
			double fscore = 2 * ((precision * recall)/(precision + recall));						// fscore over folds														// fscore over folds
			System.out.println("Accuracy: " + df3.format(correct/totalDataSize));
			System.out.println("Precision: " + df3.format(precision));
			System.out.println("Recall: " + df3.format(recall));
			System.out.println("F-Score: " + df3.format(fscore));
		}
	}

}