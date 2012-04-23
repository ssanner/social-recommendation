package project.riley.svm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SVM {
	
	/*
	 * convert from arff to libsvm format
	 * class <index>:<value>
	 */
	public void convertArff(String file) throws IOException{
		BufferedReader in = new BufferedReader(new FileReader(file));
		BufferedWriter out = new BufferedWriter(new FileWriter(file.split("\\.")[0] + ".libsvm"));
		String line;
		while((line = in.readLine()) != null){
			if (!line.startsWith("@") && line.length() > 0){
				writeArffLine(line, out);
			}											
		}			
		out.close();
	}
	
	/*
	 * write a line using the arff data
	 */
	public void writeArffLine(String line, BufferedWriter out) throws IOException{
		String[] parts = line.split(",");
		
		// third item is class		
		StringBuffer writeLine = new StringBuffer("" + yTo1nTo0(parts[2]));
		
		// first two parts of line are item id and user id
		for (int i = 3; i < parts.length; i++){
			writeLine.append(" " + (i-2) + ":" + yTo1nTo0(parts[i]));
		}		
		System.out.println(writeLine.toString());
		out.write(writeLine.toString());
		out.newLine();
	}
	
	/*
	 * input is either 'n' or 'y'
	 */
	public int yTo1nTo0(String str){
		return (str.charAt(1) == 'n' ? 0 : 1);
	}
	
	
	public static void main(String[] args) throws IOException {
		SVM svm = new SVM();
		svm.convertArff("datak100.arff");
	}
	
}
