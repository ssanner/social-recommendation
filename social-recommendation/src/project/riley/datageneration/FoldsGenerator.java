package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.DataEntry;

/*
 * generate n-fold data for cross validation
 * 
 */
public class FoldsGenerator {
	int totalFolds = 10;			// total folds to generate
	String fileName = "a.arff";		// arff file to read
	String header = "al"; 			// al || pl

	/*
	 * read file to split into validation sets
	 */
	public ArffData readArff(){
		return new ArffData(fileName);
	}

	/*
	 * write validation sets
	 * 
	 */
	public void writeFolds(ArffData data) throws FileNotFoundException{
		int size = data._data.size();				// size of the data set
		int start = 0;								// start index
		int testFold = 0;							// fold to test (others are train)
		int stepSize = size/totalFolds;				// step size
		int extra = size % totalFolds;
		for (int i = 0; i < size; i += stepSize){
			if (extra > 0){
				i++;
				extra--;
			}
			//System.out.println(start + ":" + i + ":" + (i+stepSize) + ":" + size);
			List<DataEntry> testSet = data._data.subList(i, i+stepSize);			// training indices
			List<DataEntry> trainSet = data._data.subList(start, i);				// testing indices from start to train start
			trainSet.addAll(data._data.subList(i+stepSize, size));					// testing indices from train end to list end
			testFold++;

			writeFoldData(header + "_test_fold" + testFold + ".arff",testSet);
			writeFoldData(header + "_train_fold" + testFold + ".arff",trainSet);
		}
	}

	/*
	 * write fold data including header data for each fold
	 * {pl||al}_train_fold{0..9}.arff 
	 */
	public void writeFoldData(String fileName, List<DataEntry> data) throws FileNotFoundException{
		String[] directions = new String[]{"Incoming", "Outgoing"};					
		String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
		String[] interactionType = new String[]{"Comments", "Tags", "Likes"};

		PrintWriter writer = new PrintWriter(fileName);		
		writer.println("@relation app-data");
		writer.println("@attribute 'Uid' numeric");
		writer.println("@attribute 'Item' numeric");
		writer.println("@attribute 'Class' { 'n' , 'y' }");
		for (String direction : directions){
			for (String interaction : interactionMedium){
				for (int i = 0; i < interactionType.length; i++){
					if (interaction.equals("Link") && interactionType[i].equals("Tags")){
						continue; // no link tags data
					}
					writer.println("@attribute '" + direction + "-" + interaction + "-" + interactionType[i] + "' { 'n', 'y' }");
				}
			}
		}
		writer.println("@data");
		for (DataEntry de : data) {   
			//writer.println(de);
			System.out.println(de);
		}
		writer.close();
	}

	public static void main(String[] args) throws FileNotFoundException {
		FoldsGenerator fg = new FoldsGenerator();
		ArffData data = fg.readArff();
		fg.writeFolds(data);
	}

}
