package org.nicta.lr.recommender;

import java.util.Set;
import java.util.Map;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.Configuration;
import org.nicta.lr.component.Objective;
import org.nicta.lr.component.L2Regularizer;

public class FeatureRecommender extends MFRecommender
{
	Objective objective = new Objective();
	L2Regularizer l2 = new L2Regularizer();
	
	public FeatureRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures)
	{
		super(linkLikes, userFeatures, linkFeatures, null);
		
		K = 5;
		lambda = 100;
		type = "feature";
		
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
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		//minimizeByThreadedAlternatingLBFGS(trainSamples);
		minimizeByThreadedLBFGS(trainSamples);
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
							Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
							Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections)
	{
		double error = 0;
		
		//Get the square error
		error += objective.getValue(predictions, linkLikes);
		
		//Get User and Movie norms for regularisation
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
		errorDerivative += objective.getDerivativeOverUserAttribute(linkTraits, userFeatures, predictions, linkLikes, x, y);
		
		return errorDerivative;
	}

	public double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
												Map<Long, Map<Long, Double>> connections, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * lambda;
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
	
	public double predictConnection(Double[][] userMatrix, 
			Map<Long, Double[]> idColumns,
			Map<Long, Double[]> userFeatures,
			long i, long j)
	{
		return 0;
	}
}
