package com.scrim.lolscrim.domain.riot;

public record RiotLookupResult<T>(T value, RiotSyncStatus status) {

	public static <T> RiotLookupResult<T> ok(T value) {
		return new RiotLookupResult<>(value, RiotSyncStatus.OK);
	}

	public static <T> RiotLookupResult<T> notFound() {
		return new RiotLookupResult<>(null, RiotSyncStatus.NOT_FOUND);
	}

	public static <T> RiotLookupResult<T> rateLimited() {
		return new RiotLookupResult<>(null, RiotSyncStatus.RATE_LIMITED);
	}

	public static <T> RiotLookupResult<T> error() {
		return new RiotLookupResult<>(null, RiotSyncStatus.ERROR);
	}

	public boolean isOk() {
		return status == RiotSyncStatus.OK;
	}
}
