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
