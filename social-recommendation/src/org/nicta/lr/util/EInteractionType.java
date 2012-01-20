/**
 * 
 */
package org.nicta.lr.util;

// TODO: Add ALL_LIKES, ALL_COMMENTS, REAL_TAGS (real), ~REAL_TAGS (virtual)
// TODO: Add other interactions (likes) of sufficient size... similar principle as groups?
// TODO: Generate graph of smooth group size scaling?
// TODO: Complete co-likes
public enum EInteractionType {
	FRIENDS(1), 
	LINK_LIKES(2), LINK_COMMENTS(3), 
	POST_LIKES(4), POST_COMMENTS(5), POST_TAGS(6),
	PHOTO_LIKES(7), PHOTO_COMMENTS(8), PHOTO_TAGS(9),
	VIDEO_LIKES(10), VIDEO_COMMENTS(11), VIDEO_TAGS(12),
	// add from here
	REAL(13), VIRTUAL(14),
	ALL_LIKES(15), ALL_COMMENTS(16), ALL_TAGS(17),
	ALL_LINK(18), ALL_POST(19), ALL_PHOTO(20), ALL_VIDEO(21),
	ALL_INTER(22);
	private final int _index;
	EInteractionType(int index) {
		this._index = index;
	}
	public int index() {
		return _index;
	}
}