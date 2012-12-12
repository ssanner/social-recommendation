package project.suvash.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.Interaction;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.Configuration;

import com.cybozu.labs.langdetect.LangDetectException;



public class DataUtil {

	public static void dumpComments() 
			throws SQLException,IOException
	{

		String[] tables = {"linkrPost", "linkrLinkComments", "linkrPostComments", "linkrPhotoComments", "linkrVideoComments"};
		for (String table : tables){
			FileWriter fstream = new FileWriter(table);
			BufferedWriter out = new BufferedWriter(fstream);
			String sql_query = "SELECT uid, from_id, message FROM " + table;
			Statement statement = SQLUtil.getStatement();
			ResultSet result = statement.executeQuery(sql_query);
			while (result.next()) {
				long TARGET_ID = result.getLong(1);
				long FROM_ID = result.getLong(2);
				String message = result.getString(3);
				out.write(message);
				out.newLine();
			}
			out.close();
			fstream.close();
			statement.close();			
		}			

	}

	public static void languageFilter(String inputPath, String outputPath, String lang) throws LangDetectException, IOException{
		LanguageDetector ld = new LanguageDetector();
		FileReader inputFstream = new FileReader(inputPath);
		BufferedReader inputReader = new BufferedReader(inputFstream);
		FileWriter outputFstream = new FileWriter(outputPath);
		BufferedWriter outputWriter = new BufferedWriter(outputFstream);
		String line = null;
		while((line = inputReader.readLine()) != null){
			if(ld.languageFilter(line, lang)){
				outputWriter.write(line);
				outputWriter.newLine();
			}
		}
		inputReader.close();
		outputWriter.close();
	}
	
	public static void main(String[] args) throws SQLException, IOException, LangDetectException{
		languageFilter("data/comments/combined", "combined_lang_filtered", "en");
	}

}
