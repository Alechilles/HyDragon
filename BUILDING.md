# Building HyDragon

HyDragon and Tamework share the Gradle workspace in the parent `Modding`
directory. It builds HyDragon against Tamework's current project output and
stages both projects' asset packs into one development server workspace.

## Development hot reload

From the `HyDragon` directory, explicitly select the parent workspace as the
Gradle project root:

```powershell
.\gradlew.bat -p .. stageAllModAssets
.\gradlew.bat -p .. runAllMods
```

Running `..\gradlew.bat` without `-p ..` still uses HyDragon's standalone
`settings.gradle`, because Gradle selects its project root from the current
directory rather than the wrapper's location.

Leave `runAllMods` running while you edit either project. The workspace stages
the two mods together, so their asset files can hot-reload in the same server
session.

## Verify before release

Run HyDragon's unit and packaged-artifact suites:

```powershell
.\gradlew.bat clean test packagingTest
```

When working from the shared workspace, the packaging test automatically uses
the Tamework JAR built from the sibling project.

## Prerelease

Pass the matching game version and patchline to the shared workspace command:

```powershell
.\gradlew.bat -p .. -Phytale_patchline=pre-release -Phytale_version=<game-version> runAllMods
```
