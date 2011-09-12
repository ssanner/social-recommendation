package org.nicta.lr.util;

public class Configuration 
{
	//Feature counts
	public static final int LINK_FEATURE_COUNT = 3;
	public static final int USER_FEATURE_COUNT = 3;
	
	//Windows
	public static final int TRAINING_WINDOW_RANGE = 30;
	public static final int TESTING_WINDOW_RANGE = 14;
	public static final int RECOMMENDING_WINDOW_RANGE = 14;
	
	public static int DEPLOYMENT_TYPE = Constants.TEST;
	public static boolean INITIALIZE = false;
	
	public static String DB_STRING = Constants.SERVER_DB_STRING;
	public static String LANG_PROFILE_FOLDER = Constants.SERVER_LANG_PROFILE_FOLDER;
}
