package org.nicta.fbproject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

public class FBMethods 
{
	public static Connection getSqlConnection()
		throws SQLException
	{
		try {
			Class.forName ("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException ce) {
			System.out.println("Shit");
			System.exit(1);
		}
		
		return DriverManager.getConnection(FBConstants.DB_STRING);
	}
	
	public static Double[][] getPrior(int featureCount)
	{
		Random random = new Random();
		
		Double[][] prior = new Double[FBConstants.K][featureCount];
		
		for (int x = 0; x < FBConstants.K; x++) {
			for (int y = 0; y < featureCount; y++) {
				prior[x][y] = random.nextGaussian();
			}
		}
		
		return prior;
	}
	
	public static HashMap<Long, Double[]> getMatrixIdColumns(Set<Long> ids)
	{
		Random random = new Random();
		
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		for (long id : ids) {
			Double[] column = new Double[FBConstants.K];
			
			for (int x = 0; x < column.length; x++) {
				column[x] = random.nextGaussian();
			}
			
			idColumns.put(id, column);
		}
		
		return idColumns;
	}
	
	public static double dot(Double[] vec1, Double[] vec2)
	{
		double prod = 0;
		
		for (int x = 0; x < vec1.length; x++) {
			prod += vec1[x] * vec2[x];
		}
		
		return prod;
	}
}
