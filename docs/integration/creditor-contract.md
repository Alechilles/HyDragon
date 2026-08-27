# Creditor Integration Contract

- Primary mod: HyDragon 1.1.5
- External library: Creditor 1.1.1 (CurseForge file `8724366`)
- Dependency: embedded in the HyDragon release JAR; no separate mod dependency
- Exposed hook: Creditor receives HyDragon's plugin during `setup()` and `start()`
- Failure behavior: HyDragon logs a warning and continues if Creditor cannot initialize
- Credit metadata: `Server/Credits/hydragon.json`
- Distribution notice: Creditor's MIT license and source tag are included under
  `META-INF`; the upstream 1.1.1 credit asset currently says GPLv3 even though
  both the tagged source license and CurseForge project metadata say MIT
- Validation: Gradle resolves file `8724366`; unit and packaged-artifact suites
  pass; the shaded JAR keeps HyDragon's manifest and contains the same
  `Creditor.class` as the resolved artifact plus HyDragon's credit metadata

Live `/credits` presentation remains a release acceptance check.
