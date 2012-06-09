package project.riley.predictor;

import java.io.IOException;

/*
 * Set up and launch classifiers
 */

public class Launcher {

	public static void main(String[] args) throws IOException {
		Predictor naiveBayes = new NaiveBayes(1.0d);
		Predictor logisticRegression = new LogisticRegression();
		//Predictor svm = new SVM();
		naiveBayes.runTests();
		logisticRegression.runTests();
		//svm.runTests();
	}
	
}
