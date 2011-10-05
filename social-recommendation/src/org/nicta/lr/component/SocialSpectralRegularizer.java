package org.nicta.lr.component;

import java.util.Map;

public class SocialSpectralRegularizer extends SocialRegularizer
{
	public double getValue(Map<Long, Map<Long, Double>> connections, Map<Long, Map<Long, Double>> friendships)
	{
		Object[] keys = connections.keySet().toArray();
		double error = 0;
		
		for (int a = 0; a < keys.length-1; a++) {
			Long i = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long j = (Long)keys[b];
				
				double connection = getFriendConnection(i, j, friendships);
				double predictConnection = connections.get(i).containsKey(j) ? connections.get(i).get(j) : connections.get(j).get(i);
				
				error += connection * predictConnection;
			}
		}
		
		return error / 2;
	}
	
	public double getDerivativeValueOverAttribute(Double[][] userFeatureMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns,
			Map<Long, Map<Long, Double>> connections, Map<Long, Map<Long, Double>> friendships, 
			int x, int y)
	{
		Object[] keys = connections.keySet().toArray();
		double derivative = 0;
		
		for (int a = 0; a < keys.length-1; a++) {
			Long uid1 = (Long)keys[a];
		
			for (int b = a+1; b < keys.length; b++) {
				Long uid2 = (Long)keys[b];
		
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
		
				Double[] feature = new Double[user1.length];
				for (int i = 0; i < feature.length; i++) {
					feature[i] = user1[i] - user2[i];
				}
				
				double c = getFriendConnection(uid1, uid2, friendships);
				double p = connections.get(uid1).containsKey(uid2) ? connections.get(uid1).get(uid2) : connections.get(uid2).get(uid1);
		
				double duu = 2 * feature[y] * feature[y] * userFeatureMatrix[x][y];
				
				for (int z = 0; z < user1.length; z++) {
					if (z != y) {
						duu += feature[y] * feature[z] * userFeatureMatrix[x][z];
						duu += feature[z] * feature[y] * userFeatureMatrix[x][z];
					}
				}
				duu += 2 * user1Id[x] * feature[y];
				duu -= 2 * user2Id[x] * feature[y];
		
				derivative += c * p * duu;
			}
		}
		
		return derivative;
	}
	
	public double getDerivativeValueOverId(Double[][] userFeatureMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns,
			Map<Long, Map<Long, Double>> connections, Map<Long, Map<Long, Double>> friendships,
			long userId, int k)
	{
		double error = 0;
		Double[] user1 = userFeatures.get(userId);
		Double[] idColumn = userIdColumns.get(userId);
		
		for (long uid2 : connections.keySet()) {
			if (userId == uid2) continue;	
	
			Double[] user2 = userFeatures.get(uid2);
	
			Double[] feature = new Double[user2.length];
			for (int x = 0; x < feature.length; x++) {
				feature[x] = user1[x] - user2[x];
			}
			
			Double[] user2Column = userIdColumns.get(uid2);
	
			double c = getFriendConnection(userId, uid2, friendships);
			double p = connections.get(userId).containsKey(uid2) ? connections.get(userId).get(uid2) : connections.get(uid2).get(userId);
	
			double duu = 2 * idColumn[k];
			
			for (int z = 0; z < user1.length; z++) {
				duu += 2 * feature[z] *  userFeatureMatrix[k][z];
			}
			
			duu -= 2 * user2Column[k];
			
			error += c * p * duu;
		}
		
		return error;
	}
	
	public double predictConnection(Double[][] userMatrix, 
			Map<Long, Double[]> idColumns,
			Map<Long, Double[]> userFeatures,
			long i, long j, int k)
	{		
		Double[] iFeature = userFeatures.get(i);
		Double[] iColumn = idColumns.get(i);
		Double[] jFeature = userFeatures.get(j);
		Double[] jColumn = idColumns.get(j);
		
		Double[] xU = new Double[k];
		
		Double[] feature = new Double[iFeature.length];
		for (int x = 0; x < feature.length; x++) {
			feature[x] = iFeature[x] - jFeature[x];
		}
		
		for (int x = 0; x < k; x++) {
			xU[x] = 0.0;
			
			for (int y = 0; y < iFeature.length; y++) {
				xU[x] += feature[y] * userMatrix[x][y];
			}
			
			xU[x] += iColumn[x];
			xU[x] -= jColumn[x];
		}
		
		Double[] xUU = new Double[feature.length + 2];
		
		for (int x = 0; x < feature.length; x++) {
			xUU[x] = 0.0;
		
			for (int y = 0; y < xU.length; y++) {
				xUU[x] += xU[y] * userMatrix[y][x];
			}
		}
		
		int index = feature.length;
		xUU[index] = 0d;
		for (int y = 0; y < xU.length; y++) {
			xUU[index] += xU[y] * iColumn[y];
		}
		
		index += 1;
		xUU[index] = 0d;
		for (int y = 0; y < xU.length; y++) {
			xUU[index] += xU[y] * jColumn[y];
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
