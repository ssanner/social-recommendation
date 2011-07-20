package org.nicta.lr.util;

public class Constants 
{
	public static final String DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=GuestGA&password=mB7SwjDWQF37BrLD";
	
	public static final int LINK_FEATURE_COUNT = 5;
	public static final int MIN_COMMON_WORD_COUNT = 100;
	
	public final static int USER_FEATURE_COUNT = 4;
	
	public static int K = 1;
	
	public static double LAMBDA = .001;
	public static double BETA = .001;
	
	public static final double STEP_CONVERGENCE = 1e-5;
	
	public static final int WINDOW_RANGE = 100;
	
	public static final int RECOMMENDATION_COUNT = 1;
}
