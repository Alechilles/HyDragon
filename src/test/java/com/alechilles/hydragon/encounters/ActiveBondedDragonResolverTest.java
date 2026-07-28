package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionLeaseView;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavioral coverage for selecting one confirmed active bonded full dragon. */
final class ActiveBondedDragonResolverTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000222");

    @Test
    void resolvesOnlyAnActiveFullDragonProjectionInTheCandidateWorld() throws Exception {
        ActiveBondedDragonResolver resolver = new ActiveBondedDragonResolver();
        HyDragonConfigRepository.Snapshot configs = snapshot(
                species("hydragon:nordic", "Nordic_Tamed", "AVATAR_FLIGHT"));
        BondedCompanionProfileView eligible = profile(
                "profile-eligible", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                "Nordic_Tamed", BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID());

        assertEquals(
                "profile-eligible",
                resolver.resolve(configs, OWNER, "Default", List.of(eligible)).orElseThrow());
    }

    @Test
    void rejectsWrongRosterFamilyOwnerStateWorldAndUnconfirmedProjection() throws Exception {
        ActiveBondedDragonResolver resolver = new ActiveBondedDragonResolver();
        HyDragonConfigRepository.Snapshot configs = snapshot(
                species("hydragon:nordic", "Nordic_Tamed", "AVATAR_FLIGHT"));

        List<BondedCompanionProfileView> ineligible = List.of(
                profile("wrong-roster", OWNER, "hydragon:full_dragons", "Nordic_Tamed",
                        BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID(), "other:roster"),
                profile("mini", OWNER, "hydragon:soulbound_mini", "Nordic_Tamed",
                        BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID()),
                profile("other-owner", OTHER_OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Nordic_Tamed", BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID()),
                profile("stored", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Nordic_Tamed", BondedCompanionStateView.STORED, null, null),
                profile("wrong-world", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Nordic_Tamed", BondedCompanionStateView.ACTIVE, "Hub", UUID.randomUUID()),
                profile("unconfirmed", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Nordic_Tamed", BondedCompanionStateView.ACTIVE, "Default", null));

        assertTrue(resolver.resolve(configs, OWNER, "Default", ineligible).isEmpty());
    }

    @Test
    void requiresAConfiguredAvatarFlightRoleAndSelectsDeterministically() throws Exception {
        ActiveBondedDragonResolver resolver = new ActiveBondedDragonResolver();
        HyDragonConfigRepository.Snapshot configs = snapshot(
                species("hydragon:ground", "Ground_Tamed", "GROUND"),
                species("hydragon:nordic", "Nordic_Tamed", "AVATAR_FLIGHT"));
        List<BondedCompanionProfileView> profiles = List.of(
                profile("profile-z", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Nordic_Tamed", BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID()),
                profile("profile-ground", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Ground_Tamed", BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID()),
                profile("profile-a", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Nordic_Tamed", BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID()),
                profile("unknown-role", OWNER, ActiveBondedDragonResolver.FULL_DRAGON_FAMILY,
                        "Unknown_Tamed", BondedCompanionStateView.ACTIVE, "Default", UUID.randomUUID()));

        assertEquals(
                "profile-a",
                resolver.resolve(configs, OWNER, "Default", profiles).orElseThrow());
    }

    private static BondedCompanionProfileView profile(
            String profileId,
            UUID owner,
            String family,
            String role,
            BondedCompanionStateView state,
            String world,
            UUID liveNpcUuid) {
        return profile(profileId, owner, family, role, state, world, liveNpcUuid,
                ActiveBondedDragonResolver.DRAGON_HORN_ROSTER);
    }

    private static BondedCompanionProfileView profile(
            String profileId,
            UUID owner,
            String family,
            String role,
            BondedCompanionStateView state,
            String world,
            UUID liveNpcUuid,
            String roster) {
        BondedCompanionLeaseView lease = state == BondedCompanionStateView.ACTIVE
                ? new BondedCompanionLeaseView(
                        "lease-" + profileId, liveNpcUuid, world, 10L, 0L)
                : null;
        return new BondedCompanionProfileView(
                profileId, owner, roster, family, role, "Dragon", "Dragon", null,
                3L, state, false, state == BondedCompanionStateView.ACTIVE, false,
                Map.of(), lease, 0L, null);
    }

    private static HyDragonConfigRepository.Snapshot snapshot(
            DragonSpeciesConfig... species) {
        return new HyDragonConfigRepository.Snapshot(
                java.util.Arrays.stream(species).collect(java.util.stream.Collectors.toMap(
                        DragonSpeciesConfig::getId, value -> value)),
                Map.of(), Map.of(), List.of());
    }

    private static DragonSpeciesConfig species(
            String id,
            String tamedRole,
            String mountMode) throws Exception {
        DragonSpeciesConfig species = construct(DragonSpeciesConfig.class);
        set(species, "id", id);
        set(species, "wildRoleIds", new String[] { id + "_wild" });
        set(species, "tamedRoleIdByWildRole", Map.of(id + "_wild", tamedRole));
        set(species, "difficultyId", "T3");
        set(species, "dropListId", id + "_drops");
        set(species.getMount(), "mode", mountMode);
        if ("AVATAR_FLIGHT".equals(mountMode)) {
            set(species.getMount(), "avatarFlightConfigId", id + "_flight");
        }
        set(species.getPresentation(), "localizationPrefix", id + ".name");
        return species;
    }

    private static <T> T construct(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
