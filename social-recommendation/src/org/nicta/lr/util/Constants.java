package org.nicta.lr.util;

public class Constants 
{
	public static final long APPLICATION_ID = 149458388467879l;
	
	public static final String SERVER_DB_STRING = "jdbc:mysql://localhost/linkrData?user=linkrAdmin&password=w39ve5S97GjtATP7";
	public static final String LOCAL_DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";

	public static final String SERVER_LANG_PROFILE_FOLDER = "/home/u4754420/profiles";
	public static final String LOCAL_LANG_PROFILE_FOLDER = "./profiles";

	public static final String TEST = "test";
	public static final String RECOMMEND = "recommend";
	
	public static final String SPECTRAL = "socialspectral";
	public static final String SOCIAL = "social2";
	public static final String FEATURE = "feature";
	public static final String SVM = "svm";
	public static final String NN = "nn";
	public static final String GLOBAL = "global";
	public static final String FUW = "fuw";
	public static final String FIW = "fiw";
	public static final String LOGISTIC = "logistic";
	public static final String CBF = "cbf";
	public static final String HYBRID = "hybrid";
	public static final String HYBRID_SOCIAL = "hybridsocial";
	public static final String HYBRID_SPECTRAL = "hybrid_spectral";
	public static final String SOCIAL_COPREFERENCE = "social_copreference";
	public static final String SPECTRAL_COPREFERENCE = "socialcospectral";
	
	public static final String ACTIVE = "active";
	public static final String PASSIVE = "passive";
	public static final String UNION = "union";
	
	public static final String FB_USER_PASSIVE = "fb-user-passive";
	public static final String APP_USER_PASSIVE = "app-user-passive";
	public static final String APP_USER_ACTIVE_ALL = "app-user-active-all";
	public static final String APP_USER_ACTIVE_FRIEND = "app-user-active-friend";
	public static final String APP_USER_ACTIVE_NON_FRIEND = "app-user-active-active-non-friend";
	
	public static final int ACTIVE_ALL = 0;
	public static final int ACTIVE_FRIEND = 1;
	public static final int ACTIVE_NON_FRIEND = 2;
}
