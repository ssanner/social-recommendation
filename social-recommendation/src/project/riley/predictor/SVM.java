package project.riley.predictor;

import java.io.IOException;
import java.util.ArrayList;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.ArffData.SplitData;

public class SVM extends Predictor {

	public ArffData _testData = null;
	public ArffData _trainData = null;
	private svm_model _model = null;
	
	@Override
	public void setData(SplitData data) {
		_testData = data._test;
		_trainData = data._train;	
	}

	@Override
	public void train() {
		_model = svmTrain();		
	}

	/*
	 * train the svm model
	 */
	private svm_model svmTrain() {
		svm_problem prob = new svm_problem();
		int dataCount = _testData._data.size();
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];		
		
		for (int i = 0; i < dataCount; i++){			
			double[] features = getFeatures(_trainData._data.get(i), _trainData._attr.size());
			prob.x[i] = new svm_node[features.length-1];
			// first 'feature' is class value
			for (int j = 1; j < features.length; j++){
				svm_node node = new svm_node();
				node.index = j;
				node.value = features[j];
				prob.x[i][j] = node;
			}			
			prob.y[i] = features[0];
		}				
		
		svm_parameter param = new svm_parameter();
		param.probability = 1;
		param.C = 2.0d;
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;		
		//param.cache_size = 20000;
		param.eps = 0.001;
		
		System.out.println("Training...");
		svm_model model = svm.svm_train(prob, param);
		System.out.println("Done");
		return model;
	}

	@Override
	public <T> int evaluate(T de, double threshold) {
		
		double[] features = getFeatures((DataEntry)de,_trainData._attr.size());
		svm_node node = new svm_node();
		for (int i = 1; i < features.length; i++){
			node.index = i;
			node.value = features[i];
		}
		svm_node[] nodes = new svm_node[1];
		nodes[0] = node;
		
		// threshold?
		int totalClasses = 2;
		int[] labels = new int[totalClasses];
		svm.svm_get_labels(_model, labels);
		double[] prob_estimates = new double[totalClasses];
		System.out.print("labels");
		for (int i = 0; i < totalClasses; i++){
			System.out.print(" " + labels[i]);
		}
		System.out.println();	
		
		double v = svm.svm_predict_probability(_model, nodes, prob_estimates);
		System.out.print(v);
		for (int i = 0; i < totalClasses; i++){
			System.out.print(" (" + i + ":" + prob_estimates[i] + ")");
		}
		System.out.println();
		
		
		double[] dbl = new double[1];
		svm.svm_predict_values(_model, nodes, dbl);
		double prediction = dbl[0];
		
		System.out.println("Prediction: " + prediction);
		return (int) prediction;
	}

	@Override
	public void clear() {
		_model = null;
	}

	@Override
	public String getName() {
		return "SVM";
	}

	@Override
	public <T> ArrayList<T> getTrainData() {
		return (ArrayList<T>) _trainData._data;
	}

	@Override
	public <T> ArrayList<T> getTestData() {
		return (ArrayList<T>) _testData._data;
	}

	public static void main(String[] args) throws IOException{
		SVM svm = new SVM();
		svm.runTests();
	}
	
}
