package com.alechilles.hydragon.abilities;

/** Idempotent persistence boundary for Miniwyvern ability scheduler state. */
public interface MiniwyvernAbilityStateRepository {
    LoadResult load(String profileId);

    /** Returns false when durable persistence is unavailable; callers must fail closed. */
    boolean save(String profileId, MiniwyvernAbilityState state);

    record LoadResult(Status status, MiniwyvernAbilityState state) {
        public LoadResult {
            java.util.Objects.requireNonNull(status, "status");
            if ((status == Status.LOADED) != (state != null)) {
                throw new IllegalArgumentException("Only LOADED results may contain state");
            }
        }

        public static LoadResult loaded(MiniwyvernAbilityState state) {
            return new LoadResult(Status.LOADED, java.util.Objects.requireNonNull(state, "state"));
        }

        public static LoadResult missing() { return new LoadResult(Status.MISSING, null); }

        public static LoadResult unavailable() { return new LoadResult(Status.UNAVAILABLE, null); }
    }

    enum Status { LOADED, MISSING, UNAVAILABLE }
}
