package org.nicta.lr.util;

public enum EDirectionType {
	INCOMING(1), OUTGOING(2), BIDIR(3); //, COMPLETE
	private final int _index;
	EDirectionType(int index) {
		this._index = index;
	}
	public int index() {
		return _index;
	}
}
