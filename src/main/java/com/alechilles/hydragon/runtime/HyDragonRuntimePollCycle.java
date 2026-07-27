package com.alechilles.hydragon.runtime;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Runs each installed bounded runtime task without coupling their failures. */
public final class HyDragonRuntimePollCycle implements Runnable {
    private final Runnable encounters;
    private final Runnable abilities;
    private final Runnable sagaRecovery;
    private final FailureListener failures;

    public HyDragonRuntimePollCycle(
            @Nullable Runnable encounters,
            @Nullable Runnable abilities,
            @Nullable Runnable sagaRecovery,
            @Nonnull FailureListener failures) {
        this.encounters = encounters;
        this.abilities = abilities;
        this.sagaRecovery = sagaRecovery;
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    @Override
    public void run() {
        run(Work.ENCOUNTERS, encounters);
        run(Work.MINIWYVERN_ABILITIES, abilities);
        run(Work.SAGA_RECOVERY, sagaRecovery);
    }

    public boolean hasWork() {
        return encounters != null || abilities != null || sagaRecovery != null;
    }

    public boolean encountersEnabled() {
        return encounters != null;
    }

    private void run(Work work, Runnable task) {
        if (task == null) return;
        try {
            task.run();
        } catch (RuntimeException | LinkageError failure) {
            report(new Failure(work, failure));
        }
    }

    private void report(Failure failure) {
        try {
            failures.onFailure(failure);
        } catch (RuntimeException | LinkageError ignored) {
            // Poll diagnostics must not suppress the remaining feature work.
        }
    }

    public enum Work { ENCOUNTERS, MINIWYVERN_ABILITIES, SAGA_RECOVERY }

    /** Immutable evidence for one contained poll failure. */
    public record Failure(@Nonnull Work work, @Nonnull Throwable cause) {
        public Failure {
            work = Objects.requireNonNull(work, "work");
            cause = Objects.requireNonNull(cause, "cause");
        }
    }

    @FunctionalInterface
    public interface FailureListener {
        void onFailure(@Nonnull Failure failure);
    }
}
