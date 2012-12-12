package project.suvash.util;

import java.util.ArrayList;

import org.nicta.lr.util.Configuration;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;


public class LanguageDetector {
	
	public LanguageDetector() throws LangDetectException{
		DetectorFactory.loadProfile(Configuration.LANG_PROFILE_FOLDER);
	}
	
	public boolean languageFilter(String text, String filter) throws LangDetectException{
		Detector messageDetector;
		messageDetector = DetectorFactory.create();
		messageDetector.append(text);
		String language = "";
		try{
			ArrayList<Language> lang = messageDetector.getProbabilities();
			language = lang.get(0).lang;
			//language = messageDetector.detect();
		}
		catch (Exception e)
		{
			//dirty hack as language detector fails to detect messages containing only emoticons and urls
			if(text.startsWith("http:")){
				return false;
			}
			else{
				return true;
			}
		}
		return language.equals(filter);

	}
	
	public static void main(String[] args) throws LangDetectException {
		LanguageDetector ld = new LanguageDetector();;
		boolean result = ld.languageFilter("whats up","en");
		System.out.println(result);
	}
	
	
}
