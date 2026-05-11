# Shattered Pixel Dungeon

[Shattered Pixel Dungeon](https://shatteredpixel.com/shatteredpd/) is an open-source traditional roguelike dungeon crawler with randomized levels and enemies, and hundreds of items to collect and use. It's based on the [source code of Pixel Dungeon](https://github.com/00-Evan/pixel-dungeon-gradle), by [Watabou](https://www.watabou.ru).

Shattered Pixel Dungeon currently compiles for Android, iOS, and Desktop platforms. You can find official releases of the game on:

## ELEC5618 Assignment 3 Modifications

This repository is based on the official Shattered Pixel Dungeon v3.0.2 release:
https://github.com/00-Evan/shattered-pixel-dungeon/releases/tag/v3.0.2

The assignment version keeps the original game structure and adds three software-quality and gameplay-support features:

- mob behaviour logging
- save-file checksum validation
- monster combat information display

The implementation is intentionally integrated into the existing game flow instead of being added as a separate tool. The logger hooks into `Mob` state changes, the checksum validator hooks into `Dungeon` save/load operations, and the combat information is appended to the normal monster description shown by the game's examine/bestiary UI.

### 1. Mob Behaviour Logging

The mob logging feature records important monster AI events while the game is running. Its main implementation is in:

- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/utils/MobLogger.java`
- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mob.java`

`MobLogger` is a small utility class with one public method:

```java
MobLogger.log(Mob mob, String eventType, String details)
```

When called, it creates a single structured log line containing:

- real wall-clock time, formatted as `yyyy-MM-dd HH:mm:ss.SSS`
- current game tick from `Actor.now()`
- monster display name from `mob.name()`
- monster actor id from `mob.id()`
- event type, such as `SPAWN`, `ALERT`, `TARGET_ASSIGN`, or `STATE_TRANSITION`
- a human-readable detail message

The output format is:

```text
[2026-05-11 16:24:22.563] [TICK:32.00] [marsupial rat:5] [ALERT] Mob became alerted.
```

The same log line is written to two places:

- standard output with `System.out.println(logEntry)`, so it appears in Gradle/desktop debug logs
- `mob_behavior.log`, using `FileWriter(LOG_FILE, true)`, so the log is persistent across the run

The file writer uses append mode, which means new mob events are added to the end of the existing log rather than replacing it. The logger also catches all exceptions internally. This is deliberate: logging is a diagnostic feature, so a file-system or formatting failure should not crash or interrupt the game.

The feature is connected to mob behaviour in `Mob.java` at several points:

- Spawn logging: `Mob.onAdd()` logs `SPAWN` the first time a mob is added to the level. The `firstAdded` flag prevents the same mob from being logged as newly spawned more than once after save/load or level bookkeeping.
- Alert logging: `Mob.notice()` logs `ALERT` whenever a mob becomes alerted and shows the alert indicator.
- Target logging: `Mob.act()` compares the previous enemy reference with the current enemy reference. When the target changes, it logs `TARGET_ASSIGN` with the new target name.
- State transition logging: a helper method, `updateState(AiState newState)`, centralizes many AI state assignments. It logs `STATE_TRANSITION` only when the state actually changes, which avoids repeated duplicate entries.

The main AI states tracked by this system are the existing Shattered Pixel Dungeon mob states:

- `Sleeping`
- `Wandering`
- `Hunting`
- `Fleeing`
- `Passive`

This means the log can be used to reconstruct a monster's behaviour timeline: when it spawned, when it noticed the hero, when it selected a target, and how its AI state changed during combat or movement.

### 2. Save-File Checksum Validation

The checksum feature adds integrity checking for saved games. Its main implementation is in:

- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/utils/SaveIntegrityValidator.java`
- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java`

`SaveIntegrityValidator` uses SHA-256 hashes to detect whether a save file has changed unexpectedly. For each save file, it creates a sidecar checksum file using the same path plus the `.sha256` suffix.

For example:

```text
Save file:      game2/game.dat
Checksum file:  game2/game.dat.sha256
```

The validator has two main operations.

`writeChecksum(String saveFilePath)` is called after the game writes a save file. It:

- resolves the save path through `FileUtils.getFileHandle(saveFilePath)`, so it uses the same LibGDX/local file system abstraction as the rest of the game
- checks that the save file exists, is not a directory, and is not empty
- reads the save file through a buffered stream
- calculates a SHA-256 digest using `MessageDigest.getInstance("SHA-256")`
- converts the digest bytes into a lowercase hexadecimal string
- writes the hex digest into `saveFilePath + ".sha256"`

`verifyChecksum(String saveFilePath)` is called before the game loads a save file. It:

- resolves the save file and checksum file
- fails if the save file itself does not exist
- allows loading to continue if no checksum file exists yet
- recalculates the current SHA-256 hash of the save file
- compares the recalculated hash with the saved `.sha256` value
- throws an `IOException` if the checksum exists but does not match

The "missing checksum file" case is treated as a legacy save. This is important because players or testers may already have saves created before the checksum feature was added. Without this compatibility rule, old saves would be rejected even though they were valid.

The feature is integrated in `Dungeon.java`:

- `Dungeon.saveGame(int save)` still builds the normal `Bundle` and writes it with `FileUtils.bundleToFile(...)`
- immediately after the bundle is written, it calls:

```java
SaveIntegrityValidator.writeChecksum(GamesInProgress.gameFile(save));
```

- `Dungeon.loadGame(int save, boolean fullLoad)` verifies the checksum before reading the bundle:

```java
SaveIntegrityValidator.verifyChecksum(GamesInProgress.gameFile(save));
Bundle bundle = FileUtils.bundleFromFile(GamesInProgress.gameFile(save));
```

This placement is the key design point. The checksum is generated only after the save file has been fully written, and validation happens before the save data is trusted and deserialized. If the save file has been manually edited, truncated, or corrupted after the checksum was written, the mismatch is detected before the game restores the dungeon state.

The validator prints diagnostic messages with the `[SAVE-INTEGRITY]` prefix. These messages make it easy to see when a checksum is written, when a checksum is found, and whether validation passed.

### 3. Monster Combat Information Display

The combat information feature adds extra monster statistics to the description shown when a monster is inspected. Its main implementation is in:

- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mob.java`
- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/SPDSettings.java`
- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/windows/WndSettings.java`
- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/journal/Bestiary.java`
- selected monster classes in `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/`
- message files in `core/src/main/assets/messages/actors/`
- setting-label files in `core/src/main/assets/messages/windows/`

The feature is controlled by a new setting:

```java
public static final String KEY_MOB_COMBAT_INFO = "mob_combat_info";
```

`SPDSettings.mobCombatInfo(boolean value)` stores the setting, and `SPDSettings.mobCombatInfo()` reads it. The default value is `true`, so the feature is enabled unless the player turns it off.

The UI for the setting is added to the display tab in `WndSettings.DisplayTab`. A new checkbox, `chkMobCombatInfo`, is created with the localized label `mob_combat_info`. When clicked, it calls:

```java
SPDSettings.mobCombatInfo(checked());
```

The checkbox is laid out with the existing display settings, directly after the fullscreen/power-saver area. This keeps the feature discoverable without creating a new settings page.

The information display itself is added in `Mob.info()`. The original method returned the monster's description and champion modifier descriptions. The assignment version keeps that behaviour, but inserts combat information when both conditions are true:

```java
SPDSettings.mobCombatInfo() && Bestiary.encounterCount(getClass()) > 0
```

This means combat information is shown only when:

- the player has enabled the setting
- the monster has been encountered at least once

That second condition prevents the feature from revealing combat information for completely unknown monsters. The information becomes available after the player has actually encountered that monster type, which keeps the feature closer to the existing bestiary/discovery design.

`Bestiary.encounterCount(Class<?> cls)` was also adjusted to apply existing class conversions before looking up encounter counts. Some enemies use internal classes for variants or summons, and the bestiary already maps those classes back to their normal catalog entries. Applying the same conversion here makes combat-info unlocks consistent with the bestiary.

The generated combat information is built by `Mob.combatInfo()`. It can include:

- damage range
- estimated hit chance against the current hero
- estimated miss chance against the current hero
- attack type
- special ability note

The base `Mob` class defines overridable helper methods:

```java
protected int damageRollMin()
protected int damageRollMax()
protected String attackTypeInfo()
protected String specialAbilityInfo()
```

By default, the damage methods return `-1`, which means the UI displays damage as "varies" for monsters that do not provide a fixed range. The default attack type is melee. Special ability text is omitted unless a monster overrides `specialAbilityInfo()`.

Hit chance is estimated in `Mob.estimatedHitChance(Char defender)`. It uses the same conceptual relationship as the game's attack resolution: monster accuracy comes from `attackSkill(defender)`, and hero evasion comes from `defender.defenseSkill(this)`. The result is converted into a percentage and clamped to `0-100`. Special cases are handled for zero defense, infinite accuracy, and infinite evasion.

Several early or representative monsters override the helper methods so their information is more precise:

- `Rat` adds `1-4` damage.
- `Gnoll` adds `2-5` damage.
- `Crab` adds `1-7` damage and marks its attack as fast melee.
- `Snake` adds `1-4` damage and a special note about high evasion.
- `Slime` adds `1-5` damage.
- `Albino` adds a special note about bleed chance.
- `DM100` adds `2-8` melee damage, ranged magic attack type, and a lightning-zap special note.
- `Guard` adds `4-12` damage, chain-pull attack type, and different special text depending on whether the chain has already been used.
- `GnollGuard` adds reach-based damage information, reach melee attack type, and a special note about its spear range.

All display strings are stored in the normal Shattered Pixel Dungeon message system rather than hard-coded in Java. Generic labels such as `combat_info`, `combat_damage`, `combat_hit`, and `combat_attack_ranged_magic` are stored under `actors.mobs.mob.*`. Monster-specific special notes are stored under the relevant monster message keys, such as `actors.mobs.dm100.combat_special`.

Because the text is routed through `Messages.get(...)`, the feature follows the existing localization pattern. The same combat-info keys were added to the language-specific `actors_*.properties` and `windows_*.properties` files so the game can resolve the new labels consistently across language packs.

### Validation

The project was compiled and run through the desktop target after the feature merge:

```powershell
$env:JAVA_HOME = "D:\USYD\26s1\ELEC5618 Software Quality Engineering\A3\shattered-pixel-dungeon-3.0.2\.codex-jdks\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat desktop:classes
.\gradlew.bat desktop:debug
```

Both commands completed successfully. During `desktop:debug`, the game launched, mob log lines were printed, and save-integrity messages confirmed that checksum verification and checksum writing were being executed.

[![Get it on Google Play](https://shatteredpixel.com/assets/images/badges/gplay.png)](https://play.google.com/store/apps/details?id=com.shatteredpixel.shatteredpixeldungeon)
[![Download on the App Store](https://shatteredpixel.com/assets/images/badges/appstore.png)](https://apps.apple.com/app/shattered-pixel-dungeon/id1563121109)
[![Steam](https://shatteredpixel.com/assets/images/badges/steam.png)](https://store.steampowered.com/app/1769170/Shattered_Pixel_Dungeon/)<br>
[![GOG.com](https://shatteredpixel.com/assets/images/badges/gog.png)](https://www.gog.com/game/shattered_pixel_dungeon)
[![Itch.io](https://shatteredpixel.com/assets/images/badges/itch.png)](https://shattered-pixel.itch.io/shattered-pixel-dungeon)
[![Github Releases](https://shatteredpixel.com/assets/images/badges/github.png)](https://github.com/00-Evan/shattered-pixel-dungeon/releases)

If you like this game, please consider [supporting me on Patreon](https://www.patreon.com/ShatteredPixel)!

There is an official blog for this project at [ShatteredPixel.com](https://www.shatteredpixel.com/blog/).

The game also has a translation project hosted on [Transifex](https://www.transifex.com/shattered-pixel/shattered-pixel-dungeon/).

Note that **this repository does not accept pull requests!** The code here is provided in hopes that others may find it useful for their own projects, not to allow community contribution. Issue reports of all kinds (bug reports, feature requests, etc.) are welcome.

If you'd like to work with the code, you can find the following guides in `/docs`:
- [Compiling for Android.](docs/getting-started-android.md)
    - **[If you plan to distribute on Google Play please read the end of this guide.](docs/getting-started-android.md#distributing-your-apk)**
- [Compiling for desktop platforms.](docs/getting-started-desktop.md)
- [Compiling for iOS.](docs/getting-started-ios.md)
- [Recommended changes for making your own version.](docs/recommended-changes.md)
