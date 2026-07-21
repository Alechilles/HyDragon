package com.alechilles.hydragon.encounters;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;

/** Persistent entity marker that makes encounter spawning and restart reconciliation idempotent. */
public final class HyDragonEncounterComponent implements Component<EntityStore> {
    public static final BuilderCodec<HyDragonEncounterComponent> CODEC = BuilderCodec.builder(
            HyDragonEncounterComponent.class,
            HyDragonEncounterComponent::new)
            .append(new KeyedCodec<>("EncounterId", Codec.STRING),
                    HyDragonEncounterComponent::setEncounterId,
                    HyDragonEncounterComponent::getEncounterId)
            .add()
            .append(new KeyedCodec<>("DefinitionId", Codec.STRING),
                    HyDragonEncounterComponent::setDefinitionId,
                    HyDragonEncounterComponent::getDefinitionId)
            .add()
            .build();

    private static volatile ComponentType<EntityStore, HyDragonEncounterComponent> componentType;

    private String encounterId;
    private String definitionId;

    public HyDragonEncounterComponent() {
    }

    public HyDragonEncounterComponent(String encounterId, String definitionId) {
        this.encounterId = requiredText(encounterId, "encounterId");
        this.definitionId = requiredText(definitionId, "definitionId");
    }

    public static synchronized ComponentType<EntityStore, HyDragonEncounterComponent> register(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (componentType == null) {
            componentType = plugin.getEntityStoreRegistry().registerComponent(
                    HyDragonEncounterComponent.class,
                    "HyDragonEncounter",
                    CODEC);
        }
        return componentType;
    }

    public static ComponentType<EntityStore, HyDragonEncounterComponent> getComponentType() {
        return componentType;
    }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }

    public boolean matches(String expectedEncounterId) {
        return expectedEncounterId != null && expectedEncounterId.equals(encounterId);
    }

    @Override
    public HyDragonEncounterComponent clone() {
        return new HyDragonEncounterComponent(encounterId, definitionId);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
