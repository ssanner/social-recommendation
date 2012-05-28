package project.riley.predictor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import project.riley.predictor.ArffData.DataEntry;

public abstract class Predictor {

	String dataFile = "datak1000.arff";
	ArffData data = new ArffData(dataFile);
	DecimalFormat df3 = new DecimalFormat("#.###");
	//private double[] _thresholds = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};
	private double[] _thresholds = {0.5};
	private int _iterations = 10;
	public int _classIndex = 2;
	
	public abstract void setData(ArffData.SplitData data);
	public abstract void train();
	public abstract <T> int evaluate(T de, double threshold);
	public abstract void clear();
	
	public abstract String getName();
	public abstract <T> ArrayList<T> getTrainData();
	public abstract <T> ArrayList<T> getTestData();
	
	/*
	 * convert from arff to features format
	 */
	public double[] getFeatures(DataEntry dataEntry, int size){	
		double[] tmp = new double[size];
		
		String line = dataEntry.toString();
		if (!line.startsWith("@") && line.length() > 0){
			
			// third item is class		
			String[] parts = line.split(",");
			
			// first 'feature' is class value
			tmp[0] = (double) yTo1nTo0(parts[2]);
			
			// first two parts of line are item id and user id
			for (int i = 3; i < parts.length; i++){
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
	public <T> double[] measures(ArrayList<T> data, double threshold){
		double[] measures = new double[5];
		int truePositive = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int correct = 0;
		for (T de : data) {
			int pred = evaluate(de, threshold); 
			int actual = ((Integer)((ArffData.DataEntry)de).getData(_classIndex)).intValue();
			if (pred == actual) correct++;
			if (pred == actual && actual == 1) truePositive++;
			if (pred == 1 && actual == 0) falsePositive++;
			if (pred == 0 && actual == 1) falseNegative++;
		}
		double precision = (double)truePositive/(truePositive + falsePositive);   
		double recall = (double)truePositive/(truePositive + falseNegative); 
		measures[0] = (double)correct/data.size(); 						// accuracy
		measures[1] = precision;										// precision
		measures[2] = recall;											// recall
		measures[3] = 2 * ((precision * recall)/(precision + recall)); 	// f measure
		measures[4] = (double)falsePositive/data.size();				// false positive rate
		return measures;
	}

	/*
	 * Run tests on data
	 */
	public void runTests() throws IOException{
		String rocFile = dataFile + "_" + getName() + "_ROC.data";
		BufferedWriter out = new BufferedWriter(new FileWriter(rocFile));
		System.out.println("Running " + getName() + " using " + dataFile);	

		for (double threshold : _thresholds){
			out.write(threshold + ",");
			double totalTrainAccuracy = 0.0;
			double totalTestAccuracy = 0.0;
			double totalTrainPrecision = 0.0;
			double totalTestPrecision = 0.0;
			double totalTrainRecall = 0.0;
			double totalTestRecall = 0.0;
			double totalTrainF = 0.0;
			double totalTestF = 0.0;
			double totalTrainFalsePositive = 0.0;
			double totalTestFalsePositive = 0.0;
			for (int i = 0; i < _iterations; i++){
				// Split data into train (80%) / test (20%)
				ArffData.SplitData s = data.splitData(.8d);

				// Build a classifier and train
				clear();
				setData(s);
				train();

				double trainMeasures[] = measures(getTrainData(),threshold);
				double testMeasures[] = measures(getTestData(),threshold);

				// Evaluate accuracy of trained classifier on train and test data
				/*System.out.println(i + " Accuracy on train: " + trainMeasures[0]);
			System.out.println(i + " Accuracy on test:  " + testMeasures[0]);
			System.out.println(i + " Precision on train: " + trainMeasures[1]);
			System.out.println(i + " Precision on test:  " + testMeasures[1]);
			System.out.println(i + " Recall on train: " + trainMeasures[2]);
			System.out.println(i + " Recall on test:  " + testMeasures[2]);
			System.out.println(i + " F measure on train: " + trainMeasures[3]);
			System.out.println(i + " F measure on test:  " + testMeasures[3]);		*/	

				totalTrainAccuracy += trainMeasures[0];
				totalTestAccuracy += testMeasures[0];
				totalTrainPrecision += trainMeasures[1];
				totalTestPrecision += testMeasures[1];
				totalTrainRecall += trainMeasures[2];
				totalTestRecall += testMeasures[2];
				totalTrainF += trainMeasures[3];
				totalTestF += testMeasures[3];
				totalTrainFalsePositive += trainMeasures[4];
				totalTestFalsePositive += testMeasures[4];

			}
			System.out.println("Threshold:" + threshold);			
			System.out.println("Train accuracy after " + _iterations + " iterations:" + (totalTrainAccuracy/_iterations));
			System.out.println("Test accuracy after " + _iterations + " iterations:" + (totalTestAccuracy/_iterations));
			System.out.println("Train precision after " + _iterations + " iterations:" + (totalTrainPrecision/_iterations));
			System.out.println("Test precision after " + _iterations + " iterations:" + (totalTestPrecision/_iterations));
			System.out.println("Train recall after " + _iterations + " iterations:" + (totalTrainRecall/_iterations));
			System.out.println("Test recall after " + _iterations + " iterations:" + (totalTestRecall/_iterations));
			System.out.println("Train f-measure after " + _iterations + " iterations:" + (totalTrainF/_iterations));
			System.out.println("Test f-measure after " + _iterations + " iterations:" + (totalTestF/_iterations));
			System.out.println("ROC data: " + threshold + "," + df3.format((double)totalTestAccuracy/_iterations) + "," + df3.format((double)totalTestFalsePositive/_iterations));
			out.write((df3.format((double)totalTestAccuracy/_iterations)) + "," + df3.format(((double)totalTestFalsePositive/_iterations)));
			out.newLine();
		}
		System.out.println(rocFile + " written with ROC data");
		out.close();
	}

}