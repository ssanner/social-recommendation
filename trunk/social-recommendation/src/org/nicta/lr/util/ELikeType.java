/**
 * 
 */
package org.nicta.lr.util;

// TODO: Add ALL, ALL_REAL
public enum ELikeType { 
	LINK(1), POST(2), PHOTO(3), VIDEO(4), ALL(5);
	private final int _index;
	ELikeType(int index) {
		this._index = index;
	}
	public int index() {
		return _index;
	}
}