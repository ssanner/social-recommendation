package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.DataEntry;

/*
 * generate n-fold data for cross validation
 * 
 */
public class FoldsGenerator {
	int totalFolds = 10;
	String fileName = "a.arff";
	PrintWriter writer;
	
	String[] directions = new String[]{"Incoming", "Outgoing"};					
	String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
	String[] interactionType = new String[]{"Comments", "Tags", "Likes"};
	
	/*
	 * write header data for each fold
	 */
	public void writeHeader(PrintWriter writer, String fileName) throws FileNotFoundException{
		writer = new PrintWriter(fileName);		
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
	}
	
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
	public void writeFolds(ArffData data){
		int size = data._data.size();				// size of the data set
		int testFold = 0;							// fold to test
		int stepSize = size/totalFolds;				// step size
		int extraSteps = size % totalFolds;			// left over parts
		for (int i = 0; i < size; i += stepSize){
			System.out.println(i);
			testFold++;
			/*for (DataEntry de : data._data){
				for (int i = 0; i < data._attr.size(); i++){
					System.out.print(de.getData(i) + " ");
				}
			}*/
		}
	}
	
	public static void main(String[] args) {
		FoldsGenerator fg = new FoldsGenerator();
		ArffData data = fg.readArff();
		fg.writeFolds(data);
	}
	
}
