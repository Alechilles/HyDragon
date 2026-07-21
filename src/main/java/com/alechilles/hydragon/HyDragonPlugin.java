package com.alechilles.hydragon;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Main entry point for the combined HyDragon Java plugin and asset pack. */
public final class HyDragonPlugin extends JavaPlugin {
    private static HyDragonPlugin instance;

    public HyDragonPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("HyDragon plugin setup complete.");
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("HyDragon enabled.");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("HyDragon disabled.");
        instance = null;
    }

    /** Returns the currently loaded plugin instance, if setup has begun. */
    @Nullable
    public static HyDragonPlugin getInstance() {
        return instance;
    }
}
