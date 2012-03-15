package recommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class MessageInteractionHolder {

	private HashMap<Long, Set<String>> messages = null;
	
	public MessageInteractionHolder() {
		messages = new HashMap<Long, Set<String>>();
	}
	
	/*
	 * Add new message for user uid
	 */
	public void add(Long uid, String message){
		Set<String> currentMessages = messages.get(uid);
		if (currentMessages == null){
			currentMessages = new HashSet<String>(); 			
		} 
		
		StringTokenizer tokens = new StringTokenizer(message, " ");
		while (tokens.hasMoreTokens()){
			String word = tokens.nextToken().toLowerCase();
			currentMessages.add(word);
		}
		
		messages.put(uid, currentMessages);
	}
	
	/*
	 * Return message interactions
	 */
	public HashMap<Long, Set<String>> getMessageInteractions(){
		return messages;
	}
	
	/*
	 * View message interactions
	 */
	public void viewMessages(){
		for (Long uid: messages.keySet()){
			System.out.print(uid + " [");
			for (String s : messages.get(uid)){
				System.out.print(s + " - ");
			}
			System.out.println("]");
		}
	}
	
	/*
	 * Quick test
	 */
	public static void main(String[] args) {
		MessageInteractionHolder mh = new MessageInteractionHolder();
		mh.add(new Long(100), "sup");
		mh.add(new Long(100), "sup bra");
		mh.add(new Long(100), "hi");
		mh.add(new Long(1100), "derp");
		mh.add(new Long(12), "stse");
		mh.add(new Long(100), "test");
		mh.viewMessages();
	}
	
}
