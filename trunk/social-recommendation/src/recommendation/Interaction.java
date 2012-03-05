package recommendation;

import java.util.*;

import org.nicta.lr.util.*;

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
	public void addInteraction(long uid1, long uid2, EDirectionType dir) {
		
		if (dir == EDirectionType.INCOMING || dir == EDirectionType.BIDIR) {
			Set<Long> interactions1 = _interactions.get(uid1);
			if (interactions1 == null) {
				interactions1 = new HashSet<Long>();
				_interactions.put(uid1, interactions1);
			}
			interactions1.add(uid2);
		}
		
		if (dir == EDirectionType.OUTGOING || dir == EDirectionType.BIDIR) {
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
	
	public void addAllInteractions(Interaction i) {
		for (Map.Entry<Long,Set<Long>> e : i._interactions.entrySet()) { // i's keyset
			long uid = e.getKey();
			Set<Long> to_add = e.getValue();
			if (to_add == null || to_add.size() == 0)
				continue;			
			Set<Long> interactions = this._interactions.get(uid); // this's keys
			if (interactions == null) {
				interactions = new HashSet<Long>();
				this._interactions.put(uid, interactions);
			}
			interactions.addAll(to_add);
		}
	}
	
	public void removeAllInteractions(Interaction i) {
		for (Map.Entry<Long,Set<Long>> e : i._interactions.entrySet()) { // i's keyset
			long uid = e.getKey();
			Set<Long> to_remove = e.getValue();
			if (to_remove == null || to_remove.size() == 0)
				continue;			
			Set<Long> interactions = this._interactions.get(uid); // this's keys
			if (interactions == null || interactions.size() == 0) 
				continue;
			interactions.removeAll(to_remove);
		}
	}
	
	public void retainAllInteractions(Interaction i) {
		Set<Long> EMPTY_SET = new HashSet<Long>();
		for (Map.Entry<Long,Set<Long>> e : i._interactions.entrySet()) { // i's keyset
			long uid = e.getKey();
			Set<Long> to_retain = e.getValue();
			if (to_retain == null) { 
				// Nothing to retain here
				to_retain = EMPTY_SET;
			}
			Set<Long> interactions = this._interactions.get(uid); // this's keys
			if (interactions == null || interactions.size() == 0) 
				continue;
			interactions.retainAll(to_retain);
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Interaction i = new Interaction();
		//Direction dir = Direction.BIDIRECTIONAL;
		//Direction dir = Direction.INCOMING;
		EDirectionType dir = EDirectionType.OUTGOING;
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
