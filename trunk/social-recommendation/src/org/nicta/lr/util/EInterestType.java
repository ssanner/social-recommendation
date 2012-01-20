package org.nicta.lr.util;

public enum EInterestType { 
	GROUPS(1), ACTIVITIES(2), BOOKS(3), FAVORITE_ATHLETES(4), FAVORITE_TEAMS(5), 
	INSPIRATIONAL_PEOPLE(6), INTERESTS(7), GENERAL_LIKES(8), MOVIES(9), MUSIC(10), 
	SPORTS(11), TELEVISION(12), SCHOOL(13), WORK(14);
	private final int _index;
	EInterestType(int index) {
		this._index = index;
	}
	public int index() {
		return _index;
	}
}