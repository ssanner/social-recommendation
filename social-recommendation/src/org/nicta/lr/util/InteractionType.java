/**
 * 
 */
package org.nicta.lr.util;

// TODO: Add ALL_LIKES, ALL_COMMENTS, REAL_TAGS (real), ~REAL_TAGS (virtual)
// TODO: Add other interactions (likes) of sufficient size... similar principle as groups?
// TODO: Generate graph of smooth group size scaling?
// TODO: Complete co-likes
public enum InteractionType {
	FRIENDS, 
	GROUPS_SZ_2_2, GROUPS_SZ_2_5, GROUPS_SZ_6_10, GROUPS_SZ_11_25, GROUPS_SZ_26_50, 
	GROUPS_SZ_51_100, GROUPS_SZ_101_500, GROUPS_SZ_500_PLUS, 
	LINK_LIKES, LINK_COMMENTS, 
	POST_LIKES, POST_COMMENTS, POST_TAGS,
	PHOTO_LIKES, PHOTO_COMMENTS, PHOTO_TAGS,
	VIDEO_LIKES, VIDEO_COMMENTS, VIDEO_TAGS
}