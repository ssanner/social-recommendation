/*
 * Use as much of the old code as possible.
 * But make a new class and method as I don't trust myself to edit the old code and not break anything.
 * 
 * Class for running experiments for the MLJ paper
 */
package project.suvash.predictor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.nicta.lr.LinkRecommender;
import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.FeatureRecommender;
import org.nicta.lr.recommender.SocialRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class LinkRecommenderCSV extends LinkRecommender
{
	public Double[] run(String trainFile, String testFile, String type)
			throws Exception
	{
		this.type = type;
		
		Set<Long> linkIds = new HashSet<Long>();
		Set<Long> userIds = new HashSet<Long>();
		Map<Long, Set<Long>> linkLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> testLikes= new HashMap<Long, Set<Long>>();
        HashSet<Long> allLinks = new HashSet<Long>();

		
		//read train data
		
		FileInputStream trainingInputStream = new FileInputStream(trainFile); 
		InputStreamReader trainingInputStreamReader = new InputStreamReader(trainingInputStream);
		BufferedReader trainingReader = new BufferedReader(trainingInputStreamReader);

		String line = null;
		
		while((line = trainingReader.readLine()) != null){
			String[] data = readCSVString(line);
			Long uid = Long.parseLong(data[0]);
			Long link_id = Long.parseLong(data[1]);
			Integer like = Integer.parseInt(data[2]);
			linkIds.add(link_id);
			userIds.add(uid);
			
			if(!trainData.containsKey(uid)){
				trainData.put(uid, new HashSet<Long>());
			}
			trainData.get(uid).add(link_id);
			
			if(like == 1){
				if(!linkLikes.containsKey(link_id)){
					linkLikes.put(link_id, new HashSet<Long>());
				}
				linkLikes.get(link_id).add(uid);
			}
			allLinks.add(link_id);
		}
		
		//read test data
		
		FileInputStream testInputStream = new FileInputStream(testFile); 
		InputStreamReader testInputStreamReader = new InputStreamReader(testInputStream);
		BufferedReader testReader = new BufferedReader(testInputStreamReader);

		while((line = testReader.readLine()) != null){
			String[] data = readCSVString(line);
			Long uid = Long.parseLong(data[0]);
			Long link_id = Long.parseLong(data[1]);
			Integer like = Integer.parseInt(data[2]);
			
			linkIds.add(link_id);
			userIds.add(uid);
			
			if(!testData.containsKey(uid)){
				testData.put(uid, new HashSet<Long>());
			}
			testData.get(uid).add(link_id);
			
			if(like == 1){
				if(!linkLikes.containsKey(link_id)){
					linkLikes.put(link_id, new HashSet<Long>());
				}
				linkLikes.get(link_id).add(uid);
			}
		}
		

		//Read features
		Map<Long, Double[]> users = UserUtil.getUserFeatures(userIds);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linkIds);
		
		
		//Remove ids that aren't actually links after all
		int num_invalid_link_ids = 0;
		for (Long userId : trainData.keySet()) {
			Set<Long> remove = new HashSet<Long>();
			
			for (Long linkId : trainData.get(userId)) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
					num_invalid_link_ids++;
				}
			}
			
			trainData.get(userId).removeAll(remove);
		}
		for (Long userId : testData.keySet()) {
			Set<Long> remove = new HashSet<Long>();
			
			for (Long linkId : testData.get(userId)) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
					num_invalid_link_ids++;
				}
			}
			
			testData.get(userId).removeAll(remove);
		}
		System.out.println("Discarded " + num_invalid_link_ids + " invalid link IDs.");
				
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		 int count =0;
         for(long userId: trainData.keySet()){
         	count += trainData.get(userId).size();
         }
         
         System.out.format("Training size: %d\n", count);
         
         count =0;
         for(long userId: testData.keySet()){
         	count += testData.get(userId).size();
         }
         
         System.out.format("Test size: %d\n", count);
         
         Map<Long, Long[]> linkPosters = LinkUtil.getLinkPosters(allLinks);
         
         HashSet<Long> removeUsers = new HashSet<Long>();
                 
         for (long userId : trainData.keySet()) {
                 Set<Long> testLinks = trainData.get(userId);
                         
                 HashSet<Long> removeLinks = new HashSet<Long>();
                         
                 for (long linkId : testLinks) {
                         Long[] posters = linkPosters.get(linkId);
                         if (posters == null) {
                                 removeLinks.add(linkId);
                                 continue;
                         }
                 }
                         
                 testLinks.removeAll(removeLinks);
                 if (testLinks.size() == 0) {
                         removeUsers.add(userId);
                         continue;
                 }
                         
         }
         for (long userId : removeUsers) {
                 trainData.remove(userId);
         }
         
        Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		((SocialRecommender)recommender).setLambda(1);
		((SocialRecommender)recommender).setBeta(0.0001);
		recommender.train(trainData);
		
		Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
		Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
		double threshold = getOptimalThreshold(trainPredictions, linkLikes);
		return getClassificationMetrics(predictions, linkLikes, threshold);
	}
	
	private String[] readCSVString(String line){
		ArrayList<String> items = new ArrayList<String>();

		StringTokenizer st = new StringTokenizer(line,",");
		while (st.hasMoreTokens())
		{
			//get next token and store it in the array
			 items.add(st.nextToken());
		}
		String[] result = new String[items.size()];
		return items.toArray(result);
	}
	
    public static void main(String[] args)
            throws Exception
    {
    	LinkRecommenderCSV csv = new LinkRecommenderCSV();
        csv.run("activeCsv/active_data.csv.train.1", "activeCsv/active_data.csv.test.1", Constants.SOCIAL);
    }
    
 
      public Double[] getClassificationMetrics(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
        {
                double truePos = 0;
                double falsePos = 0;
                double trueNeg = 0;
                double falseNeg = 0;
                
                int totalCount = 0;
                
                for (long userId : predictions.keySet()) {
                        Map<Long, Double> userPredictions = predictions.get(userId);
                        
                        for (long linkId : userPredictions.keySet()) {
                                totalCount++;
                                
                                Set<Long> likes = linkLikes.get(linkId);
                                boolean trueLikes = false;
                                if (likes != null && likes.contains(userId)) trueLikes = true;
                                
                                double val = userPredictions.get(linkId);
                                boolean predicted = false;
                                if (val >= threshold) predicted = true;
                                
                                if (predicted) {
                                        if (trueLikes) {
                                                truePos++;
                                        }
                                        else {
                                                falsePos++;
                                        }
                                }
                                else {
                                        if (trueLikes) {
                                                falseNeg++;
                                        }
                                        else {
                                                trueNeg++;
                                        }
                                }
                        }
                }
                
                double accuracy = (truePos + trueNeg) / (truePos + trueNeg + falsePos + falseNeg);
                if ((truePos + trueNeg + falsePos + falseNeg) == 0) accuracy = 0;
                
                double precision = truePos / (truePos + falsePos);
                if ((truePos + falsePos) == 0) precision = 0;
                
                double recall = truePos / (truePos + falseNeg);
                if ((truePos + falseNeg) == 0) recall = 0;
                
                double f1 = 2 * (precision * recall) / (precision + recall);
                if ((precision + recall) == 0) f1 = 0;
                
                double confidence = 2 * Math.sqrt((accuracy * (1 - accuracy)) / totalCount);
                
                System.out.println("TP: " + truePos + " FP: " + falsePos + " TN: " + trueNeg + " FN: " + falseNeg);
                System.out.println("Accuracy: " + accuracy);
                System.out.println("Confidence Interval: " + confidence);
                System.out.println("Precision: " + precision);
                System.out.println("Recall: " + recall);
                System.out.println("F1: " + f1);
                
                return new Double[]{accuracy, precision, recall, f1};
        }
}