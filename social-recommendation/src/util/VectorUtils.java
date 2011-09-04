/** Sparse (HashMap) and dense (double[]) vector utilities.
 *   
 * @author Scott Sanner (ssanner@gmail.com)
 */

package util;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class VectorUtils {

	// Compact Vectors
	public static String GetString(Double[] v) {
		if (v == null)
			return "null";
		StringBuilder sb = new StringBuilder("[ ");
		for (double d : v) {
			sb.append(d + " ");
		}
		sb.append("]");
		return sb.toString();
	}
	
	public static double[] ElementWiseSubtract(double[] d1, double[] d2) {
		double[] result = new double[d1.length];
		for (int i = 0; i < d1.length; i++)
			result[i] = d1[i] - d2[i];
		return result;
	}

	public static double[] ElementWiseMultiply(double[] d1, double[] d2) {
		double[] result = new double[d1.length];
		for (int i = 0; i < d1.length; i++)
			result[i] = d1[i] * d2[i];
		return result;
	}

	public static double L1Norm(double[] d) {
		double accum = 0d;
		for (int k = 0; k < d.length; k++) {
			accum += Math.abs(d[k]);
		}
		return accum;
	}

	public static double[] NormalizeL1(double[] d) {
		double accum = 0d;
		for (int k = 0; k < d.length; k++) {
			accum += Math.abs(d[k]);
		}
		if (accum > 0)
			return ScalarMultiply(d, 1d/accum);
		else
			return d;
	}

	public static double[] NormalizeL2(double[] d) {
		double accum = 0d;
		for (int k = 0; k < d.length; k++) {
			accum += d[k]*d[k];
		}
		accum = Math.sqrt(accum);
		if (accum > 0)
			return ScalarMultiply(d, 1d/accum);
		else
			return d;
	}
	
	public static double[] Sum(double[] d1, double[] d2) {
		if (d1.length != d2.length) {
			System.out.println("ERROR: Vector Sum mismatch: " + d1.length + " vs " + d2.length);
			new Exception().printStackTrace();
			System.exit(1);
		}
		double[] sum = new double[d1.length];
		for (int k = 0; k < d1.length; k++) {
			sum[k] += d1[k] + d2[k];
		}
		return sum;
	}

	public static double[] ScalarMultiply(double[] d, double val) {
		double[] new_d = new double[d.length];
		for (int k = 0; k < d.length; k++) {
			new_d[k] += d[k]*val;
		}
		return new_d;
	}
	
	public static double DotProduct(double[] d1, double[] d2) {
		if (d1.length != d2.length) {
			System.out.println("ERROR: Vector Sum mismatch: " + d1.length + " vs " + d2.length);
			new Exception().printStackTrace();
			System.exit(1);
		}
		double dot_product = 0d;
		for (int k = 0; k < d1.length; k++) {
			dot_product += d1[k]*d2[k];
		}
		return dot_product;
	}

	public static double WeightedDotProduct(double[] d1, double[] d2, double[] weight) {
		double dot_product = 0d;
		for (int k = 0; k < d1.length; k++) {
			dot_product += d1[k]*d2[k]*weight[k];
		}
		return dot_product;
	}

	public static double CosSim(double[] d1, double[] d2) {
		d1 = NormalizeL2(d1);
		d2 = NormalizeL2(d2);
		return DotProduct(d1, d2);
	}
	
	public static double WeightedCosSim(double[] d1, double[] d2, double[] weight) {
		d1 = NormalizeL2(d1);
		d2 = NormalizeL2(d2);
		weight = NormalizeL1(weight); // L1 b/c it is a weighting
		return WeightedDotProduct(d1, d2, weight);
	}

	// Sparse Vectors
	
	public static Map<Object,Double> Copy(Map<Object,Double> m) {
		Map<Object,Double> ret = new HashMap<Object,Double>();
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			ret.put(e.getKey(), e.getValue());
		}
		return ret;
	}

	public static Map<Object,Double> ConvertToBoolean(Map<Object,Double> m) {
		Map<Object,Double> ret = new HashMap<Object,Double>();
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			ret.put(e.getKey(), e.getValue() == 0d ? 0d : 1d);
		}
		return ret;
	}
	
	// Makes a new vector
	public static Map<Object,Double> ScalarMultiply(Map<Object,Double> m, double val) {
		Map<Object,Double> ret = new HashMap<Object,Double>();
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			Double value = e.getValue()*val;
			ret.put(e.getKey(), value);
		}
		return ret;
	}
	
	// Elementwise multiply where we multiply each entry in m
	// by the corresponding vector value in weight (hence weight
	// should contain a superset of the keys in m)
	public static Map<Object,Double> ElementWiseMultiply(
			Map<Object,Double> m, Map<Object,Double> weight) {
		
		Map<Object,Double> ret = new HashMap<Object,Double>();
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			Object key = e.getKey();
			Double w = weight.get(key);
			if (w == null)
				System.err.println("No entry for '" + key + 
						"' in VectorUtils.ElementWiseMultiply: " + m + " .* " + weight);
			Double value = e.getValue()*w;
			ret.put(key, value);
		}
		return ret;
	}
	
	// Vector should be non-negative
	public static double L1Norm(Map<Object,Double> m) {
		double accum = 0d;
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			accum += Math.abs(e.getValue());
		}
		return accum;
	}

	public static Map<Object,Double> NormalizeL1(Map<Object,Double> m) {
		double accum = 0d;
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			accum += Math.abs(e.getValue());
		}
		if (accum > 0)
			return ScalarMultiply(m, 1d/accum);
		else
			return m;
	}
	
	public static Map<Object,Double> NormalizeL2(Map<Object,Double> m) {
		double accum = 0d;
		for (Map.Entry<Object, Double> e : m.entrySet()) {
			accum += e.getValue() * e.getValue();
		}
		accum = Math.sqrt(accum);
		if (accum > 0)
			return ScalarMultiply(m, 1d/accum);
		else
			return m;
	}
	
	public static Map<Object,Double> Sum(Map<Object,Double> m1, Map<Object,Double> m2) {

		Map<Object,Double> ret = new HashMap<Object,Double>();
		
		Set<Object> all_keys = new HashSet<Object>();
		all_keys.addAll(m1.keySet());
		all_keys.addAll(m2.keySet());
		for (Object key : all_keys) {
			Double d1 = m1.get(key);
			Double d2 = m2.get(key);
			if (d1 == null)
				ret.put(key, d2);
			else if (d2 == null)
				ret.put(key, d1);
			else
				ret.put(key, d1 + d2);
		}
		
		return ret;
	}
	
	// Return the dot product of two feature vectors
	public static double DotProduct(Map<Object,Double> m1, Map<Object,Double> m2) {
		if (m1.size() > m2.size()) {
			Map swap = m1;
			m1 = m2;
			m2 = swap;
		}
		double accum = 0d;
		for (Object key : m1.keySet())
			if (m2.containsKey(key))
				accum += m1.get(key)*m2.get(key);
		return accum;
	}

	// Return the weighted dot product of two feature vectors
	public static double WeightedDotProduct(Map<Object,Double> m1, 
			Map<Object,Double> m2, Map<Object,Double> weight) {
		if (m1.size() > m2.size()) {
			Map swap = m1;
			m1 = m2;
			m2 = swap;
		}
		double accum = 0d;
		for (Object key : m1.keySet()) {
			Double w = weight.get(key);
			if (w == null)
				w = 1d;
			if (m2.containsKey(key))
				accum += m1.get(key)*m2.get(key)*w;
		}
		return accum;
	}

	public static double CosSim(Map<Object,Double> m1, Map<Object,Double> m2) {
		m1 = NormalizeL2(m1);
		m2 = NormalizeL2(m2);
		return DotProduct(m1, m2);
	}
	
	public static double WeightedCosSim(Map<Object,Double> m1, 
			Map<Object,Double> m2, Map<Object,Double> weight) {
		m1 = NormalizeL2(m1);
		m2 = NormalizeL2(m2);
		weight = NormalizeL1(weight); // L1 b/c it is a weighting
		return WeightedDotProduct(m1, m2, weight);
	}
}
