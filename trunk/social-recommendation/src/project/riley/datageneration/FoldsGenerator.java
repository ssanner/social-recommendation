package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.ArffData.FoldData;

/*
 * generate n-fold data for cross validation
 * 
 */
public class FoldsGenerator {
	
	public static final String FILENAME = "passive.arff";
	public static final int NUM_FOLDS = 10;
	
	public static void main(String args[]) {

		ArffData arff = new ArffData(FILENAME);
		FoldData folds = arff.foldData(NUM_FOLDS);
		folds.writeData();
		System.out.println("Generated " + NUM_FOLDS + " folds and exported files.");
	}
}
