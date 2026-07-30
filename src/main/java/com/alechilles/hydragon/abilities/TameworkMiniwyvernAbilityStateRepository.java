package com.alechilles.hydragon.abilities;

import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Merges ability scheduler state into HyDragon's shared bonded extension document. */
public final class TameworkMiniwyvernAbilityStateRepository
        implements MiniwyvernAbilityStateRepository {
    private static final BondedMiniwyvernExtensionCodec CODEC =
            new BondedMiniwyvernExtensionCodec();

    private final BondedMiniwyvernExtensionStore extensions;
    private final ConcurrentHashMap<Key, Observed> observed = new ConcurrentHashMap<>();

    public TameworkMiniwyvernAbilityStateRepository(
            BondedMiniwyvernExtensionStore extensions) {
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    @Override
    public synchronized LoadResult load(UUID ownerUuid, String profileId) {
        Key key = key(ownerUuid, profileId);
        Observed cached = observed.get(key);
        if (cached != null) {
            return LoadResult.loaded(cached.document().abilityState());
        }
        BondedMiniwyvernExtensionStore.ReadResult result = extensions
                .load(key.ownerUuid(), key.profileId())
                .toCompletableFuture()
                .getNow(null);
        if (result == null) return LoadResult.unavailable();
        if (result.status() == BondedMiniwyvernExtensionStore.ReadStatus.LOADED) {
            observed.put(key, new Observed(result.document(), result.revision()));
            return LoadResult.loaded(result.document().abilityState());
        }
        observed.remove(key);
        return result.status() == BondedMiniwyvernExtensionStore.ReadStatus.MISSING
                ? LoadResult.missing()
                : LoadResult.unavailable();
    }

    @Override
    public synchronized boolean save(
            UUID ownerUuid,
            String profileId,
            MiniwyvernAbilityState state) {
        Key key = key(ownerUuid, profileId);
        Objects.requireNonNull(state, "state");
        Observed current = observed.get(key);
        if (current == null) return false;
        BondedMiniwyvernExtensionDocument desired;
        try {
            desired = current.document().withAbilityState(state);
        } catch (RuntimeException failure) {
            return false;
        }
        String operationId = operationId(key, current.revision(), CODEC.encode(desired));
        BondedMiniwyvernExtensionStore.WriteResult result = extensions.compareAndSet(
                        key.ownerUuid(), key.profileId(), operationId,
                        current.revision(), desired)
                .toCompletableFuture()
                .getNow(null);
        if (result == null) return false;
        if (result.status() == BondedMiniwyvernExtensionStore.WriteStatus.APPLIED) {
            observed.put(key, new Observed(result.document(), result.revision()));
            return true;
        }
        return result.status() == BondedMiniwyvernExtensionStore.WriteStatus.CONFLICT
                && refreshAfterConflict(key, desired);
    }

    private boolean refreshAfterConflict(
            Key key,
            BondedMiniwyvernExtensionDocument desired) {
        BondedMiniwyvernExtensionStore.ReadResult current = extensions
                .load(key.ownerUuid(), key.profileId())
                .toCompletableFuture()
                .getNow(null);
        if (current == null
                || current.status() != BondedMiniwyvernExtensionStore.ReadStatus.LOADED) {
            observed.remove(key);
            return false;
        }
        observed.put(key, new Observed(current.document(), current.revision()));
        return CODEC.encode(desired).equals(CODEC.encode(current.document()));
    }

    private static String operationId(Key key, long revision, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = key.ownerUuid() + "\n" + key.profileId()
                    + "\n" + revision + "\n" + payload;
            return "hydragon:ability-state:"
                    + HexFormat.of().formatHex(digest.digest(
                    source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Key key(UUID ownerUuid, String profileId) {
        return new Key(
                Objects.requireNonNull(ownerUuid, "ownerUuid"),
                requiredText(profileId, "profileId"));
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record Key(UUID ownerUuid, String profileId) {
    }

    private record Observed(BondedMiniwyvernExtensionDocument document, long revision) {
        private Observed {
            Objects.requireNonNull(document, "document");
            if (revision < 0L) throw new IllegalArgumentException("revision cannot be negative");
        }
    }
}
