package project.riley.predictor;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import project.riley.predictor.ArffData.DataEntry;

/*
 * Naive Bayes implementation
 */

public class NaiveBayes extends Predictor {

	public static DecimalFormat _df = new DecimalFormat("0.######");

	//public ArffData _trainData = null;
	//public ArrayList<DataEntry> _testData = null;
	//public ArrayList<DataEntry> _trainData = null;

	public double DIRICHLET_PRIOR = 1d;
	public double _threshold;
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
			ArffData.Attribute  a = _trainData._attr.get(_attr_index);
			ArffData.Attribute ca = _trainData._attr.get(CLASS_INDEX);
			if (_attr_index == CLASS_INDEX) {
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
		
		_threshold = 0.5d;
		
		DIRICHLET_PRIOR = dirichlet_prior;

		// Bad to have zero counts... makes cases not seen in data
		// impossible and can lead to divide by zero.
		if (DIRICHLET_PRIOR == 0d) 
			DIRICHLET_PRIOR = 1e-6d;
	}

	@Override
	public void train() {
		if (_trainData == null) { System.out.println("No data!"); }

		_condProb = new ArrayList<ClassCondProb>(_trainData._attr.size());

		//System.out.println("Training for " + _condProb.size() + " attributes.");

		// Build conditional probability tables
		ArffData.Attribute ca = _trainData._attr.get(CLASS_INDEX);		

		if (ca.type != ArffData.TYPE_CLASS) {
			System.out.println("Cannot classify non-class attribute index " + 
					CLASS_INDEX + ":\n" + ca);
			System.exit(1);
		}

		// For each class, record count with positive and record 
		// count with negative
		for (int i = 0; i < _trainData._attr.size(); i++) {

			if (_trainData._attr.get(i).type != ArffData.TYPE_CLASS){ // dont want to do anything with the real cols
				//System.out.println("Skipping - " + _trainData._attr.get(i));
				continue;
			}

			// TODO: Inefficient to constantly recompute
			int[] overall_count = new int[ca.class_vals.size()];

			//System.out.println("Processing " + i);
			ClassCondProb ccp = new ClassCondProb(i);
			_condProb.add(ccp);

			// Put the prior in this class
			if (i == CLASS_INDEX) {
				ccp._logprob = new double[ca.class_vals.size()][];
				for (int j = 0; j < ca.class_vals.size(); j++) {
					ccp._logprob[j] = new double[1];
				}
				for (int j = 0; j < _trainData._data.size(); j++) {
					ArffData.DataEntry de = _trainData._data.get(j);					
					int class_value = ((Integer)de.getData(CLASS_INDEX)).intValue();
					ccp._logprob[class_value][0] = ccp._logprob[class_value][0] + 1d; 
				}
				// Normalize and take log
				for (int j = 0; j < ca.class_vals.size(); j++) {
					if (DIRICHLET_PRIOR + ccp._logprob[j][0] > 0d)
						ccp._logprob[j][0] = Math.log((DIRICHLET_PRIOR + ccp._logprob[j][0]) / 
								(_trainData._data.size() + ca.class_vals.size() * DIRICHLET_PRIOR));
				}
				continue;
			}

			// Otherwise compute the conditional probabilities for this attribute
			ArffData.Attribute a  = _trainData._attr.get(i);
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
			for (int j = 0; j < _trainData._data.size(); j++) {				
				ArffData.DataEntry de = _trainData._data.get(j);
				int attr_value  = ((Integer)de.getData(i)).intValue();
				int class_value = ((Integer)de.getData(CLASS_INDEX)).intValue();
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

	@Override
	// SPS -- TODO: Threshold should be determined on train data and automatically set
	//              to value that maximizes accuracy (?). 
	public int evaluate(DataEntry de) {
		// Get class attribute
		ArffData.Attribute ca = _testData._attr.get(CLASS_INDEX);
		if (ca.type != ArffData.TYPE_CLASS) {
			System.out.println("Cannot classify non-class attribute index " + 
					CLASS_INDEX + ":\n" + ca);
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
				if (j == CLASS_INDEX) {
					class_value += ccp._logprob[i][0];
				} else {
					//System.out.print(((Integer)de.getData(j)).intValue() + " ");
					class_value += ccp._logprob[((Integer)((ArffData.DataEntry) de).getData(j)).intValue()][i];
				}
			}

			//cv[i] = Math.exp(class_value);
			cv[i] = Math.exp(class_value/(_condProb.size()+1d)); // geometric mean
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
		
		// Note: SPS -- the following is very poor style... can you write this in a cleaner way?
		//              Math.abs(best_class-1).  Negation of a {0,1}? 1-best_class would be a little
		//              cleaner, but still this would be unclear.
		double prediction = cv[best_class]/Z >= _threshold ? best_class : Math.abs(best_class-1);
		return (int) prediction;
	}

	@Override
	public void clear() {
		_condProb = null;
	}

	@Override
	public String getName() {
		return "NaiveBayes(" + DIRICHLET_PRIOR + ")";
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("\nNaive Bayes CPTs [" + _condProb.size() + "]:\n\n");
		for (int i = 0; i < _condProb.size(); i++) {
			ClassCondProb ccp = _condProb.get(i);
			sb.append("Attribute: " + _trainData._attr.get(i+2).name + "\n");
			sb.append(ccp.toString() + "\n");
		}
		return sb.toString();
	}

	public static void main(String[] args) throws IOException {
		NaiveBayes nb = new NaiveBayes(1.0d);
		nb.runTests("active.arff", 10);
	}

}
