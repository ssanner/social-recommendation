package project.riley.predictor;

import java.io.IOException;

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
		return "Social Recommender - ( " + type + " )";
	}
	
	public void runTests(String source_file, int num_folds) throws Exception {
		LinkRecommenderArff.setType(type);		
		LinkRecommenderArff.runTests(source_file,num_folds);
	}

}
