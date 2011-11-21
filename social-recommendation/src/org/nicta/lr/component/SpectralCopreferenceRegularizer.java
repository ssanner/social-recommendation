package org.nicta.lr.component;

import java.util.Map;

import org.nicta.lr.util.Configuration;

public class SpectralCopreferenceRegularizer extends SocialCopreferenceRegularizer
{
	public double getValue(Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, Map<Long, Map<Long, Map<Long, Double>>> copreferences)
	{
		double error = 0;
		int count = 0;
		
		for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
				
			for (long uid1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);
				
				for (long uid2 : userCopreferences.keySet()) {
					double actual = userCopreferences.get(uid2);
					double predicted = predictedCopreferences.get(linkId).get(uid1).get(uid2);
	
					error += Math.pow(actual - predicted, 2);
					count++;
				}
			}
		}
		System.out.println("link copreference count: " + copreferences.size());
		System.out.println("total copreferences count: " + count);
		
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
		
					Double[] feature = new Double[user1.length];
					for (int i = 0; i < feature.length; i++) {
						feature[i] = user1[i] - user2[i];
					}
				
					double p = userCopreferences.get(uid2);
					double s = connections.get(linkId).get(uid1).get(uid2);
		
					double duu = 2 * feature[y] * feature[y] * userFeatureMatrix[x][y] * Vy[x];
				
					for (int z = 0; z < user1.length; z++) {
						if (z != y) {
							duu += feature[y] * feature[z] * userFeatureMatrix[x][z] * Vy[x];
							duu += feature[z] * feature[y] * userFeatureMatrix[x][z] * Vy[x];
						}
					}
					duu += 2 * user1Id[x] * feature[y] * Vy[x];
					duu -= 2 * user2Id[x] * feature[y] * Vy[x];
		
					derivative += p * s * duu;
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

				Double[] feature = new Double[user1.length];
				for (int i = 0; i < feature.length; i++) {
					feature[i] = user1[i] - user2[i];
				}
				
				double p = userCopreferences.get(uid2);
				double s = connections.get(linkId).get(userId).get(uid2);

				double duu = 0;

				for (int z = 0; z < feature.length; z++) {
					duu += feature[z] * userFeatureMatrix[k][z] * Vy[k];
				}

				duu += user2Column[k] * Vy[k];

				derivative += p * s * duu * -1;
			}
		}

		return derivative;
	}
	
	public double getDerivativeValueOverLinkAttribute(Double[][] userMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkFeatures,
														Map<Long, Map<Long, Map<Long, Double>>> copreferences, Map<Long, Map<Long, Map<Long, Double>>> connections, 
														int x, int y)
	{
		double derivative = 0;

		for (long linkId : copreferences.keySet()) {
			Double[] link = linkFeatures.get(linkId);
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);

			for (long uid1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);

				for (long uid2 : userCopreferences.keySet()) {
					Double[] user1 = userFeatures.get(uid1);
					Double[] user1Id = userIdColumns.get(uid1);
					Double[] user2 = userFeatures.get(uid2);
					Double[] user2Id = userIdColumns.get(uid2);

					Double[] feature = new Double[user1.length];
					for (int i = 0; i < feature.length; i++) {
						feature[i] = user1[i] - user2[i];
					}

					double p = userCopreferences.get(uid2);
					double s = connections.get(linkId).get(uid1).get(uid2);

					Double[] vector = new Double[user1Id.length];
					
					for (int i = 0; i < vector.length; i++) {
						vector[i] = 0.0;
						
						for (int z = 0; z < user1.length; z++) {
							vector[i] += feature[z] * userMatrix[i][z];
						}
						
						vector[i] += user1Id[i];
						vector[i] -= user2Id[i];
						
						vector[i] *= vector[i];
					}

					derivative += p * s * vector[x] * link[y];
				}
			}
		}

		return derivative;
	}
	
	public double getDerivativeValueOverLinkId(Double[][] userMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkFeatures,
												Map<Long, Map<Long, Map<Long, Double>>> copreferences, Map<Long, Map<Long, Map<Long, Double>>> connections, 
												long linkId, int k)
	{
		double derivative = 0;

		//for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
			
			for (long uid1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(uid1);
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Column = userIdColumns.get(uid1);
				
				for (long uid2 : userCopreferences.keySet()) {
					Double[] user2 = userFeatures.get(uid2);
					Double[] user2Column = userIdColumns.get(uid2);
	
					double p = userCopreferences.get(uid2);
					double s = connections.get(linkId).get(uid1).get(uid2);
	
					Double[] feature = new Double[user1.length];
					for (int i = 0; i < feature.length; i++) {
						feature[i] = user1[i] - user2[i];
					}
					
					Double[] vector = new Double[user1Column.length];
					
					for (int i = 0; i < vector.length; i++) {
						vector[i] = 0.0;
						
						for (int z = 0; z < user1.length; z++) {
							vector[i] += feature[z] * userMatrix[i][z];
						}
						
						vector[i] += user1Column[i];
						vector[i] -= user2Column[i];
						
						vector[i] *= vector[i];
					}
					
					derivative += p * s * vector[k];
				}
			}
		//}

		return derivative;
	}
	
	public double predictConnection(Double[][] userMatrix, Map<Long, Double[]> idColumns, Map<Long, Double[]> userFeatures,
									Double[][] linkMatrix, Map<Long, Double[]> linkIdColumns, Map<Long, Double[]> linkFeatures, 
									long i, long j, long y, int k)
	{		
		Double[] iFeature = userFeatures.get(i);
		Double[] iColumn = idColumns.get(i);
		Double[] jFeature = userFeatures.get(j);
		Double[] jColumn = idColumns.get(j);
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
		Double[] feature = new Double[iFeature.length];
		for (int x = 0; x < feature.length; x++) {
			feature[x] = iFeature[x] - jFeature[x];
		}
		
		for (int x = 0; x < k; x++) {
			xU[x] = 0.0;
			
			for (int z = 0; z < iFeature.length; z++) {
				xU[x] += feature[z] * userMatrix[x][z];
			}
			
			xU[x] += iColumn[x];
			xU[x] -= jColumn[x];
		}
		
		//xU * diag(Vy)
		for (int x = 0; x < xU.length; x++) {
			xU[x] *= Vy[x];
		}
		
		Double[] xUU = new Double[feature.length + 2];
		
		for (int x = 0; x < feature.length; x++) {
			xUU[x] = 0.0;
		
			for (int z = 0; z < xU.length; z++) {
				xUU[x] += xU[z] * userMatrix[z][x];
			}
		}
		
		int index = feature.length;
		xUU[index] = 0d;
		for (int z = 0; z < xU.length; z++) {
			xUU[index] += xU[z] * iColumn[z];
		}
		
		index += 1;
		xUU[index] = 0d;
		for (int z = 0; z < xU.length; z++) {
			xUU[index] += xU[z] * jColumn[z];
		}
		
		double connection = 0;
		
		for (int x = 0; x < jFeature.length; x++) {
			connection += xUU[x] * feature[x];
		}
		connection += xUU[feature.length];
		connection -= xUU[feature.length + 1];
		
		return connection;
	}
}
