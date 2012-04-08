package naivebayes;

/** Simple Naive Bayes algorithm.
 * 
 * @author Scott Sanner
 */


import java.util.*;
import java.text.*;

/* TODO: Fix to inherit form Classifier again; then remove local _arffData and _classIndex */
public class NaiveBayes /*extends Classifier*/ {

	public static DecimalFormat _df = new DecimalFormat("0.######");

	public ArffData _arffData = null;
	public int _classIndex    = -1;

	public double DIRICHLET_PRIOR = 1d;
	public ArrayList<ClassCondProb> _condProb = null; 

	public class ClassCondProb {
		int _attr_index;
		public double[][] _logprob; // For each class and attribute,
		// a probability that sums to 1

		public ClassCondProb(int index) {
			_attr_index = index;
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			ArffData.Attribute  a = _arffData._attr.get(_attr_index);
			ArffData.Attribute ca = _arffData._attr.get(_classIndex);
			if (_attr_index == _classIndex) {
				for (int cv = 0; cv < ca.class_vals.size(); cv++) {
					sb.append("P( " + ca.name + " = " + ca.getClassName(cv) + " ) = " + 
							_df.format(Math.exp(_logprob[cv][0])) + "\n");
				}
			} else { 
				for (int cv = 0; cv < ca.class_vals.size(); cv++) {
					for (int av = 0; av < a.class_vals.size(); av++) {
						sb.append("P( " + a.name + " = " + a.getClassName(av) + " | " + ca.name + " = " + ca.getClassName(cv) + 
								" ) = " + _df.format(Math.exp(_logprob[av][cv])) + "\n");
					}
				}
			}
			return sb.toString();
		}
	}

	public NaiveBayes(double dirichlet_prior) {
		DIRICHLET_PRIOR = dirichlet_prior;

		// Bad to have zero counts... makes cases not seen in data
		// impossible and can lead to divide by zero.
		if (DIRICHLET_PRIOR == 0d) 
			DIRICHLET_PRIOR = 1e-6d;
	}

	public void setTrainData(ArffData arff_data) {
		//System.out.println("Setting data: " + arff_data.toString());
		_arffData = arff_data;		
	}

	public String toString() {
		StringBuffer sb = new StringBuffer("\nNaive Bayes CPTs [" + _condProb.size() + "]:\n\n");
		for (int i = 0; i < _condProb.size(); i++) {
			ClassCondProb ccp = _condProb.get(i);
			sb.append("Attribute: " + _arffData._attr.get(i+2).name + "\n");
			sb.append(ccp.toString() + "\n");
		}
		return sb.toString();
	}

	public String getName() {
		return "NaiveBayes";
	}

	public void clear() {
		_condProb = null;
	}

	// TODO: Should redo training to be incremental!
	public void train(int class_index) {

		_classIndex = class_index;
		if (_arffData == null) { System.out.println("No data!"); }

		_condProb = new ArrayList<ClassCondProb>(_arffData._attr.size());

		//System.out.println("Training for " + _condProb.size() + " attributes.");

		// Build conditional probability tables
		ArffData.Attribute ca = _arffData._attr.get(class_index);		

		if (ca.type != ArffData.TYPE_CLASS) {
			System.out.println("Cannot classify non-class attribute index " + 
					class_index + ":\n" + ca);
			System.exit(1);
		}

		// For each class, record count with positive and record 
		// count with negative
		for (int i = 0; i < _arffData._attr.size(); i++) {

			if (_arffData._attr.get(i).type != ArffData.TYPE_CLASS){ // dont want to do anything with the real cols
				//System.out.println("Skipping - " + _arffData._attr.get(i));
				continue;
			}

			// TODO: Inefficient to constantly recompute
			int[] overall_count = new int[ca.class_vals.size()];

			//System.out.println("Processing " + i);
			ClassCondProb ccp = new ClassCondProb(i);
			_condProb.add(ccp);

			// Put the prior in this class
			if (i == class_index) {
				ccp._logprob = new double[ca.class_vals.size()][];
				for (int j = 0; j < ca.class_vals.size(); j++) {
					ccp._logprob[j] = new double[1];
				}
				for (int j = 0; j < _arffData._data.size(); j++) {
					ArffData.DataEntry de = _arffData._data.get(j);					
					int class_value = ((Integer)de.getData(class_index)).intValue();
					ccp._logprob[class_value][0] = ccp._logprob[class_value][0] + 1d; 
				}
				// Normalize and take log
				for (int j = 0; j < ca.class_vals.size(); j++) {
					if (DIRICHLET_PRIOR + ccp._logprob[j][0] > 0d)
						ccp._logprob[j][0] = Math.log((DIRICHLET_PRIOR + ccp._logprob[j][0]) / 
								(_arffData._data.size() + ca.class_vals.size() * DIRICHLET_PRIOR));
				}
				continue;
			}

			// Otherwise compute the conditional probabilities for this attribute
			ArffData.Attribute a  = _arffData._attr.get(i);
			if (a.type != ArffData.TYPE_CLASS) {
				//System.out.println("Skipping - " + a);
				//System.out.println("Cannot classify non-class attribute index " + 
				//		i + ":\n" + a);
				//System.exit(1);
				continue;
			}

			ccp._logprob = new double[a.class_vals.size()][];
			for (int j = 0; j < a.class_vals.size(); j++) {
				ccp._logprob[j] = new double[ca.class_vals.size()];
			}

			// Sort data entries into subnodes
			for (int j = 0; j < _arffData._data.size(); j++) {				
				ArffData.DataEntry de = _arffData._data.get(j);
				int attr_value  = ((Integer)de.getData(i)).intValue();
				int class_value = ((Integer)de.getData(class_index)).intValue();
				ccp._logprob[attr_value][class_value] = ccp._logprob[attr_value][class_value] + 1d;
				overall_count[class_value]++;
			}

			// Normalize and take log
			for (int av = 0; av < a.class_vals.size(); av++) {
				for (int cv = 0; cv < ca.class_vals.size(); cv++) {
					if (DIRICHLET_PRIOR + ccp._logprob[av][cv] != 0d)
						ccp._logprob[av][cv] = Math.log((DIRICHLET_PRIOR + ccp._logprob[av][cv]) 
								/ (overall_count[cv] + DIRICHLET_PRIOR * ca.class_vals.size()));
				}
			}
		}
		//System.out.println("Constructed " + _condProb.size() + " CPTs.");
		//System.out.println(this);
	}

	public int evaluate(ArffData.DataEntry de, double threshold) {

		// Get class attribute
		ArffData.Attribute ca = _arffData._attr.get(_classIndex);
		if (ca.type != ArffData.TYPE_CLASS) {
			System.out.println("Cannot classify non-class attribute index " + 
					_classIndex + ":\n" + ca);
			System.exit(1);
		}

		// For each class, record count with positive and record 
		// count with negative
		int best_class = -1;
		double best_class_value = Double.NEGATIVE_INFINITY;
		double Z = 0d;
		double[] cv = new double[2];		
		for (int i = 0; i < ca.class_vals.size(); i++) {			

			double class_value = 0d;
			for (int j = 2; j < _condProb.size(); j++) {			

				ClassCondProb ccp = _condProb.get(j);
				if (j == _classIndex) {
					class_value += ccp._logprob[i][0];
				} else {
					//System.out.print(((Integer)de.getData(j)).intValue() + " ");
					class_value += ccp._logprob[((Integer)de.getData(j)).intValue()][i];
				}
			}
			cv[i] = Math.exp(class_value);
			Z += cv[i];
			//System.out.println("[" + i + "] " + class_value + " " + _df.format(Math.exp(class_value)));
			if (class_value > best_class_value) {
				best_class = i;
				best_class_value = class_value;
			}															
		}

		/*for (int i = 0; i < cv.length; i++){
			System.out.println(i + " " + _df.format(cv[i]) + "/" + _df.format(Z) + "=" + _df.format(cv[i]/Z));
		}*/
		
		//System.out.println("Best [" + best_class + "] " + best_class_value + " :: " + de);
		System.out.println(cv[best_class]/Z > threshold);
		return best_class;	
	}

	public double[] measures(ArrayList<ArffData.DataEntry> data) {
		double[] measures = new double[4];
		int truePositive = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int correct = 0;
		for (ArffData.DataEntry de : data) {
			int pred = evaluate(de, 0.5); // Evaluate returns sorted results
			int actual     = ((Integer)de.getData(_classIndex)).intValue();
			if (pred == actual) correct++;
			if (pred == actual && actual == 1) truePositive++;
			if (pred == 1 && actual == 0) falsePositive++;
			if (pred == 0 && actual == 1) falseNegative++;
		}
		double precision = (double)truePositive/(truePositive + falsePositive);   
		double recall = (double)truePositive/(truePositive + falseNegative); 
		measures[0] = (double)correct/data.size(); 						// accuracy
		measures[1] = precision;										// precision
		measures[2] = recall;											// recall
		measures[3] = 2 * ((precision * recall)/(precision + recall)); 	// f measure
		return measures;
	}

	public static void main(String args[]) {

		System.out.println("Running NaiveBayes:\n");

		ArffData data = new ArffData("datak100.arff");

		// Assume classification attribute always comes last
		int CLASS_INDEX = 2; 

		// Diagnostic output
		//System.out.println(data); // View data
		//System.out.println(nb); // View what has been learned

		double totalTrainAccuracy = 0.0;
		double totalTestAccuracy = 0.0;
		double totalTrainPrecision = 0.0;
		double totalTestPrecision = 0.0;
		double totalTrainRecall = 0.0;
		double totalTestRecall = 0.0;
		double totalTrainF = 0.0;
		double totalTestF = 0.0;
		int iterations = 1;
		for (int i = 0; i < iterations; i++){
			// Split data into train (80%) / test (20%)
			ArffData.SplitData s = data.splitData(.8d);

			// Build a NB classifier and train
			NaiveBayes nb = new NaiveBayes(1.0d /* prior counts */);
			nb.clear();
			nb.setTrainData(s._train);
			nb.train(CLASS_INDEX);

			/*
			 * 0 = accuracy
			 * 1 = precision
			 * 2 = recall
			 * 3 = f-measure
			 */
			double trainMeasures[] = nb.measures(s._train._data);
			double testMeasures[] = nb.measures(s._test._data);
			
			// Evaluate accuracy of trained classifier on train and test data
			/*System.out.println(i + " Accuracy on train: " + trainMeasures[0]);
			System.out.println(i + " Accuracy on test:  " + testMeasures[0]);
			System.out.println(i + " Precision on train: " + trainMeasures[1]);
			System.out.println(i + " Precision on test:  " + testMeasures[1]);
			System.out.println(i + " Recall on train: " + trainMeasures[2]);
			System.out.println(i + " Recall on test:  " + testMeasures[2]);
			System.out.println(i + " F measure on train: " + trainMeasures[3]);
			System.out.println(i + " F measure on test:  " + testMeasures[3]);		*/	

			totalTrainAccuracy += trainMeasures[0];
			totalTestAccuracy += testMeasures[0];
			totalTrainPrecision += trainMeasures[1];
			totalTestPrecision += testMeasures[1];
			totalTrainRecall += trainMeasures[2];
			totalTestRecall += testMeasures[2];
			totalTrainF += trainMeasures[3];
			totalTestF += testMeasures[3];
			
			//System.out.println(nb);
			
		}
		System.out.println("Train accuracy after " + iterations + " iterations:" + (totalTrainAccuracy/iterations));
		System.out.println("Test accuracy after " + iterations + " iterations:" + (totalTestAccuracy/iterations));
		System.out.println("Train precision after " + iterations + " iterations:" + (totalTrainPrecision/iterations));
		System.out.println("Test precision after " + iterations + " iterations:" + (totalTestPrecision/iterations));
		System.out.println("Train recall after " + iterations + " iterations:" + (totalTrainRecall/iterations));
		System.out.println("Test recall after " + iterations + " iterations:" + (totalTestRecall/iterations));
		System.out.println("Train f-measure after " + iterations + " iterations:" + (totalTrainF/iterations));
		System.out.println("Test f-measure after " + iterations + " iterations:" + (totalTestF/iterations));

	}

}
