package recommendation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/*
 * Holder class for interaction messages and set of users interacted with
 */

public class InteractionMessageHolder {
	
	private Set<Long> interactees = null;
	private ArrayList<String> messages = null;
	
	public InteractionMessageHolder() {
		interactees = new HashSet<Long>();
		messages = new ArrayList<String>();
	}	
	
	public void add(Long uid, String message){
		interactees.add(uid);
		messages.add(message);
	}
	
	public Set<Long> getInteractees(){
		return interactees;
	}
	
	public ArrayList<String> getMessages(){
		return messages;
	}
	
}
