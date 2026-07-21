package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.ProfileDataApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TameworkMiniwyvernAbilityStateRepositoryTest {
    @Test
    void distinguishesMissingCorruptAndValidState() {
        MemoryProfileData data = new MemoryProfileData();
        TameworkMiniwyvernAbilityStateRepository repository =
                new TameworkMiniwyvernAbilityStateRepository(data);

        assertEquals(MiniwyvernAbilityStateRepository.Status.MISSING,
                repository.load("profile-1").status());

        data.put("profile-1", "hydragon", "miniwyvern_ability_state", "{}");
        assertEquals(MiniwyvernAbilityStateRepository.Status.UNAVAILABLE,
                repository.load("profile-1").status());

        MiniwyvernAbilityState expected = MiniwyvernAbilityState.empty("fire", 100L);
        assertTrue(repository.save("profile-1", expected));
        MiniwyvernAbilityStateRepository.LoadResult loaded = repository.load("profile-1");
        assertEquals(MiniwyvernAbilityStateRepository.Status.LOADED, loaded.status());
        assertEquals(expected, loaded.state());
    }

    private static final class MemoryProfileData implements ProfileDataApi {
        private final Map<String, String> values = new LinkedHashMap<>();

        @Override public Optional<String> get(String profileId, String namespace, String key) {
            return Optional.ofNullable(values.get(profileId + '|' + namespace + '|' + key));
        }

        @Override public Map<String, String> list(String profileId, String namespace) { return Map.of(); }

        @Override public boolean put(String profileId, String namespace, String key, String value) {
            values.put(profileId + '|' + namespace + '|' + key, value);
            return true;
        }

        @Override public boolean delete(String profileId, String namespace, String key) {
            values.remove(profileId + '|' + namespace + '|' + key);
            return true;
        }
    }
}
