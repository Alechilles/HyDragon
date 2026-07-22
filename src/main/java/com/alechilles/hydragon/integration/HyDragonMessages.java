package com.alechilles.hydragon.integration;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import java.util.Objects;

/** Stable player-facing translation keys backed by Server/Languages catalogs. */
public final class HyDragonMessages {
    private static final String PREFIX = "server.messages.";

    private HyDragonMessages() {
    }

    public static Message soulBondClaimed() { return translated("soulBond.claimed"); }
    public static Message soulBondAlreadyClaimed() { return translated("soulBond.alreadyClaimed"); }
    public static Message soulBondUnavailable() { return translated("soulBond.unavailable"); }
    public static Message attunementSuccess() { return translated("attunement.success"); }
    public static Message attunementSame() { return translated("attunement.same"); }
    public static Message attunementUnavailable() { return translated("attunement.unavailable"); }
    public static Message repairSuccess() { return translated("repair.success"); }
    public static Message repairInvalid() { return translated("repair.invalid"); }
    public static Message refundUnavailable() { return translated("refund.unavailable"); }
    public static Message refundNone() { return translated("refund.none"); }
    public static Message refundRecovered() { return translated("refund.recovered"); }
    public static Message refundNoSpace() { return translated("refund.noSpace"); }
    public static Message refundPending() { return translated("refund.pending"); }
    public static Message vesselUnavailable() { return translated("vessel.unavailable"); }
    public static Message statusUsage() { return translated("status.usage"); }
    public static Message statusUnavailable() { return translated("status.unavailable"); }
    public static Message statusTitle(String version) {
        return translated("status.title").param("version", version);
    }
    public static Message statusConfig(
            Message state, int species, int archetypes, int encounters, int issues) {
        return translated("status.config")
                .param("state", state)
                .param("species", species)
                .param("archetypes", archetypes)
                .param("encounters", encounters)
                .param("issues", issues);
    }
    public static Message statusConfigIssue(String issue) {
        return translated("status.configIssue").param("issue", issue);
    }
    public static Message statusConfigMore(int count) {
        return translated("status.configMore").param("count", count);
    }
    public static Message statusRejectedReload(int issues) {
        return translated("status.rejectedReload").param("issues", issues);
    }
    public static Message statusTamework(String range, String apiVersion, int capabilities) {
        return translated("status.tamework")
                .param("range", range)
                .param("apiVersion", apiVersion)
                .param("capabilities", capabilities);
    }
    public static Message statusFeature(String feature, Message state, String reason) {
        return translated("status.feature")
                .param("feature", feature)
                .param("state", state)
                .param("reason", reason);
    }
    public static Message statusTameworkPersistence(
            String status, long queue, String population, String resilience) {
        return translated("status.tameworkPersistence")
                .param("status", status)
                .param("queue", queue)
                .param("population", population)
                .param("resilience", resilience);
    }
    public static Message statusDiagnosticsIssue(String reason) {
        return translated("status.diagnosticsIssue").param("reason", reason);
    }
    public static Message statusLocalPersistence(
            Message state,
            int players,
            int profiles,
            int encounters,
            int pendingProfileProjections,
            int quarantined,
            int reconcile,
            int orphanedLinks) {
        return translated("status.localPersistence")
                .param("state", state)
                .param("players", players)
                .param("profiles", profiles)
                .param("encounters", encounters)
                .param("pendingProfileProjections", pendingProfileProjections)
                .param("quarantined", quarantined)
                .param("reconcile", reconcile)
                .param("orphanedLinks", orphanedLinks);
    }
    public static Message statusOrphan(String kind, String identity, String action) {
        return translated("status.orphan")
                .param("kind", kind)
                .param("identity", identity)
                .param("action", action);
    }
    public static Message statusOrphanMore(int count) {
        return translated("status.orphanMore").param("count", count);
    }
    public static Message statusLocalPersistenceIssue(String reason) {
        return translated("status.localPersistenceIssue").param("reason", reason);
    }
    public static Message statusStateReady() { return translated("status.state.ready"); }
    public static Message statusStateInvalid() { return translated("status.state.invalid"); }
    public static Message statusStateDisabled() { return translated("status.state.disabled"); }
    public static Message statusStateUnavailable() { return translated("status.state.unavailable"); }
    public static Message statusStateReadWrite() { return translated("status.state.readWrite"); }
    public static Message statusStateReadOnly() { return translated("status.state.readOnly"); }

    /** Server-side resolution for non-client sinks and locale contract tests. */
    public static String resolve(String language, String key) {
        String messageId = messageId(key);
        I18nModule module = I18nModule.get();
        return module == null ? messageId : module.getMessage(language, messageId);
    }

    private static Message translated(String key) {
        return Message.translation(messageId(key));
    }

    private static String messageId(String key) {
        String normalized = Objects.requireNonNull(key, "key").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("key is required");
        return PREFIX + normalized;
    }
}
