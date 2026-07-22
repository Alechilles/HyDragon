package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkRuntimeDiagnostics;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/** {@code /hydragon status} capability/config diagnostics. */
public final class HyDragonStatusCommand extends AbstractAsyncCommand {
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
    @Nonnull
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        String action = parseAction(context.getInputString());
        if (!action.equals("status")) {
            context.sendMessage(HyDragonMessages.statusUsage());
            return CompletableFuture.completedFuture(null);
        }
        TameworkBridge bridge = bridgeSupplier.get();
        if (bridge == null) {
            context.sendMessage(HyDragonMessages.statusUnavailable());
            return CompletableFuture.completedFuture(null);
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
        return CompletableFuture.completedFuture(null);
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
