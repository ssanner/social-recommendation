package org.nicta.lr.util;

import java.util.*;

public class Interaction {

	private Map<Long,Set<Long>> _interactions = null;
	
	public Interaction() {
		_interactions = new HashMap<Long,Set<Long>>();
	}
	
	/** Maintain interactions between
	 * 
	 * @param uid1
	 * @param uid2
	 */		
	public void addInteraction(long uid1, long uid2, Direction dir) {
		
		if (dir == Direction.INCOMING || dir == Direction.BIDIR) {
			Set<Long> interactions1 = _interactions.get(uid1);
			if (interactions1 == null) {
				interactions1 = new HashSet<Long>();
				_interactions.put(uid1, interactions1);
			}
			interactions1.add(uid2);
		}
		
		if (dir == Direction.OUTGOING || dir == Direction.BIDIR) {
			Set<Long> interactions2 = _interactions.get(uid2);
			if (interactions2 == null) {
				interactions2 = new HashSet<Long>();
				_interactions.put(uid2, interactions2);
			}
			interactions2.add(uid1);
		}
	}
	
	public Set<Long> getInteractions(Long uid) {
		return _interactions.get(uid);
	}
	
	public Map<Long,Set<Long>> getAllInteractions() {
		return _interactions;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Interaction i = new Interaction();
		//Direction dir = Direction.BIDIRECTIONAL;
		//Direction dir = Direction.INCOMING;
		Direction dir = Direction.OUTGOING;
		i.addInteraction(1, 2, dir);
		i.addInteraction(3, 4, dir);
		i.addInteraction(1, 3, dir);
		System.out.println("1: " + i.getInteractions(1l));
		System.out.println("2: " + i.getInteractions(2l));
		System.out.println("3: " + i.getInteractions(3l));
		System.out.println("4: " + i.getInteractions(4l));
		System.out.println("5: " + i.getInteractions(5l));
	}

}
