package org.nicta.lr.component;

import java.util.Map;

import org.nicta.lr.util.Configuration;

public class SocialCopreferenceRegularizer
{	
	public double getValue(Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, Map<Long, Map<Long, Map<Long, Double>>> copreferences)
	{
		double error = 0;
		
		for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
				
			for (long uid1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);
				
				for (long uid2 : userCopreferences.keySet()) {
					double actual = userCopreferences.get(uid2);
					double predicted = predictedCopreferences.get(linkId).get(uid1).get(uid2);
	
					error += Math.pow(actual - predicted, 2);
				}
			}
		}
		
		return error / 2;
	}
	
	public double getDerivativeValueOverUserAttribute(Double[][] userFeatureMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkTraits,
														Map<Long, Map<Long, Map<Long, Double>>> copreferences, Map<Long, Map<Long, Map<Long, Double>>> connections, 
														int x, int y)
	{
		double derivative = 0;

		for (long linkId : copreferences.keySet()) {
			Double[] Vy = linkTraits.get(linkId);
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
				
			for (long uid1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);
				
				for (long uid2 : userCopreferences.keySet()) {
					Double[] user1 = userFeatures.get(uid1);
					Double[] user1Id = userIdColumns.get(uid1);
					Double[] user2 = userFeatures.get(uid2);
					Double[] user2Id = userIdColumns.get(uid2);
	
					double p = userCopreferences.get(uid2);
					double c = connections.get(linkId).get(uid1).get(uid2);
	
					double duu = 2 * user1[y] * user2[y] * userFeatureMatrix[x][y] * Vy[x];
					for (int z = 0; z < user1.length; z++) {
						if (z != y) {
							duu += user1[y] * user2[z] * userFeatureMatrix[x][z] * Vy[x];
							duu += user1[z] * user2[y] * userFeatureMatrix[x][z] * Vy[x];
						}
					}
					duu += user1Id[x] * user2[y] * Vy[x];
					duu += user2Id[x] * user1[y] * Vy[x];
	
					derivative += (p - c) * duu * -1;
				}
			}
		}
		
		return derivative;
	}
	
	public double getDerivativeValueOverUserId(Double[][] userFeatureMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkTraits,
											Map<Long, Map<Long, Map<Long, Double>>> copreferences, Map<Long, Map<Long, Map<Long, Double>>> connections, 
											long userId, int k)
	{
		Double[] user1 = userFeatures.get(userId);
		double derivative = 0;

		for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
			Map<Long, Double> userCopreferences = linkCopreferences.get(userId);
			if (userCopreferences == null) continue;
			
			Double[] Vy = linkTraits.get(linkId);
			
			for (long uid2 : userCopreferences.keySet()) {
				Double[] user2 = userFeatures.get(uid2);

				Double[] user2Column = userIdColumns.get(uid2);

				double p = userCopreferences.get(uid2);
				double c = connections.get(linkId).get(userId).get(uid2);

				double duu = 0;

				for (int z = 0; z < user1.length; z++) {
					duu += user2[z] * userFeatureMatrix[k][z] * Vy[k];
				}

				duu += user2Column[k] * Vy[k];

				derivative += (p - c) * duu * -1;
			}
		}

		return derivative;
	}
	
	public double getDerivativeValueOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Double[]> linkFeatures,
														Map<Long, Map<Long, Map<Long, Double>>> copreferences, Map<Long, Map<Long, Map<Long, Double>>> connections,
														int x, int y)
	{
		double derivative = 0;

		for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
			Double[] link = linkFeatures.get(linkId);
			
			for (long uid1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);
				Double[] user1Trait = userTraits.get(uid1);
				
				for (long uid2 : userCopreferences.keySet()) {
					Double[] user2Trait = userTraits.get(uid2);

					double p = userCopreferences.get(uid2);
					double c = connections.get(linkId).get(uid1).get(uid2);

					Double[] hadamard = new Double[user1Trait.length];
					for (int i = 0; i < hadamard.length; i++) {
						hadamard[i] = user1Trait[i] * user2Trait[i];
					}
					
					derivative += (p - c) * hadamard[x] * link[y] * -1;
				}
			}
		}

		return derivative;
	}
	
	public double getDerivativeValueOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Map<Long, Double>>> copreferences, Map<Long, Map<Long, Map<Long, Double>>> connections, 
												long linkId, int k)
	{
		//System.out.println("Link id derivative");
		//System.out.println("cop: " + copreferences.size());
		//System.out.println("con: " + connections.size());
		
		double derivative = 0;

		//for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
			
			for (long uid1 : linkCopreferences.keySet()) {
				Double[] user1Trait = userTraits.get(uid1);
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);
				
				if (userCopreferences == null) continue;
	
				for (long uid2 : userCopreferences.keySet()) {
					Double[] user2Trait = userTraits.get(uid2);
					
					double p = userCopreferences.get(uid2);
					double s = connections.get(linkId).get(uid1).get(uid2);
	
					Double[] hadamard = new Double[user1Trait.length];
					for (int i = 0; i < hadamard.length; i++) {
						hadamard[i] = user1Trait[i] * user2Trait[i];
					}
	
					derivative += (p - s) * hadamard[k] * -1;
				}
			}
		//}

		return derivative;
	}
	
	public double predictConnection(Double[][] userMatrix, Map<Long, Double[]> userIdColumns, Map<Long, Double[]> userFeatures,
									Double[][] linkMatrix, Map<Long, Double[]> linkIdColumns, Map<Long, Double[]> linkFeatures, 
									long i, long j, long y, int k)
	{
		Double[] iFeature = userFeatures.get(i);
		Double[] iColumn = userIdColumns.get(i);
		Double[] jFeature = userFeatures.get(j);
		Double[] jColumn = userIdColumns.get(j);
		Double[] yFeature = linkFeatures.get(y);
		Double[] yIdColumn = linkIdColumns.get(y);
		
		Double[] Vy = new Double[k];
		for (int x = 0; x < k; x++) {
			Vy[x] = 0.0;

			for (int z = 0; z < Configuration.LINK_FEATURE_COUNT; z++) {
				Vy[x] += linkMatrix[x][z] * yFeature[z];
			}
			
			Vy[x] += yIdColumn[x];
		}
		
		
		Double[] xU = new Double[k];
		for (int x = 0; x < k; x++) {
			xU[x] = 0.0;
			
			for (int z = 0; z < iFeature.length; z++) {
				xU[x] += iFeature[z] * userMatrix[x][z];
			}
			
			xU[x] += iColumn[x];
		}
		
		//xU * diag(Vy)
		for (int x = 0; x < xU.length; x++) {
			xU[x] *= Vy[x];
		}
		
		Double[] xUU = new Double[iFeature.length + 1];
		
		for (int x = 0; x < iFeature.length; x++) {
			xUU[x] = 0.0;
		
			for (int z = 0; z < xU.length; z++) {
				xUU[x] += xU[z] * userMatrix[z][x];
			}
		}
		
		int index = iFeature.length;
		xUU[index] = 0d;
		
		for (int z = 0; z < xU.length; z++) {
			xUU[index] += xU[z] * jColumn[z];
		}
		
		double connection = 0;
		
		for (int x = 0; x < jFeature.length; x++) {
			connection += xUU[x] * jFeature[x];
		}
		connection += xUU[jFeature.length];
		
		return connection;
	}
	
	
}
