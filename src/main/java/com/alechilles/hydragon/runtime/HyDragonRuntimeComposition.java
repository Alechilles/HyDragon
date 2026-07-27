package com.alechilles.hydragon.runtime;

import com.alechilles.hydragon.integration.FeatureGate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Owns independently gated HyDragon feature runtimes.
 *
 * <p>One failed installation is reported without rolling back unrelated
 * runtimes. Shutdown closes only successful installations in reverse order.</p>
 */
public final class HyDragonRuntimeComposition implements AutoCloseable {
    private final Map<Slot, AutoCloseable> started = new LinkedHashMap<>();
    private final FailureListener failures;

    public HyDragonRuntimeComposition(@Nonnull FailureListener failures) {
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /** Installs one runtime only when its complete feature gate is available. */
    @Nullable
    public synchronized <T extends AutoCloseable> T install(
            @Nonnull Slot slot,
            @Nullable FeatureGate gate,
            @Nonnull Supplier<T> installer) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(installer, "installer");
        if (gate == null || !gate.available()) return null;
        if (started.containsKey(slot)) {
            throw new IllegalStateException("runtime slot is already installed: " + slot);
        }
        try {
            T runtime = Objects.requireNonNull(installer.get(), "installer returned null");
            started.put(slot, runtime);
            return runtime;
        } catch (RuntimeException | LinkageError failure) {
            report(new Failure(slot, Phase.INSTALL, failure));
            return null;
        }
    }

    /** Returns the exact feature slots whose installation succeeded. */
    @Nonnull
    public synchronized Set<Slot> startedSlots() {
        return Set.copyOf(started.keySet());
    }

    @Override
    public synchronized void close() {
        List<Map.Entry<Slot, AutoCloseable>> entries =
                new ArrayList<>(started.entrySet());
        started.clear();
        for (int index = entries.size() - 1; index >= 0; index--) {
            close(entries.get(index));
        }
    }

    private void close(Map.Entry<Slot, AutoCloseable> entry) {
        try {
            entry.getValue().close();
        } catch (Exception | LinkageError failure) {
            report(new Failure(entry.getKey(), Phase.CLOSE, failure));
        }
    }

    private void report(Failure failure) {
        try {
            failures.onFailure(failure);
        } catch (RuntimeException | LinkageError ignored) {
            // Diagnostics must not turn an isolated feature failure into rollback.
        }
    }

    public enum Slot {
        BONDED_GAMEPLAY,
        MINIWYVERN_ABILITIES,
        DYNAMIC_ENCOUNTERS
    }

    public enum Phase { INSTALL, CLOSE }

    /** Immutable startup/shutdown failure reported with its isolated slot. */
    public record Failure(
            @Nonnull Slot slot,
            @Nonnull Phase phase,
            @Nonnull Throwable cause) {
        public Failure {
            slot = Objects.requireNonNull(slot, "slot");
            phase = Objects.requireNonNull(phase, "phase");
            cause = Objects.requireNonNull(cause, "cause");
        }
    }

    @FunctionalInterface
    public interface FailureListener {
        void onFailure(@Nonnull Failure failure);
    }
}
