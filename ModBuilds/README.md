# ModBuilds

**Generated — do not edit.** Every jar here is written by a mod's `exportJar` Gradle task, which
runs automatically after `build`.

The point is that all five finished mod jars sit in one folder ready to copy into a Minecraft
instance's `mods/`, instead of being scattered across five `build/libs/` directories.

## What lands here

Only the remapped mod jar for each of the five mods. Deliberately absent:

- **`-sources.jar`** files — they live in `build/libs/` and must never end up in a mods folder.
- **TrarnCore** — it is bundled *inside* each mod jar via jar-in-jar, so it is never installed on
  its own. Exporting it here would only invite dropping it into `mods/` by mistake.

Each export clears older jars for that same mod first, so a `mod_version` bump cannot leave a
stale copy behind for you to copy by accident.

## Refreshing

```bash
# one mod
cd ../ContainerUtil && ./gradlew build

# all of them
cd .. && for m in ClaimViz SimDistance EasyPortalLinker ContainerUtil RSwitch; do
  (cd "$m" && ./gradlew build)
done
```

## Installing

Copy all five together. They share the bundled TrarnCore library, and mixing a jar from before a
library change with jars from after it is exactly the mismatch that produces
`NoClassDefFoundError` at startup.

```bash
cp *.jar ~/.var/app/com.modrinth.ModrinthApp/data/ModrinthApp/profiles/"Fabric 1.21.11"/mods/
```
