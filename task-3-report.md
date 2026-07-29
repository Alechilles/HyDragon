# Miniwyvern role-swap assets — Task 3 handoff

## Delivered

- Replaced the legacy single tamed role and interaction with exactly seven form-specific assets: Wild, Nature, Toxic, Fire, Void, Lightning, and Ice.
- Each role inherits `Template_Wyvern_Mini_Flying_Tamed`, retains the old companion/follow/defend/bite values, selects its form appearance, and points to its own interaction config.
- Each interaction is scoped to its source role, preserves Feed and ModeCycle, and supplies the six directed non-self transforms. Every transform declares `IsTamed`, `PlayerIsOwner`, and a matching held stack with `Quantity: 8`; it applies `SetRole` with `ChangeAppearance: true` before `RemoveItemsHand`.
- Expanded the sole `hydragon:soulbound_mini` roster and companion role allowlists to exactly those seven roles.
- Added `Wyvern_Mini_Toxic`, using the supplied `Miniwyvern_Toxic.png` texture under the existing Miniwyvern model-parent convention.
- Updated the focused Python wiring contract and `BundledConfigAssetContractTest`.

## Validation evidence

- `python` JSON parsing assertion: passed for all seven interaction files and all 42 directed transforms; confirms owner/tamed gates, six entries per source, `ChangeAppearance`, and no Water/Wind interaction files.
- `git diff --check`: passed.
- `./mvnw -q -Dexec.skip=true -Dtest=BundledConfigAssetContractTest#miniwyvernRoleSwapAssetsCoverEveryNonSelfDestinationAtExactCost test`: passed.
- The complete `BundledConfigAssetContractTest` class currently fails only in its separate archetype-texture contract because `Miniwyvern_Neutral.png` is absent. This is outside Task 3's role/interaction scope.
- `python scripts/validate_assets.py`: reaches the role/interaction checks without Task 3 errors, but still fails on the same in-progress archetype appearance family (neutral/water/wind). These are owned by the role-driven form/ability work, not Task 3.

## Integration blockers / risks

1. The currently installed Tamework source's `SetRoleEffect` codec only has `Role` and `RoleParam`; it does not yet decode `ChangeAppearance`. The assets intentionally include the requested field and require the adjacent role-effect codec/runtime task before a normal asset-codec validation can pass.
2. There is no `Server/Item/Items/Ingredient/Draconic_Essence_Toxic.json`. Toxic’s transforms intentionally reference `Draconic_Essence_Toxic` to preserve the requested distinct matching-essence behavior. Mapping Toxic to the existing Nature essence would create two indistinguishable interaction predicates and make one destination unreachable. The item/form asset owner must add the Toxic essence.
3. Tamework's current `ItemsInHand.Quantity` contract is a **minimum** stack size. Therefore a held stack above eight will satisfy the configured `Quantity: 8` gate and have eight removed. Exact-stack enforcement requires a Tamework schema/runtime extension; it cannot be represented by the current asset shape.
4. No exact locked HytaleNpcAssetTools project profile is present in this worktree, so profile/snapshot/candidate validation could not be run. The deterministic local JSON and focused Maven checks above were used instead.

## Prompt/state matrix

| Source interaction | Prompt condition | Action | Target state | Reset/target loss | Evidence |
| --- | --- | --- | --- | --- | --- |
| `HyDragonIntWyvernMini_{Wild,Nature,Toxic,Fire,Void,Lightning,Ice}` | Tamed owner holding exactly 8 matching destination essences | Set role, then remove held stack | Destination role and appearance | No cooldown/state mutation; ordinary companion lifecycle remains Tamework-owned | Static JSON/contract test; live runtime pending codec task |

No commit was created.
