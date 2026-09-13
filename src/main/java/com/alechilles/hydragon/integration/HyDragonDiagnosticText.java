package com.alechilles.hydragon.integration;

import com.hypixel.hytale.server.core.Message;

/** Maps HyDragon-owned diagnostic prose to deferred translations at the command boundary. */
public final class HyDragonDiagnosticText {
    private HyDragonDiagnosticText() {
    }

    public static Message configIssue(String issue) {
        if (issue.equals("Assets not loaded")) return diagnostic("config.assetsNotLoaded");
        if (issue.startsWith("Unknown encounter phase: ")) {
            return diagnostic("config.unknownEncounterPhase")
                    .param("phase", issue.substring("Unknown encounter phase: ".length()));
        }
        if (issue.startsWith("Semantic is unknown: ")) {
            return diagnostic("config.unknownSemantic")
                    .param("semantic", issue.substring("Semantic is unknown: ".length()));
        }
        if (issue.startsWith("DragonSpecies[") && issue.contains("] references encounter ")) {
            int split = issue.indexOf("] references encounter ");
            String encounter = issue.substring(split + "] references encounter ".length());
            int target = encounter.indexOf(" targeting ");
            if (target >= 0) {
                return diagnostic("config.referencesEncounterTarget")
                        .param("species", issue.substring("DragonSpecies[".length(), split))
                        .param("encounter", encounter.substring(0, target))
                        .param("target", encounter.substring(target + " targeting ".length()));
            }
            return diagnostic("config.referencesEncounter")
                    .param("species", issue.substring("DragonSpecies[".length(), split))
                    .param("encounter", encounter);
        }
        if (issue.startsWith("DragonSpecies[") && issue.contains("] references missing encounter ")) {
            int split = issue.indexOf("] references missing encounter ");
            return diagnostic("config.referencesMissingEncounter")
                    .param("species", issue.substring("DragonSpecies[".length(), split))
                    .param("encounter", issue.substring(split + "] references missing encounter ".length()));
        }
        if (issue.startsWith("Encounter[") && issue.contains("] references missing species ")) {
            int split = issue.indexOf("] references missing species ");
            return diagnostic("config.referencesMissingSpecies")
                    .param("encounter", issue.substring("Encounter[".length(), split))
                    .param("species", issue.substring(split + "] references missing species ".length()));
        }
        int scope = issue.indexOf(": ");
        if (scope > 0 && issue.substring(0, scope).endsWith("]")) {
            return diagnostic("config.scoped")
                    .param("scope", issue.substring(0, scope))
                    .param("reason", configIssue(issue.substring(scope + 2)));
        }
        int upgrade = issue.indexOf("].");
        if (issue.startsWith("EssenceBondAura.Upgrades[") && upgrade > 0) {
            return diagnostic("config.scoped")
                    .param("scope", issue.substring(0, upgrade + 1))
                    .param("reason", configIssue(issue.substring(upgrade + 2)));
        }
        return configDetail(issue);
    }

    public static Message featureReason(String reason) {
        if (reason.equals("ready")) return diagnostic("feature.ready");
        if (reason.equals("Tamework API unavailable")) return diagnostic("feature.apiUnavailable");
        if (reason.startsWith("missing capabilities ")) {
            return diagnostic("feature.missingCapabilities")
                    .param("capabilities", reason.substring("missing capabilities ".length()));
        }
        if (reason.equals("Tamework public API is unavailable")) return diagnostic("bridge.publicApiUnavailable");
        if (reason.startsWith("Tamework API bootstrap failed: ")) {
            return diagnostic("bridge.bootstrapFailed")
                    .param("exception", reason.substring("Tamework API bootstrap failed: ".length()));
        }
        if (reason.startsWith("Tamework API capability ") && reason.contains(" failed: ")) {
            int split = reason.indexOf(" failed: ");
            return diagnostic("bridge.capabilityFailed")
                    .param("phase", reason.substring("Tamework API capability ".length(), split))
                    .param("exception", reason.substring(split + " failed: ".length()));
        }
        if (reason.equals("Tamework bonded-companion API is null")) return diagnostic("bridge.bondedApiNull");
        if (reason.equals("Tamework bonded-companion availability is null")) {
            return diagnostic("bridge.bondedAvailabilityNull");
        }
        if (reason.startsWith("Tamework bonded-companion availability refresh failed: ")) {
            return diagnostic("bridge.bondedAvailabilityRefreshFailed")
                    .param("exception", reason.substring(
                            "Tamework bonded-companion availability refresh failed: ".length()));
        }
        return technical(reason);
    }

    public static Message diagnosticsReason(String reason) {
        if (reason.startsWith("diagnostics read failed: ")) {
            return diagnostic("diagnostics.readFailed")
                    .param("exception", reason.substring("diagnostics read failed: ".length()));
        }
        return featureReason(reason);
    }

    public static Message localPersistenceReason(String reason) {
        if (reason.equals("state store not initialized")) return diagnostic("persistence.notInitialized");
        if (reason.startsWith("state store open failed: ")) {
            return diagnostic("persistence.openFailed")
                    .param("exception", reason.substring("state store open failed: ".length()));
        }
        if (reason.equals("unsupported store schema or quarantined store")) {
            return diagnostic("persistence.unsupportedSchema");
        }
        return technical(reason);
    }

    public static Message orphanAction(String action) {
        if (action.equals("verify Tamework ownership, then reconcile or quarantine the profile extension")) {
            return diagnostic("persistence.orphanProfileExtension");
        }
        return technical(action);
    }

    private static Message configDetail(String detail) {
        if (detail.equals("WildRoleIds must contain at least one full-dragon role")) {
            return diagnostic("config.wildRoleRequired");
        }
        if (detail.equals("Miniwyvern is Soul Bond-exclusive and cannot be a full-dragon wild role")) {
            return diagnostic("config.miniwyvernWildRole");
        }
        if (detail.equals("RoleId must map form to its tamed role")) return diagnostic("config.roleIdMapping");
        if (detail.equals("PlayerEligibility.RequiredMountMode must be AVATAR_FLIGHT for high-altitude encounters")) {
            return diagnostic("config.highAltitudeMountMode");
        }
        if (detail.equals("Admission.GlobalLimit must be positive and at least PerRegionLimit")) {
            return diagnostic("config.globalLimit");
        }
        if (detail.equals("Grounding.BuildupSourceIds must declare a lure source followed by at least one stagger source")) {
            return diagnostic("config.buildupSources");
        }
        if (detail.equals("WildRoleIds contains a blank role")) return diagnostic("config.blankRole");
        if (detail.equals("ParticleAndSoundIds cannot contain blank values")) {
            return diagnostic("config.blankValues");
        }
        if (detail.equals("RegionsAndAltitude requires finite MinY <= MaxY")) {
            return diagnostic("config.finiteAltitudeRange");
        }
        if (detail.equals("Mount.Mode must be NONE, GROUND, or AVATAR_FLIGHT")) {
            return diagnostic("config.mountModeOptions");
        }
        if (detail.equals("WeatherPredicate.Mode must be AnyOf or AllOf")) {
            return diagnostic("config.weatherModeOptions");
        }
        if (detail.startsWith("SiphonCooldownMs must be at least ") && detail.endsWith(" for a siphon")) {
            return diagnostic("config.siphonCooldownMinimum")
                    .param("minimum", detail.substring("SiphonCooldownMs must be at least ".length(),
                            detail.length() - " for a siphon".length()));
        }
        if (detail.startsWith("Lightning requires ") && detail.endsWith(" in PassiveEffects")) {
            return diagnostic("config.lightningPassiveEffect")
                    .param("effect", detail.substring("Lightning requires ".length(),
                            detail.length() - " in PassiveEffects".length()));
        }
        if (detail.startsWith("MiniwyvernArchetypes is missing ")) {
            return diagnostic("config.missingArchetypes")
                    .param("archetypes", detail.substring("MiniwyvernArchetypes is missing ".length()));
        }
        if (detail.endsWith(" contains a null asset")) {
            return diagnostic("config.nullAsset")
                    .param("family", detail.substring(0, detail.length() - " contains a null asset".length()));
        }
        if (detail.endsWith(" contains an asset with a blank id")) {
            return diagnostic("config.blankAssetId")
                    .param("family", detail.substring(0, detail.length() - " contains an asset with a blank id".length()));
        }
        if (detail.startsWith("MiniwyvernArchetypes has duplicate RoleId ") && detail.contains(" for ")
                && detail.contains(" and ")) {
            String values = detail.substring("MiniwyvernArchetypes has duplicate RoleId ".length());
            int first = values.indexOf(" for ");
            int second = values.indexOf(" and ", first + " for ".length());
            return diagnostic("config.duplicateRoleId")
                    .param("roleId", values.substring(0, first))
                    .param("firstArchetype", values.substring(first + " for ".length(), second))
                    .param("secondArchetype", values.substring(second + " and ".length()));
        }
        if (detail.contains(" contains unsupported source: ")) {
            return splitValue(detail, " contains unsupported source: ", "config.unsupportedValue");
        }
        if (detail.contains(" contains duplicate source: ")) {
            return splitValue(detail, " contains duplicate source: ", "config.duplicateSource");
        }
        if (detail.contains(" contains duplicate ")) {
            return splitValue(detail, " contains duplicate ", "config.duplicate");
        }
        if (detail.contains(" has duplicate ")) {
            return splitValue(detail, " has duplicate ", "config.duplicate");
        }
        if (detail.endsWith(" must not be empty")) return field(detail, " must not be empty", "config.notEmpty");
        if (detail.endsWith(" must not be null")) return field(detail, " must not be null", "config.notNull");
        if (detail.endsWith(" must not be blank when configured")) {
            return field(detail, " must not be blank when configured", "config.notBlankWhenConfigured");
        }
        if (detail.endsWith(" must be positive and finite")) {
            return field(detail, " must be positive and finite", "config.positiveFinite");
        }
        if (detail.endsWith(" must be non-negative")) return field(detail, " must be non-negative", "config.nonNegative");
        if (detail.endsWith(" must be positive")) return field(detail, " must be positive", "config.positive");
        if (detail.endsWith(" must be finite")) return field(detail, " must be finite", "config.finite");
        if (detail.endsWith(" must be a namespaced identifier")) {
            return field(detail, " must be a namespaced identifier", "config.namespacedIdentifier");
        }
        if (detail.contains(" is required when ") && detail.endsWith(" is configured")) {
            int split = detail.indexOf(" is required when ");
            return diagnostic("config.requiredWhenConfigured")
                    .param("field", detail.substring(0, split))
                    .param("condition", detail.substring(split + " is required when ".length(),
                            detail.length() - " is configured".length()));
        }
        if (detail.contains(" is required for ")) {
            return splitValue(detail, " is required for ", "config.requiredFor");
        }
        if (detail.endsWith(" is required")) return field(detail, " is required", "config.required");
        if (detail.endsWith(" is missing")) return field(detail, " is missing", "config.missing");
        if (detail.contains(" is missing ")) return splitValue(detail, " is missing ", "config.missingValue");
        if (detail.contains(" is only valid for ")) return splitValue(detail, " is only valid for ", "config.onlyValidFor");
        if (detail.contains(" must be between ")) return between(detail);
        if (detail.contains(" must be in ")) return splitValue(detail, " must be in ", "config.range");
        if (detail.contains(" must be a finite fraction in ")) {
            return splitValue(detail, " must be a finite fraction in ", "config.finiteFraction");
        }
        if (detail.contains(" must not exceed ")) return splitValue(detail, " must not exceed ", "config.maximum");
        if (detail.contains(" must be at least ")) return splitValue(detail, " must be at least ", "config.minimum");
        if (detail.contains(" must be one of ")) return splitValue(detail, " must be one of ", "config.oneOf");
        if (detail.contains(" must be ")) return splitValue(detail, " must be ", "config.expected");
        if (detail.endsWith(" does not name a configured PassiveModifiers semantic")) {
            return field(detail, " does not name a configured PassiveModifiers semantic", "config.configuredSemantic");
        }
        if (detail.endsWith(" must name an EntityEffect asset")) {
            return field(detail, " must name an EntityEffect asset", "config.entityEffect");
        }
        return technical(detail);
    }

    private static Message field(String detail, String suffix, String key) {
        return diagnostic(key).param("field", detail.substring(0, detail.length() - suffix.length()));
    }

    private static Message splitValue(String detail, String separator, String key) {
        int split = detail.indexOf(separator);
        return diagnostic(key)
                .param("field", detail.substring(0, split))
                .param("value", detail.substring(split + separator.length()));
    }

    private static Message between(String detail) {
        int split = detail.indexOf(" must be between ");
        String bounds = detail.substring(split + " must be between ".length());
        int separator = bounds.indexOf(" and ");
        if (separator < 0) return technical(detail);
        return diagnostic("config.between")
                .param("field", detail.substring(0, split))
                .param("minimum", bounds.substring(0, separator))
                .param("maximum", bounds.substring(separator + " and ".length()));
    }

    private static Message diagnostic(String key) {
        return HyDragonMessages.diagnostic(key);
    }

    private static Message technical(String detail) {
        return diagnostic("technical").param("detail", detail);
    }
}
