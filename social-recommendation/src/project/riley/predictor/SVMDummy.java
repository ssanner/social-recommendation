package project.riley.predictor;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class SVMDummy {

	double[][] train = new double[1000][]; 
	double[][] test = new double[10][];
	private svm_model _model = null;

	// linearly separable data in y, first 'feature' is class
	private void setData(){
		for (int i = 0; i < train.length; i++){
			if (i+1 > (train.length/2)){		// 50% positive
				double[] vals = {1,0,i+i};
				train[i] = vals;
			} else {
				double[] vals = {0,0,i-i-i-2}; // 50% negative
				train[i] = vals;
			}			
		}

		for (int i = 0; i < test.length; i++){			
			if (i+1 > (test.length/2)){
				double[] vals = {1,0,i+i};
				test[i] = vals;
			} else {
				double[] vals = {0,0,i-i-i-2};
				test[i] = vals;
			}			
		}
	}

	private svm_model svmTrain() {
		svm_parameter param = new svm_parameter();
		//param.probability = 1;
		//param.degree = 3;
		//param.gamma = 0.25;
		//param.nu = 0.5;
		//param.C = 1;
		param.C = .00000000000000000000001;
		
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;		
		//param.cache_size = 20000;
		param.eps = 0.001;
		
		svm_problem prob = new svm_problem();
		int dataCount = train.length;
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];		
		
		for (int i = 0; i < dataCount; i++){			
			double[] features = train[i];
			prob.x[i] = new svm_node[features.length-1];
			for (int j = 1; j < features.length; j++){
				prob.x[i][j-1] = new svm_node();
				prob.x[i][j-1].index = j;
				prob.x[i][j-1].value = features[j];
			}			
			prob.y[i] = features[0];
		}						
		
		svm_model model = svm.svm_train(prob, param);

		return model;
	}


	public int evaluate(double[] features) {		
		svm_node[] nodes = new svm_node[features.length-1];
		for (int i = 1; i < features.length; i++){
			nodes[i-1] = new svm_node();
			nodes[i-1].index = i;
			nodes[i-1].value = features[i];
		}
		
		int totalClasses = 2;		
		int[] labels = new int[totalClasses];
		svm.svm_get_labels(_model,labels);
		
		double[] prob_estimates = new double[totalClasses];
		//double v = svm.svm_predict_probability(_model, nodes, prob_estimates);
		double v = svm.svm_predict(_model, nodes);
		
		for (int i = 0; i < totalClasses; i++){
			System.out.print("(" + labels[i] + ":" + prob_estimates[i] + ")");
		}
		System.out.println("(Actual:" + features[0] + " Prediction:" + v + ")");			
		
		return (int)v;
	}

	public static void main(String[] args) {		
		SVMDummy svm = new SVMDummy();
		svm.setData();
		svm._model = svm.svmTrain();

		int correct = 0;
		for (int i = 0; i < svm.test.length; i++){
			if (svm.test[i][0] == svm.evaluate(svm.test[i])){
				correct++;
			}
		}
		System.out.println(correct + "/" + svm.test.length);
	}

}
