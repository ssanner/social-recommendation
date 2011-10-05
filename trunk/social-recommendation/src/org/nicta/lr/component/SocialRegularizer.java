package org.nicta.lr.component;

import java.util.Map;

public class SocialRegularizer
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
				error += Math.pow(connection - predictConnection, 2);
			}
		}
		
		return error / 2;
	}
	
	public double getDerivativeValueOverAttribute(Double[][] userFeatureMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns,
														Map<Long, Map<Long, Double>> connections, Map<Long, Map<Long, Double>> friendships, 
														int x, int y)
	{
		double derivative = 0;
		
		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long uid1 = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long uid2 = (Long)keys[b];
				
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
				
				double c = getFriendConnection(uid1, uid2, friendships);
				double p = connections.get(uid1).containsKey(uid2) ? connections.get(uid1).get(uid2) : connections.get(uid2).get(uid1);
				
				double duu = 2 * user1[y] * user2[y] * userFeatureMatrix[x][y];
				for (int z = 0; z < user1.length; z++) {
					if (z != y) {
						duu += user1[y] * user2[z] * userFeatureMatrix[x][z];
						duu += user1[z] * user2[y] * userFeatureMatrix[x][z];
					}
				}
				duu += user1Id[x] * user2[y];
				duu += user2Id[x] * user1[y];
				
				derivative += (c - p) * duu * -1;
			}
		}
		
		return derivative;
	}
	
	public double getDerivativeValueOverId(Double[][] userFeatureMatrix, Map<Long, Double[]> userFeatures, Map<Long, Double[]> userIdColumns,
											Map<Long, Map<Long, Double>> connections, Map<Long, Map<Long, Double>> friendships,
											long userId, int k)
	{
		Double[] user1 = userFeatures.get(userId);
		double derivative = 0;
		
		for (long uid2 : connections.keySet()) {
			if (userId == uid2) continue;	
			
			Double[] user2 = userFeatures.get(uid2);
			
			Double[] user2Column = userIdColumns.get(uid2);
			
			double c = getFriendConnection(userId, uid2, friendships);
			double p = connections.get(userId).containsKey(uid2) ? connections.get(userId).get(uid2) : connections.get(uid2).get(userId);
			
			double duu = 0;
			
			for (int z = 0; z < user1.length; z++) {
				duu += user2[z] *  userFeatureMatrix[k][z];
			}
			
			duu += user2Column[k];
			
			derivative += (c - p) * duu * -1;
		}
		
		return derivative;
	}

	public double getFriendConnection(Long uid1, Long uid2, Map<Long, Map<Long, Double>> friendships)
	{
		if ((friendships.containsKey(uid1) && friendships.get(uid1).containsKey(uid2))) {
			return friendships.get(uid1).get(uid2);
		}
		
		return 0;
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
		
		for (int x = 0; x < k; x++) {
			xU[x] = 0.0;
			
			for (int y = 0; y < iFeature.length; y++) {
				xU[x] += iFeature[y] * userMatrix[x][y];
			}
			
			xU[x] += iColumn[x];
		}
		
		Double[] xUU = new Double[iFeature.length + 1];
		
		for (int x = 0; x < iFeature.length; x++) {
			xUU[x] = 0.0;
		
			for (int y = 0; y < xU.length; y++) {
				xUU[x] += xU[y] * userMatrix[y][x];
			}
		}
		
		int index = iFeature.length;
		xUU[index] = 0d;
		
		for (int y = 0; y < xU.length; y++) {
			xUU[index] += xU[y] * jColumn[y];
		}
		
		double connection = 0;
		
		for (int x = 0; x < jFeature.length; x++) {
			connection += xUU[x] * jFeature[x];
		}
		connection += xUU[jFeature.length];
		
		return connection;
	}
}
