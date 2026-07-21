package com.alechilles.hydragon.config;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** MVP Draconic Stone repair settings stored under {@code Server/HyDragon/StoneMaintenance}. */
public final class StoneMaintenanceConfig
        implements JsonAssetWithMap<String, DefaultAssetMap<String, StoneMaintenanceConfig>> {
    private static final BuilderCodec<RepairSettings> REPAIR_CODEC = BuilderCodec.builder(
            RepairSettings.class,
            RepairSettings::new
    )
            .<String>append(new KeyedCodec<>("ItemId", Codec.STRING),
                    (settings, value) -> settings.itemId = value,
                    settings -> settings.itemId)
            .add()
            .<Integer>append(new KeyedCodec<>("Quantity", Codec.INTEGER),
                    (settings, value) -> settings.quantity = value == null ? 1 : value,
                    settings -> settings.quantity)
            .add()
            .build();

    private static final BuilderCodec<FutureExtensions> FUTURE_EXTENSIONS_CODEC = BuilderCodec.builder(
            FutureExtensions.class,
            FutureExtensions::new
    )
            .<Boolean>append(new KeyedCodec<>("DurationEnabled", Codec.BOOLEAN),
                    (settings, value) -> settings.durationEnabled = value != null && value,
                    settings -> settings.durationEnabled)
            .add()
            .<Boolean>append(new KeyedCodec<>("EnergyEnabled", Codec.BOOLEAN),
                    (settings, value) -> settings.energyEnabled = value != null && value,
                    settings -> settings.energyEnabled)
            .add()
            .build();

    public static final AssetBuilderCodec<String, StoneMaintenanceConfig> CODEC = AssetBuilderCodec.builder(
            StoneMaintenanceConfig.class,
            StoneMaintenanceConfig::new,
            Codec.STRING,
            (asset, key) -> asset.assetKey = key,
            asset -> asset.assetKey,
            (asset, data) -> asset.data = data,
            asset -> asset.data
    )
            .documentation("HyDragon MVP death-repair policy. Duration and energy remain fail-closed extensions.")
            .<RepairSettings>append(new KeyedCodec<>("Repair", REPAIR_CODEC),
                    (asset, value) -> asset.repair = value == null ? new RepairSettings() : value,
                    asset -> asset.repair)
            .add()
            .<FutureExtensions>append(new KeyedCodec<>("FutureExtensions", FUTURE_EXTENSIONS_CODEC),
                    (asset, value) -> asset.futureExtensions = value == null ? new FutureExtensions() : value,
                    asset -> asset.futureExtensions)
            .add()
            .build();

    private AssetExtraInfo.Data data;
    String assetKey;
    RepairSettings repair = new RepairSettings();
    FutureExtensions futureExtensions = new FutureExtensions();

    StoneMaintenanceConfig() {
    }

    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (blank(repair.itemId)) errors.add("Repair.ItemId is required");
        if (repair.quantity <= 0) errors.add("Repair.Quantity must be positive");
        if (futureExtensions.durationEnabled || futureExtensions.energyEnabled) {
            errors.add("Duration and energy maintenance are deferred and must remain disabled");
        }
        return List.copyOf(errors);
    }

    public String getAssetKey() { return assetKey; }
    @Override
    public String getId() { return assetKey == null ? "" : assetKey.trim(); }
    public RepairSettings getRepair() { return repair; }
    public FutureExtensions getFutureExtensions() { return futureExtensions; }

    /** Required item and count for a bonded-stone repair. */
    public static final class RepairSettings {
        String itemId = "Revitalizing_Essence";
        int quantity = 1;

        public String getItemId() { return itemId == null ? "" : itemId.trim(); }
        public int getQuantity() { return quantity; }
    }

    /** Reserved flags that deliberately fail validation while the extensions are deferred. */
    public static final class FutureExtensions {
        boolean durationEnabled;
        boolean energyEnabled;

        public boolean isDurationEnabled() { return durationEnabled; }
        public boolean isEnergyEnabled() { return energyEnabled; }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
