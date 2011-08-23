package org.nicta.lr.recommender;

import java.util.Map;
import java.util.HashMap;

public class ConnectionThread extends Thread
{
	int x;
	Object[] users;
	Double[][] userMatrix;
	Map<Long, Double[]> idColumns;
	Map<Long, Double[]> userFeatures;
	Map<Long, Map<Long, Double>> connections;
	MFRecommender backpointer;
	
	public ConnectionThread(int x, Object[] users, Double[][] userMatrix, Map<Long, Double[]> idColumns,
								Map<Long, Double[]> userFeatures, Map<Long, Map<Long, Double>> connections,
								MFRecommender backpointer)
	{
		this.x = x;
		this.users = users;
		this.userMatrix = userMatrix;
		this.idColumns = idColumns;
		this.userFeatures = userFeatures;
		this.connections = connections;
		this.backpointer = backpointer;
	}
	
	public void run()
	{
		Long user1 = (Long)users[x];
		HashMap<Long, Double> conn = new HashMap<Long, Double>();
		for (int y = x+1; y < users.length; y++) {
			Long user2 = (Long)users[y];
			conn.put(user2, backpointer.predictConnection(userMatrix, idColumns, userFeatures, user1, user2));	
		}
		connections.put((Long)users[x], conn);
	}
}
