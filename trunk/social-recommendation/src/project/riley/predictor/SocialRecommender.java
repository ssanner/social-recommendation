package project.riley.predictor;

import java.io.IOException;
import java.io.PrintWriter;

import org.nicta.lr.util.Constants;

import project.riley.predictor.ArffData.DataEntry;

/*
 * bridge to josephs link recommender code
 */

public class SocialRecommender extends Predictor {

	String type = null;
	
	public SocialRecommender(String type){
		this.type = type;
	}
	
	@Override
	public void train() {}

	@Override
	public int evaluate(DataEntry de) {	return 0; }

	@Override
	public void clear() {}

	@Override
	public String getName() {
		return "SocialRecommender(" + type + ")";
	}
	
	public void runTests(String source_file, int num_folds, int threshold, PrintWriter writer, boolean demographics, boolean groups, boolean conversations) throws Exception {
		System.out.println("Running " + getName() + " using " + source_file);
		writer.println("Running " + getName() + " using " + source_file);
		LinkRecommenderArff.setType(type);		
		LinkRecommenderArff.runTests(source_file, num_folds, threshold, writer, demographics, groups, conversations);
	}

}
