package org.nicta.lr.component;

import java.util.Map;

public class L2Regularizer
{
	public double getValue(Double[][] matrix)
	{
		double value = 0;
		
		for (int x = 0; x < matrix.length; x++) {
			for (int y = 0; y < matrix[x].length; y++) {
				value += Math.pow(matrix[x][y], 2);
			}
		}
		
		return value / 2;
	}
	
	public double getValue(Map<Long, Double[]> matrix)
	{
		double value = 0;
		
		for (long key : matrix.keySet()) {
			Double[] vector = matrix.get(key);
			
			for (int x = 0; x < vector.length; x++) {
				value += Math.pow(vector[x], 2);
			}
		}
		
		return value / 2;
	}
	
	public double getValue(Double[] vector)
	{
		double value = 0;
			
		for (int x = 0; x < vector.length; x++) {
			value += Math.pow(vector[x], 2);
		}
		
		return value / 2;
	}
	
	public double getDerivative(Double[][] matrix, int x, int y)
	{
		return matrix[x][y];
	}
	
	public double getDerivative(Double[] vector, int k)
	{
		return vector[k];
	}
}
