package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.integration.HyDragonFeature;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import java.util.Locale;
import javax.annotation.Nonnull;

/** Elemental essence interaction for re-attuning the owner's existing Miniwyvern profile. */
public final class HyDragonMiniwyvernAttuneInteraction extends HyDragonServerInteraction {
    public static final String TYPE_ID = "HyDragonMiniwyvernAttune";
    public static final BuilderCodec<HyDragonMiniwyvernAttuneInteraction> CODEC = BuilderCodec.builder(
            HyDragonMiniwyvernAttuneInteraction.class,
            HyDragonMiniwyvernAttuneInteraction::new,
            SimpleInteraction.CODEC
    )
            .<String>append(new KeyedCodec<>("ArchetypeId", Codec.STRING),
                    (interaction, value) -> interaction.archetypeId = value,
                    interaction -> interaction.archetypeId)
            .documentation("Required target archetype matching a HyDragon Miniwyvern archetype asset.")
            .add()
            .build();

    private String archetypeId;

    protected HyDragonMiniwyvernAttuneInteraction() {
        super();
    }

    public HyDragonMiniwyvernAttuneInteraction(String id, String archetypeId) {
        super(id);
        this.archetypeId = archetypeId;
    }

    public String getArchetypeId() {
        return archetypeId == null ? "" : archetypeId.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    protected boolean isRequestValid() {
        return !getArchetypeId().isBlank();
    }

    @Nonnull
    @Override
    protected HyDragonFeature requiredFeature() {
        return HyDragonFeature.MINIWYVERN_ATTUNEMENT;
    }

    @Nonnull
    @Override
    protected String actionLabel() {
        return "Miniwyvern attunement to " + getArchetypeId();
    }
}
