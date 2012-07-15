package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.ArffData.FoldData;

/*
 * generate n-fold data for cross validationf
 * 
 */
public class FoldsGenerator {
	
	public static final String FILENAME = "passive.arff";
	public static final int NUM_FOLDS = 10;
	
	public static void main(String args[]) throws Exception {
		DataGeneratorAccurateLabelsv2.populateCachedData(false /* active */,/* top k */10);				// generate data
		DataGeneratorAccurateLabelsv2.writeData(FILENAME,/* interaction threshold */ 0);				// write data
		
		ArffData arff = new ArffData(FILENAME);															// load data
		FoldData folds = arff.foldData(NUM_FOLDS);
		folds.writeData();																				// split into folds
		System.out.println("Generated " + NUM_FOLDS + " folds and exported files.");
	}
}
