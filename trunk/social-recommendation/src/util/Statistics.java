package util;

import java.util.ArrayList;

public class Statistics {

	public static double Min(ArrayList<Double> l) {
		double min = Double.MAX_VALUE;
		for (int i = 0; i < l.size(); i++)
			min = Math.min(min, l.get(i));
		return min;
	}

	public static double Max(ArrayList<Double> l) {
		double max = -Double.MAX_VALUE;
		for (int i = 0; i < l.size(); i++)
			max = Math.max(max, l.get(i));
		return max;
	}

	public static double Avg(ArrayList<Double> l) {
		double accum = 0d;
		for (int i = 0; i < l.size(); i++) {
			accum += l.get(i);
		}
		return accum / l.size();
	}
	
	public static double StdError95(ArrayList<Double> l) {
		if (l.size() <= 1)
			return Double.NaN;
			
		double avg = Avg(l);
		double ssq = 0d;
		int sz = l.size();
		for (int i = 0; i < sz; i++) {
			double factor = (l.get(i) - avg);
			ssq += factor * factor;
		}	
		double stdev = Math.sqrt(ssq / (sz - 1));
		return getTCoef95(sz) * (stdev / Math.sqrt(sz));
	}
	
	public static double getTCoef95(int size) {
		int dof = size - 1;
		if (dof <= 30)
			return TCOEF[dof];
		else if (dof <= 120)
			return 2.0d; // small approx in 3rd significant digit
		else
			return 1.96d;
	}
	
	public static final double[] TCOEF = { 
		Double.NaN, // size = 1, dof = 0
		12.71,
		4.303,
		3.182,
		2.776,
		2.571,
		2.447,
		2.365,
		2.306,
		2.262,
		2.228,
		2.201,
		2.179,
		2.16,
		2.145,
		2.131,
		2.12,
		2.11,
		2.101,
		2.093,
		2.086,
		2.08,
		2.074,
		2.069,
		2.064,
		2.06,
		2.056,
		2.052,
		2.048,
		2.045,
		2.042 }; // 30 dof
}
