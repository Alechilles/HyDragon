package com.alechilles.hydragon.bonded;

import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Narrow public-API port used by HyDragon's bonded extension domain services. */
public interface BondedMiniwyvernExtensionGateway {
    CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
            getMiniwyvernExtension(UUID ownerUuid, String profileId);

    CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
            compareAndSetMiniwyvernExtension(
                    UUID ownerUuid,
                    String profileId,
                    String idempotencyKey,
                    String jsonPayload,
                    long expectedRevision);
}
