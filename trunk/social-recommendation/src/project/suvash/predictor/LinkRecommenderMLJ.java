/*
 * Use as much of the old code as possible.
 * But make a new class and method as I don't trust myself to edit the old code and not break anything.
 * 
 * Class for running experiments for the MLJ paper
 */
package project.suvash.predictor;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.LinkRecommender;
import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.FeatureRecommender;
import org.nicta.lr.recommender.SocialRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class LinkRecommenderMLJ extends LinkRecommender
{
	private Random random = new Random();
	
	public Map<Long, Set<Long>>[] splitData()
                throws Exception
        {
                random.setSeed(1);
                System.out.println("Get friendships");
                Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
                
                System.out.println("Get training data");
                Map<Long, Set<Long>>[] data = getTrainingData(friendships);
                Map<Long, Set<Long>> trainData = data[0];
                Map<Long, Set<Long>> linkLikes = data[1];
                
                Set<Long> usersNeeded = new HashSet<Long>();
                Set<Long> linksNeeded = new HashSet<Long>();
                
                usersNeeded.addAll(trainData.keySet());
                linksNeeded.addAll(linkLikes.keySet());
                for (long id : trainData.keySet()) {
                        linksNeeded.addAll(trainData.get(id));
                }
                
                Map<Long, Set<Long>>[] dataSplits = new Map[10];
                Map<Long, Set<Long>> userDone = new HashMap<Long, Set<Long>>();
                
                for (int x = 0; x < 10; x++) {
                        System.out.println(x);
                        Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();
                        
                        Map<Long, Set<Long>> activeData = trainData;
                        
                        for (long userId : trainData.keySet()) {
                                if (x == 0) {
                                        userDone.put(userId, new HashSet<Long>());
                                }
                                
                                Set<Long> userLinks = trainData.get(userId);
                                
                                int likeCount = 0;
                                int notLikeCount = 0;
                                
                                HashSet<Long> userLikes = new HashSet<Long>();
                                Set<Long> active = activeData.get(userId);
                                
                                for (long linkId : userLinks) {         
                                        if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
                                                likeCount++;
                                                userLikes.add(linkId);
                                        }
                                        else {
                                                notLikeCount++;
                                        }
                                }
                                
                                int testLikeCount = (int)(likeCount * 0.1);
                                if (testLikeCount < 1) continue;
                                
                                int testNotLikeCount = (int)(notLikeCount * 0.1);
                                if (testNotLikeCount < 1) continue;
                                
                                int addedLike = 0;
                                int addedNotLike = 0;
                                
                                Object[] links = userLinks.toArray();
                                
                                HashSet<Long> userTest = new HashSet<Long>();
                                
                                //Add likes for testing
                                while (addedLike < testLikeCount) {
                                        int randomIndex = (int)(random.nextDouble() * links.length);
                                        Long linkId = (Long)links[randomIndex];
                                        
                                        if (active == null || !active.contains(linkId)) continue;
                                        if (userDone.get(userId).contains(linkId)) continue;
                                        
                                        if (userLikes.contains(linkId) && !userTest.contains(linkId)) {
                                                userTest.add(linkId);
                                                addedLike++;
        
                                        }       
                                }
                                
                                //Add dislikes for testing
                                while (addedNotLike < testNotLikeCount) {
                                        int randomIndex = (int)(random.nextDouble() * links.length);
                                        Long linkId = (Long)links[randomIndex];
                                        
                                        if (active == null || !active.contains(linkId)) continue;
                                        if (userDone.get(userId).contains(linkId)) continue;
                                        
                                        if (!userLikes.contains(linkId) && !userTest.contains(linkId)) {
                                                userTest.add(linkId);
                                                addedNotLike++;
                                        }
                                }
                                
                                testData.put(userId, userTest);
                        }
                        
                        dataSplits[x] = testData;
                }
                
                FileOutputStream fos = new FileOutputStream("dataset.csv"); 
        		OutputStreamWriter out = new OutputStreamWriter(fos);
        		
        		for(int i=0; i< 10; i++){
        			 Map<Long, Set<Long>> d = dataSplits[i];
        			 for(Long uid: d.keySet()){
        				 for(Long linkid: d.get(uid)){
        					 out.write(uid.toString()+","+linkid.toString()+"\n");
        					 out.flush();
        				 }
        			 }
        		}
        		out.close();
        		fos.close();
                return dataSplits;
        }
        
        /*
         * Of the long algorithm list, which method works best training and testing Acc / P / R / F-score on actively collected data?  I think best not to use passively collected "likes"... 
         * people have pointed out that a "likes" in the App and a "likes" on FB could have different semantics.  So we're just doing cross-validation of train / test splits of the active data.  
         * Keep track of these train / test splits (store them on disk)... it's important the all evaluations reuse them.
         */
        public void run1(Map<Long, Set<Long>>[] dataSplits)
                throws Exception
        {
                random.setSeed(1);
                System.out.println("Get friendships");
                Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
                
                System.out.println("Get training data");
                Map<Long, Set<Long>>[] data = getTrainingData(friendships);
                Map<Long, Set<Long>> linkLikes = data[1];
                
                Set<Long> usersNeeded = new HashSet<Long>();
                Set<Long> linksNeeded = new HashSet<Long>();
                
                usersNeeded.addAll(data[0].keySet());
                linksNeeded.addAll(linkLikes.keySet());
                
                for (long userId : data[0].keySet()) {
                        linksNeeded.addAll(data[0].get(userId));
                }

                Map<Long, Double[]> users = UserUtil.getUserFeatures(usersNeeded);
                Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linksNeeded);
                
                double[] accuracies = new double[10];
                double[] precisions = new double[10];
                double[] recalls = new double[10];
                double[] f1s = new double[10];
                double meanAccuracy = 0;
                double meanPrecision = 0;
                double meanRecall = 0;
                double meanF1 = 0;
                
                for (int x = 0; x < 10; x++) {
                	Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
                    ((SocialRecommender)recommender).setLambda(1);
                    ((SocialRecommender)recommender).setBeta(0.0001);
                    
                        Map<Long, Set<Long>> testData = dataSplits[x];
                        Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();
                        
                        for (int y = 0; y < 10; y ++) {
                                if (x == y) continue;
                                
                                Map<Long, Set<Long>> train = dataSplits[y];
                                for (long userId : train.keySet()) {
                                        if (!trainData.containsKey(userId)) {
                                                trainData.put(userId, new HashSet<Long>());
                                        }
                                        
                                        trainData.get(userId).addAll(train.get(userId));
                                }
                        }
                        recommender.train(trainData);
                
                        Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
                        Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
                
                        double threshold = getOptimalThreshold(trainPredictions, linkLikes);
                
                        Double[] metrics = getClassificationMetrics(predictions, linkLikes, threshold);
                        accuracies[x] = metrics[0];
                        precisions[x] = metrics[1];
                        recalls[x] = metrics[2];
                        f1s[x] = metrics[3];
                        
                        meanAccuracy += metrics[0];
                        meanPrecision += metrics[1];
                        meanRecall += metrics[2];
                        meanF1 += metrics[3];
                }
                
                meanAccuracy /= 10;
                meanPrecision /= 10;
                meanRecall /= 10;
                meanF1 /= 10;
                
                System.out.println("AVERAGES:");
                System.out.println("Accuracy: " + meanAccuracy);
                System.out.println("Precision: " + meanPrecision);
                System.out.println("Recall: " + meanRecall);
                System.out.println("F1: " + meanF1);
                
                //for (int x = 0; x)
        }
        
        public static void main(String[] args)
                throws Exception
        {
                LinkRecommenderMLJ mlj = new LinkRecommenderMLJ();
                Map<Long, Set<Long>>[] dataSplits = mlj.splitData();
                
                //type = Constants.SOCIAL;
                //new LinkRecommenderMLJ().run1(dataSplits);
        }
        
        public Map<Long, Set<Long>>[] getActiveData(Map<Long, Map<Long, Double>> friendships, int restriction)
                        throws SQLException
        {
                Statement statement = SQLUtil.getStatement();
                HashMap<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();
                Map<Long, Set<Long>> linkLikes = new HashMap<Long, Set<Long>>();
                        
                ResultSet result = statement.executeQuery("SELECT link_id, uid, rating FROM trackRecommendedLinks WHERE rating=2 OR rating=1");
                HashSet<Long> allLinks = new HashSet<Long>();
                        
                while (result.next()) {
                        long linkId = result.getLong("link_id");
                        long userId = result.getLong("uid");
                        int rating = result.getInt("rating");
                                
                        if (!trainData.containsKey(userId)) {
                                trainData.put(userId, new HashSet<Long>());
                        }
                        trainData.get(userId).add(linkId);
                                
                        if (rating == 1) {
                                if (!linkLikes.containsKey(linkId)) {
                                        linkLikes.put(linkId, new HashSet<Long>());
                                }
                                linkLikes.get(linkId).add(userId);
                        }
                                
                        allLinks.add(linkId);
                }
                        
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
                        
                Map<Long, Set<Long>>[] data = new Map[2];
                data[0] = trainData;
                data[1] = linkLikes;
                        
                return data;
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