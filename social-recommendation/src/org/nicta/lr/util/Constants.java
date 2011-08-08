package org.nicta.lr.util;

public class Constants 
{
	public static final String DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=GuestGA&password=mB7SwjDWQF37BrLD";
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=linkrAdmin&password=w39ve5S97GjtATP7";
	
	public static final int LINK_FEATURE_COUNT = 5;
	public static final int MIN_COMMON_WORD_COUNT = 100;
	
	public final static int USER_FEATURE_COUNT = 4;
	
	public static int K = 1;
	
	public static double LAMBDA = 10;
	public static double BETA = .001;
	
	public static final double STEP_CONVERGENCE = 1e-5;
	
	public static final int WINDOW_RANGE = 100;
	
	public static double C = Math.pow(2, 1);
	
	public static final double BOUNDARY = 0.5;
	
	public static final long APPLICATION_ID = 136622929736439l; //OLD
	//public static final long APPLICATION_ID = 149458388467879l; //NEW
}
