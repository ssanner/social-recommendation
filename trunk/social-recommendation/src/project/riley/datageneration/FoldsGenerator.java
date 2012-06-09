package project.riley.datageneration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import project.riley.predictor.ArffData;

/*
 * generate n-fold data for cross validation
 * 
 * # Split the data into 'fold' sets of training and testing indices 
def splitData(fold):
	start = 0				# starting index
	length = len(dataMap)   # length of the data set
	skip = length / fold    # skip size for folds
	indices = []
	for i in range(start, length, skip):  # break up indices for testing and training sets
		testIndices = [x for x in range(i, (i+skip), 1)]
		trainIndices = [x for x in range(start, i, 1)] + [x for x in range((i+15), length, 1)]
		indices.append((testIndices, trainIndices))
	return indices			# return the sets of indices

 */
public class FoldsGenerator {
	int folds = 10;
	String fileName = "a.arff";
	PrintWriter writer;
	
	String[] directions = new String[]{"Incoming", "Outgoing"};					
	String[] interactionMedium = new String[]{"Post", "Photo", "Video", "Link"};
	String[] interactionType = new String[]{"Comments", "Tags", "Likes"};
	
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
	
	public ArffData readArff(){
		return new ArffData(fileName);
	}
	
	public void writeFolds(ArffData data){
		
	}
	
	public static void main(String[] args) {
		FoldsGenerator fg = new FoldsGenerator();
		ArffData data = fg.readArff();
		fg.writeFolds(data);
	}
	
}
