package recommendation;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

import org.nicta.lr.util.EDirectionType;

public class MessageInteraction {

	private Map<Long,MessageInteractionHolder> _interactions = null;
	
	public MessageInteraction() {
		_interactions = new HashMap<Long,MessageInteractionHolder>();
	}
	
	/*
	 * Add an interaction and associated message
	 */
	public void addInteraction(long uid1, long uid2, EDirectionType dir, String message) {
		
		long inter;
		long outer;
		// only need to handle incoming and outgoing
		if (dir == EDirectionType.INCOMING) {
			inter = uid1;
			outer = uid2;
		} else {
			inter = uid2;
			outer = uid1;
		}
		
		MessageInteractionHolder interactions1 = _interactions.get(inter);
		if (interactions1 == null) {
			interactions1 = new MessageInteractionHolder();
		}
		interactions1.add(outer, message);
		_interactions.put(inter, interactions1);
		
	}
	
	/*
	 * Return interaction for user
	 */
	public MessageInteractionHolder getInteractions(Long uid) {
		return _interactions.get(uid);
	}
	
	/*
	 * Return all user interactions
	 */
	public Map<Long,MessageInteractionHolder> getAllInteractions() {
		return _interactions;
	}	
	
	/*
	 *  View all interactions between users
	 */
	public void viewInteractions(){
		for (Long uid : _interactions.keySet()){
			System.out.println(uid);
			for (Long pid : _interactions.get(uid).getMessageInteractions().keySet()){
				System.out.print("  (" + pid + ":");
				for (String m : _interactions.get(uid).getMessageInteractions().get(pid)){
					System.out.print(m + " - ");
				}		
				System.out.println(")");
			}
		}
	}
	
	/*
	 * Quick test
	 */
	public static void main(String[] args) {						
		MessageInteraction mi = new MessageInteraction();
		mi.addInteraction(new Long(100), new Long(200), EDirectionType.OUTGOING, "test 1");
		mi.addInteraction(new Long(100), new Long(200), EDirectionType.OUTGOING, "test 2");
		mi.addInteraction(new Long(100), new Long(200), EDirectionType.OUTGOING, "test 3");
		mi.addInteraction(new Long(100), new Long(200), EDirectionType.OUTGOING, "test 4");
		mi.addInteraction(new Long(100), new Long(200), EDirectionType.OUTGOING, "test 5");
		mi.addInteraction(new Long(100), new Long(200), EDirectionType.OUTGOING, "test 6");
		mi.addInteraction(new Long(700), new Long(200), EDirectionType.OUTGOING, "test 7");
		mi.addInteraction(new Long(100), new Long(300), EDirectionType.OUTGOING, "test 1");
		mi.viewInteractions();
	}
	
}
