# Spatial Inversion Esper Add-on

A Re:AcademyCraft add-on adding the **"Spatial Manipulation"** esper category with 8 level-5 skills.

**Playstyle:** Offense, defense, mobility, battlefield control.
**Minecraft:** 1.20.1 | **Forge:** 47.4.10 | **Java:** 17 | **Re:AcademyCraft:** 1.5.0
**Mod ID:** `spatialinversion` | **Depends on:** `academy` (both sides)

---

## Skills Overview

All 8 skills are **level 5** in the Spatial Manipulation category.

| # | Skill | Type | Description |
|---|-------|------|-------------|
| 1 | **Spatial Link** | TOGGLE | Two connected anchors; projectiles & items pass between them. Place anchor A (first press), then anchor B (second press, max 20 blocks from A). Link lasts 8s. Press again to collapse early. |
| 2 | **Vector Redirect** | INSTANT | Temporary portal ~2.4 blocks ahead redirects incoming projectiles in your current look direction. Lasts 3s. |
| 3 | **Spatial Exchange** | INSTANT | Swap positions with a targeted living entity (range 18). Both destinations checked for safety. Bosses resist. |
| 4 | **Distortion Field** | INSTANT | Field around you (radius 6) bends incoming projectile trajectories outward. Lasts 5s. |
| 5 | **Inversion Strike** | TOGGLE | Charge a spatial attack (6 tick delay). Emerges from unexpected angle ~3.5 blocks from target. Press again to release early. Moderate-high damage + push. |
| 6 | **Fold Step** | INSTANT | Create two portals and teleport between them (max 16 blocks, horizontal + vertical). Safe destination required. |
| 7 | **Spatial Cage** | TOGGLE | Four portals in a ring (radius 8) around target area. Redirects projectiles; soft wall reduces outward velocity by 65%. Lasts 6s. |
| 8 | **Absolute Inversion** | INSTANT (Ultimate) | Two massive surfaces (13×9) at aim point & behind you reverse spatial relationship. Capacity 12 redirections. Lasts 5s. Bosses resist forced effects. |

---

## Configuration

Config file: `config/spatialinversion-common.toml` (created on first run)

All values are tunable per-skill:
- CP cost, overload cost, cooldown, duration, range/radius
- Global: max object speed, momentum retention, max redirects per object, safe landing drop distance, extra eligible entities, field bend strength

**Default extra eligible entities:** `academy:coin, academy:silbarn` (Re:AcademyCraft's coin and silbarn entities)

---

## Building

**Prerequisites:** Java 17, internet access (for ForgeGradle dependencies)

```bash
./gradlew build
```

Output: `build/libs/spatial-inversion-addon-forge-1.20.1-1.0.0.jar`

### GitHub Actions

The repository includes a workflow (`.github/workflows/build.yml`) that:
1. Runs on push to `main` and manual dispatch
2. Installs Temurin JDK 17
3. Runs the zero-particle check (`tools/check_no_particles.py`)
4. Builds with Gradle wrapper
5. Uploads the JAR as an artifact

---

## Installation

### For Server + Clients (Required on both sides)

1. **Install Re:AcademyCraft 1.5.0** (mod ID `academy`) in `mods/` folder
   - Download from CurseForge

2. **Install this add-on** (`spatial-inversion-addon-forge-1.20.1-1.0.0.jar`) in `mods/` folder

3. **Run Forge 1.20.1 (47.4.10)** on server and all clients

### From GitHub Actions Artifact

1. Create a new GitHub repository
2. Upload the **entire contents** of this project (including `.github/` folder and `libs/`)
3. Commit to `main` branch
4. Go to **Actions** → **Build Spatial Inversion Add-on** → **Run workflow**
5. Download the `spatial-inversion-addon` artifact when build completes
6. Place the JAR in your `mods/` folder alongside Re:AcademyCraft

---

## Zero-Particle Guarantee

This add-on uses **zero particles** — all visuals are pure geometry rendered via `RenderLevelStageEvent.AFTER_PARTICLES` with Tesselator + BufferUploader.

Forbidden terms are blocked by `tools/check_no_particles.py` (runs in CI).

---

## Known Limitations & Open Questions

| Issue | Status |
|-------|--------|
| **Re:AcademyCraft's own beams (Railgun, Meltdowner, etc.) cannot be redirected** | Known limitation — they are instant rays, not entities. Only vanilla `Projectile` subclasses, `ItemEntity`, and configured extra entities are affected. |
| **Late-joining players may not see already-active effects** | Effects are sent on spawn; no state sync for late joiners. |
| **Shader pack compatibility** | Not tested; custom shaders may affect additive glow rendering. |
| **Smoothness of pulled players in multiplayer** | Depends on server tick rate and latency; velocity sync uses `hurtMarked = true`. |
| **`/aim learn` with add-on skills** | Not verified whether base command accepts add-on skill IDs. |
| **AbstractHurtingProjectile xPower/yPower/zPower rotation** | Requires access transformer or reflection; not implemented. |
| **Client effect cleanup on logout/dimension change** | Handled via event listeners, but edge cases may exist. |

---

## Credits

- **Re:AcademyCraft** by PinChan4273 (GPLv3) — base mod & public API
- **Original AcademyCraft** by PinChan4273 & contributors
- This add-on: GPLv3

---

## License

GPLv3 — see `LICENSE` and `NOTICE.md` in the JAR.