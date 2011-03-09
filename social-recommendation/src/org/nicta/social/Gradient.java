package org.nicta.social;

/*
 * Just a simple test class for learning how Gradient Descent actually works.
 */

public class Gradient
{
    public static void main(String[] args)
    {
    	new Gradient().gradient();
    }

    public double fPrime(double x)
    {
    	return (4 * Math.pow(x, 3)) - (9 * Math.pow(x, 2));
    }

    public void gradient()
    {
    	double xOld = 0;
    	double xNew = 6;
		double eps = 0.01;
		double precision = 0.00001;

		int iterations = 0;

		while (Math.abs(xNew - xOld) > precision) {
			iterations++;

			double tmp = xNew - (eps * fPrime(xNew));
			//if (tmp < xNew) {
				xOld = xNew;
				xNew = tmp;
			//}
			//else {
				//eps *= .1;
			//}
		}

		System.out.println("Local minimum: " + xNew);
		System.out.println("Iterations: " + iterations);
    }
}