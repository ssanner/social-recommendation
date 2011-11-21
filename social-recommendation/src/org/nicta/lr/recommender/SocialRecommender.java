package org.nicta.lr.recommender;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;

import org.nicta.lr.component.Objective;
import org.nicta.lr.component.SocialSpectralRegularizer;
import org.nicta.lr.component.SocialRegularizer;
import org.nicta.lr.component.L2Regularizer;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.Configuration;
import org.nicta.lr.util.UserUtil;

public class SocialRecommender extends MFRecommender
{	
	double beta = 1.0E-3;
	
	SocialRegularizer socialRegularizer;
	Objective objective;
	L2Regularizer l2;
	
	public SocialRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends, String type)
	{
		super(linkLikes, userFeatures, linkFeatures, friends);
		
		K = 5;
		lambda = 100;
		
		this.type = type;
		friendships = friends;
		
		if (Configuration.DEPLOYMENT_TYPE == Constants.TEST || Configuration.INITIALIZE) {
			initializePriors(userFeatures.keySet(), linkFeatures.keySet());
		}
		else if (Configuration.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			try {
				loadData();
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		if (Constants.SOCIAL.equals(type) || Constants.HYBRID_SOCIAL.equals(type)) {
			System.out.println("Type: " + type);
			socialRegularizer = new SocialRegularizer();
			beta = 0.001;
		}
		else if (Constants.SPECTRAL.equals(type) || Constants.HYBRID_SPECTRAL.equals(type)){
			System.out.println("Type: " + type);
			socialRegularizer = new SocialSpectralRegularizer();
			beta = .001;
		}
		else {
			System.out.println("Type: Non social");
		}
		
		objective = new Objective();
		l2 = new L2Regularizer();
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		try {
			int linkCount = 0;
			
			//Remove users that do not have data yet.
			HashSet<Long> remove = new HashSet<Long>();
			for (long trainId : trainSamples.keySet()) {
				if (! userFeatures.containsKey(trainId)) {
					remove.add(trainId);
				}
				else {
					linkCount += trainSamples.get(trainId).size();
				}
			}
		
			for (long userId : remove) {
				trainSamples.remove(userId);
			}
			
			friendConnections = UserUtil.getFriendInteractionMeasure(trainSamples.keySet());
			//friendConnections = friendships;
			
			//checkDerivative(trainSamples);
			minimizeByThreadedLBFGS(trainSamples);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections)
	{
		double error = 0;
	
		if (socialRegularizer != null) {
			error += socialRegularizer.getValue(connections, friendConnections);
			error *= beta;
		}
		
		error += objective.getValue(predictions, linkLikes);

		//Get User and Link norms for regularisation
		double userNorm = l2.getValue(userFeatureMatrix) + l2.getValue(userIdColumns);
		double linkNorm = l2.getValue(linkFeatureMatrix) + l2.getValue(linkIdColumns);
		userNorm *= lambda;
		linkNorm *= lambda;

		error += userNorm + linkNorm;

		return error;
	}
	
	public double getErrorDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
														Map<Long, Map<Long, Double>> connections, int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * lambda;
		
		if (socialRegularizer != null) {
			double socDerivative = socialRegularizer.getDerivativeValueOverAttribute(userFeatureMatrix, userFeatures, userIdColumns, connections, friendConnections, x, y);
			errorDerivative += beta * socDerivative;
		}
		
		errorDerivative += objective.getDerivativeOverUserAttribute(linkTraits, userFeatures, predictions, linkLikes, x, y);

		return errorDerivative;
	}
	
	
	public double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
												Map<Long, Map<Long, Double>> connections, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * lambda;

		if (socialRegularizer != null) {
			double socDerivative = socialRegularizer.getDerivativeValueOverId(userFeatureMatrix, userFeatures, userIdColumns, connections, friendConnections, userId, k);
			errorDerivative += beta * socDerivative;
		}
		
		errorDerivative += objective.getErrorDerivativeOverUserId(linkTraits, linkLikes, predictions, k, userId);
		
		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, int y)
	{
		double errorDerivative = linkFeatureMatrix[x][y] * lambda;
		errorDerivative += objective.getErrorDerivativeOverLinkAttribute(userTraits, linkFeatures, linkLikes, predictions, x, y);

		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * lambda;
		errorDerivative += objective.getErrorDerivativeOverLinkId(userTraits, linkLikes, predictions, x, linkId);
		
		return errorDerivative;
	}
	
	
	/**
	 * Convenience method for getting 'friendship' values.
	 * 
	 * Friendship values should be bounded between 0 and 1. Current assumption is values should be equal bidirectionally.
	 * 
	 * @param uid1
	 * @param uid2
	 * @param friendships
	 * @return
	 */
	public double getFriendConnection(Long uid1, Long uid2, Map<Long, Map<Long, Double>> friendships)
	{
		if ((friendships.containsKey(uid1) && friendships.get(uid1).containsKey(uid2))) {
			return friendships.get(uid1).get(uid2);
		}
		
		return 0;
	}	
	
	public void setBeta(double b)
	{
		beta = b;
	}
	
	public double predictConnection(Double[][] userMatrix, 
			Map<Long, Double[]> idColumns,
			Map<Long, Double[]> userFeatures,
			long i, long j)
	{
		return socialRegularizer != null ? socialRegularizer.predictConnection(userMatrix, idColumns, userFeatures, i, j, K) : 0;
	}	
}
