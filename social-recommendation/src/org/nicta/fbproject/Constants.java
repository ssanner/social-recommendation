package org.nicta.fbproject;

public class Constants 
{
	static final String DB_STRING = "jdbc:mysql://localhost/sorec?user=socrec&password=sorec";
	
	static final int LINK_FEATURE_COUNT = 4;
	static final int MIN_COMMON_WORD_COUNT = 100;
	
	final static int USER_FEATURE_COUNT = 4;
	
	final static String COUNTRIES[] = {
		"Others",
		"Canada",
		"Australia",
		"Vietnam",
		"United States",
		"United Kingdom",
		"Italy",
		"France",
		"Singapore",
		"New Zealand",
		"Russia",
		"Puerto Rico",
		"Spain",
		"India",
		"Finland",
		"Hong Kong",
		"Cuba",
		"Austria",
		"Switzerland",
		"Nepal",
		"Costa Rica",
		"Thailand",
		"Indonesia",
		"Peru",
		"Malaysia",
		"Greece",
		"Philippines",
	};
	
	static final int K = 5;
	
	static final double LAMBDA = 1;
	
	static final double STEP_CONVERGENCE = 1e-2;
}
