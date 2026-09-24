package com.alechilles.hydragon.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

/** Checks the current combat target without changing target selection. */
public final class BuilderSensorHyDragonTargetAirborne extends BuilderSensorBase {
    public static final String ID = "HyDragonTargetAirborne";

    @Override
    public Sensor build(BuilderSupport support) {
        return new SensorHyDragonTargetAirborne(this, support.getTargetSlot("LockedTarget"));
    }

    @Override
    public BuilderSensorHyDragonTargetAirborne readConfig(JsonElement data) {
        return this;
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Override
    public String getShortDescription() {
        return "Whether LockedTarget is persistently airborne, including its mount when riding.";
    }

    @Override
    public String getLongDescription() {
        return getShortDescription();
    }
}
