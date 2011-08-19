package org.nicta.lr.util;

public class Constants 
{
	public static final String DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=linkrAdmin&password=w39ve5S97GjtATP7";
	
	public static final int LINK_FEATURE_COUNT = 3;
	public static final int MIN_COMMON_WORD_COUNT = 100;
	
	public final static int USER_FEATURE_COUNT = 4;
	
	public static int K = 5;
	
	public static double LAMBDA = 100;
	public static double BETA = 1.0E-6;
	
	public static final double STEP_CONVERGENCE = 1e-2;
	
	public static final int WINDOW_RANGE = 22;
	
	public static double C = Math.pow(2, 1);
	
	public static final double BOUNDARY = 0.5;
	
	public static final double STEP_SIZE = 0.0001; //learning rate

	public static int TEST = 0;
	
	public static int RECOMMEND = 1;
	
	public static int DEPLOYMENT_TYPE = TEST;
	
	public static boolean INITIALIZE = false;
	
	public static final long APPLICATION_ID = 149458388467879l; //NEW
	
	
	
	
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=GuestGA&password=mB7SwjDWQF37BrLD";
	//public static final long APPLICATION_ID = 136622929736439l; //OLD
}
