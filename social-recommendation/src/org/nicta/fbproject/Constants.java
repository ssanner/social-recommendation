package org.nicta.fbproject;

public class Constants 
{
	static final String DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=GuestGA&password=mB7SwjDWQF37BrLD";
	
	static final int LINK_FEATURE_COUNT = 4;
	static final int MIN_COMMON_WORD_COUNT = 100;
	
	final static int USER_FEATURE_COUNT = 4;
	
	static final int K = 5;
	
	static final double LAMBDA = 1;
	static final double BETA = 1;
	
	static final double STEP_CONVERGENCE = 1e-2;
	
	static final int WINDOW_RANGE = 100;
	
	static final int RECOMMENDATION_COUNT = 1;
}
