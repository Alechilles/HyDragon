# HyDragon companion portraits

The shared assets in `src/main/resources/Server/Tamework/DynamicIcons/` cover all 21 concrete wild/tamed NPC roles in 13 appearance groups. Capture items and normal/roster command panels use the same mappings with the matching Tamework dynamic-icon development build.

There are 15 images: ice and toxic hydras, stone/fire/ice Rock Drakes, seven Miniwyvern elements, and three Nordic Drake skins. Nordic Drake maps the exact `Skin` values `GreenBalls`, `OldOrange`, and `Cobalt`; orange is its default when attachments are unavailable. Other groups use their role-specific base portrait. No appearance combinations are omitted from the current HyDragon models.

## Regeneration

From the HyDragon repository, use the sibling Animal Husbandry helper to resolve model parents, then the shared Tamework generator. Keep prepared models and render output outside this repository:

```powershell
$work = [IO.Path]::GetFullPath('../CodexDocs/HyDragon/dynamic-icons')
python "../Alec's Animal Husbandry!/scripts/tools/spawner-icons/prepare_models.py" --batch-manifest scripts/spawner-icons/hydragon.batch.json --output-dir "$work/prepared"
python ../alecstamework/scripts/tools/generate_spawner_icon_overrides.py --batch-manifest "$work/prepared/effective.batch.json" --asset-root "$work/staging" --dynamic-icons-output-dir "$work/staging/Server/Tamework/DynamicIcons" --dynamic-icon-id-prefix HyDragon_DynamicIcon --manifest-out "$work/manifest.json" --renderer-jobs-out "$work/jobs.json"
```

Miniwyvern entries use `cameraScale: 1.25` and `cameraAutoFramePadding: 2` in
`hydragon.batch.json`. They retain the shared rotation `[22.5, 45, 22.5]`,
auto-framing, and six attempts. The lower starting scale keeps the full
snout, wings, and tail in the raw render before alpha framing.

In Blockbench, run **Run Tamework Dynamic Icon Batch (From Jobs JSON)** with `jobs.json`. Use the current Tamework batch renderer and Hytale Models plugin. Inspect all 15 images, check references and role coverage, then copy the generated PNGs and dynamic configs from staging into `src/main/resources` together. Rebuild HyDragon's JAR for client installation.

The manifest limits each pass to 100 combinations. If future models add attachment slots, select the most visible features before expanding the batch.

Keep the Miniwyvern camera settings aligned across the wild and six elemental forms when regenerating their portraits. Inspect the full silhouette; transparent borders alone do not prove the model was not cropped during rendering.
