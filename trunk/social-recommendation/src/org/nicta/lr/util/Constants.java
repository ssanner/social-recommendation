package org.nicta.lr.util;

public class Constants 
{
	public static final long APPLICATION_ID = 149458388467879l;
	//public static final String DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";
	static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=linkrAdmin&password=w39ve5S97GjtATP7";
	
	public static final int LINK_FEATURE_COUNT = 3;
	public final static int USER_FEATURE_COUNT = 4;
	
	public static final int TRAINING_WINDOW_RANGE = 30;
	public static final int TESTING_WINDOW_RANGE = 14;
	public static final int RECOMMENDING_WINDOW_RANGE = 14;

	public static int TEST = 0;
	public static int RECOMMEND = 1;
	public static int DEPLOYMENT_TYPE = TEST;
	public static boolean INITIALIZE = false;
	
	public static final String SOCIAL = "social";
	public static final String FEATURE = "feature";
	public static final String SVM = "svm";
	public static final String NN = "nn";
	public static final String GLOBAL = "global";
	public static final String FUW = "fuw";
	public static final String FIW = "fiw";
	
	//static final String DB_STRING = "jdbc:mysql://localhost/linkrData?user=GuestGA&password=mB7SwjDWQF37BrLD";
	//public static final long APPLICATION_ID = 136622929736439l; //OLD
}
