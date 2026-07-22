package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkRuntimeDiagnostics;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/** {@code /hydragon status} capability/config diagnostics. */
public final class HyDragonStatusCommand extends AbstractPlayerCommand {
    public static final String PERMISSION = "hydragon.command.status";
    private final Supplier<HyDragonConfigRepository.Snapshot> configSupplier;
    private final Supplier<List<String>> reloadIssuesSupplier;
    private final Supplier<TameworkBridge> bridgeSupplier;
    private final Supplier<HyDragonPersistenceStatus> persistenceSupplier;
    private final String pluginVersion;

    public HyDragonStatusCommand(
            String pluginVersion,
            Supplier<HyDragonConfigRepository.Snapshot> configSupplier,
            Supplier<List<String>> reloadIssuesSupplier,
            Supplier<TameworkBridge> bridgeSupplier,
            Supplier<HyDragonPersistenceStatus> persistenceSupplier) {
        super("hydragon", "server.messages.status.description");
        this.configSupplier = configSupplier;
        this.reloadIssuesSupplier = reloadIssuesSupplier;
        this.bridgeSupplier = bridgeSupplier;
        this.persistenceSupplier = persistenceSupplier;
        this.pluginVersion = pluginVersion;
        requirePermission(PERMISSION);
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        String action = parseAction(context.getInputString());
        if (!action.equals("status")) {
            context.sendMessage(HyDragonMessages.statusUsage());
            return;
        }
        TameworkBridge bridge = bridgeSupplier.get();
        if (bridge == null) {
            context.sendMessage(HyDragonMessages.statusUnavailable());
            return;
        }
        TameworkRuntimeDiagnostics.Snapshot diagnostics = TameworkRuntimeDiagnostics.read(bridge);
        for (var message : HyDragonStatusFormatter.formatMessages(
                pluginVersion,
                configSupplier.get(),
                reloadIssuesSupplier.get(),
                bridge.snapshot(),
                diagnostics,
                persistenceSupplier.get())) {
            context.sendMessage(message);
        }
    }

    private static String parseAction(String input) {
        if (input == null || input.isBlank()) return "status";
        for (String token : input.trim().split("\\s+")) {
            String clean = token.startsWith("/") ? token.substring(1) : token;
            if (!clean.equalsIgnoreCase("hydragon")) {
                return clean.toLowerCase(Locale.ROOT);
            }
        }
        return "status";
    }
}
