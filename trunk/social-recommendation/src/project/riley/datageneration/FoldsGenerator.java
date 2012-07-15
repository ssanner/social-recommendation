package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.SQLException;
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
	
	public static void main(String args[]) throws Exception {
		//DataGeneratorPassiveActive.populateCachedData(true /* active */);							// generate data
		DataGeneratorPassiveActive.populateCachedData(false /* passive */);							// generate data
		DataGeneratorPassiveActive.writeData(FILENAME, 0 /* interaction threshold */);				// write data
		
		ArffData arff = new ArffData(FILENAME);														// load data
		FoldData folds = arff.foldData(NUM_FOLDS);
		folds.writeData();																			// split into folds
		System.out.println("Generated " + NUM_FOLDS + " folds and exported files.");
	}
}
