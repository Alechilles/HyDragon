# Building HyDragon

HyDragon and Tamework share the Gradle workspace in the parent `Modding`
directory. It builds HyDragon against Tamework's current project output and
links both projects' asset files into one development server workspace.

## Development hot reload

From the `HyDragon` directory:

```powershell
..\gradlew.bat stageAllModAssets
..\gradlew.bat runAllMods
```

Leave `runAllMods` running while you edit either project. The workspace stages
the two mods together, so their linked asset files can hot-reload in the same
server session.

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
..\gradlew.bat -Phytale_patchline=pre-release -Phytale_version=<game-version> runAllMods
```
