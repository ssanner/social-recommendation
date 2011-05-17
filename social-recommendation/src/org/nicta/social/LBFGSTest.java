package org.nicta.social;

public class LBFGSTest 
{
	//f(x)=x4-3x3+2 , with derivative f'(x)=4x3-9x2
	public static void main(String[] args)
		throws Exception
	{
		double xVal = 6;// # The algorithm starts at x=6
		double eps = 0.01;// # step size
		double precision = 0.00001;
	 
		int iteration = 0;
		double error = f(xVal);
		boolean go = true;
		while (go)/* > precision)*/ {
			iteration++;
			
			xVal = xVal - eps * g(xVal);
			double new_error = f(xVal);
			
			if (new_error < error) {
				error = new_error;
			}
			else {
				go = false;
			}
		}
		
		System.out.println("Local minimum occurs at " + xVal + " " + iteration);
		
		/*
		lbfgs(int n, int m, double[] x, double f, double[] g,
                boolean diagco, double[] diag, int[] iprint, double eps,
                double xtol, int[] iflag)
        */
		
		
		int[] iprint = {1,3};
		int[] iflag = {0};
		double[] diag = {1};
		double[] x = {6};
		double[] g = {g(x[0])};
			
		iteration = 0;
		go = true;
		
		while (go) {
			System.out.println("Iteration: " + (++iteration) + " " + x[0]);
			LBFGS.lbfgs(1, 5, x, f(x[0]), new double[]{g(x[0])},
					false, diag, iprint, precision,
					1e-256, iflag);
			
			System.out.println("x: " + x[0]);
			if (iflag[0] == 0) go = false;
		}
		
		System.out.println("iflag: " + iflag[0]);
	}
	
	public static double g(double x)
	{
	    return 4 * Math.pow(x, 3) - 9 * Math.pow(x,2);
	}
	
	public static double f(double x)
	{
		return Math.pow(x,4)-3 * Math.pow(x,3) + 2;
	}
}
