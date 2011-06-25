package org.nicta.fbproject;

public class RecommenderMain 
{
	public static void main(String[] args)
		throws Exception
	{
		new FeatureRecommender().recommend();
		new SocialRecommender().recommend();
	}
}
