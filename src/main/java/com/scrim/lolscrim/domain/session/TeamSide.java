package com.scrim.lolscrim.domain.session;

public enum TeamSide {
	BLUE,
	RED;

	public TeamSide opposite() {
		return this == BLUE ? RED : BLUE;
	}
}
