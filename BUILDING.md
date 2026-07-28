# Building and installing HyDragon

HyDragon uses the same Maven 3.9.9 wrapper and `install-plugin` workflow as
Tamework. HyDragon compiles against the packaged Tamework 3.0.0 JAR, so build
and install Tamework before building HyDragon.

## Quick development install

From the HyDragon repository in PowerShell:

```powershell
..\alecstamework\mvnw.cmd package "-Dmaven.test.skip=true" -Pinstall-plugin
.\mvnw.cmd package "-Dmaven.test.skip=true" -Pinstall-plugin
```

The HyDragon command packages the current `Common/`, `Server/`, Java, and
manifest content, then copies `HyDragon v0.2.1.jar` to both:

- `%APPDATA%\Hytale\install\release\package\game\latest\Server\mods`
- `%APPDATA%\Hytale\UserData\Mods`

Tamework's first command installs its matching 3.0.0 JAR to the same runtime.

## Verified build

Use `verify` before a release or when changing Java/integration behavior:

```powershell
.\mvnw.cmd clean verify
```

This runs asset validation, unit tests, packaged-JAR checks, and the packaged
HyDragon/Tamework integration test. It builds but does not install the JAR.
Run the quick install command afterward to deploy the verified sources.

## Prerelease install

Pass `-Dprerelease=true` to both projects:

```powershell
..\alecstamework\mvnw.cmd package "-Dmaven.test.skip=true" -Pinstall-plugin -Dprerelease=true
.\mvnw.cmd package "-Dmaven.test.skip=true" -Pinstall-plugin -Dprerelease=true
```

This selects the prerelease Hytale server and userdata paths while preserving
the same two-destination install contract.
