package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.BondedCompanionLeaseView;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Selects a confirmed active avatar-flight dragon from the shared bonded roster. */
public final class ActiveBondedDragonResolver {
    public static final String DRAGON_HORN_ROSTER = "hydragon:dragon_horn";
    public static final String FULL_DRAGON_FAMILY = "hydragon:full_dragons";

    /**
     * Resolves one stable profile ID without consulting generic population or
     * profile persistence. A profile is eligible only when its exact live
     * bonded lease is confirmed in the candidate world.
     */
    public Optional<String> resolve(
            HyDragonConfigRepository.Snapshot configs,
            UUID ownerUuid,
            String worldKey,
            List<BondedCompanionProfileView> profiles) {
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        String world = requiredText(worldKey, "worldKey");
        Objects.requireNonNull(profiles, "profiles");
        if (!configs.isValid()) return Optional.empty();

        Set<String> avatarFlightRoles = avatarFlightRoles(configs);
        return profiles.stream()
                .filter(Objects::nonNull)
                .filter(profile -> matchesAuthority(profile, ownerUuid, world))
                .filter(profile -> avatarFlightRoles.contains(profile.roleId()))
                .map(BondedCompanionProfileView::profileId)
                .min(String::compareTo);
    }

    private static Set<String> avatarFlightRoles(
            HyDragonConfigRepository.Snapshot configs) {
        Set<String> roles = new HashSet<>();
        for (DragonSpeciesConfig species : configs.species().values()) {
            if (species != null
                    && species.getMount().getMode()
                    == DragonSpeciesConfig.MountMode.AVATAR_FLIGHT) {
                roles.addAll(species.getTamedRoleIdByWildRole().values());
            }
        }
        roles.removeIf(value -> value == null || value.isBlank());
        return Set.copyOf(roles);
    }

    private static boolean matchesAuthority(
            BondedCompanionProfileView profile,
            UUID ownerUuid,
            String worldKey) {
        if (!ownerUuid.equals(profile.ownerUuid())
                || !DRAGON_HORN_ROSTER.equals(profile.rosterId())
                || !FULL_DRAGON_FAMILY.equals(profile.familyId())
                || profile.state() != BondedCompanionStateView.ACTIVE) {
            return false;
        }
        BondedCompanionLeaseView lease = profile.activeLease();
        return lease != null
                && lease.liveNpcUuid() != null
                && worldKey.equals(lease.worldKey());
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
