# Spatial Inversion Add-on — Testing Report

**Date:** 2026-11-07
**Environment:** Linux VM (Ubuntu 22.04), Java 11 (build only), Gradle 8.8
**Note:** Full compilation and in-game testing requires Java 17 and internet access for ForgeGradle dependencies.

---

## Summary

| Category | Status | Details |
|----------|--------|---------|
| **Syntax / API Check** | ✅ PASSED | All Java files parse correctly; imports match public API v2 |
| **Zero-Particle Check** | ✅ PASSED | `tools/check_no_particles.py` reports no violations |
| **Gradle Configuration** | ✅ PASSED | Build files valid; plugin management configured for ForgeGradle |
| **Compilation** | ⚠️ NOT VERIFIED | Requires Java 17 (VM has Java 11 only). Build fails at toolchain detection. |
| **In-Game Testing** | ❌ NOT DONE | Requires compiled JAR + Re:AcademyCraft 1.5.0 + Forge 47.4.10 server/client |

---

## What Was Verified (Text/Static Analysis)

### 1. API Compliance
- All imports use only public API packages: `io.github.pinchan4273.reacademycraft.api`, `.mc`, `.forge`, `.client`
- No internal packages referenced
- `ActivationOutcome` enum values used correctly: `PERFORMED`, `STARTED`, `REFUSED_RESOURCE`, `REFUSED_PERMISSION`, `REFUSED_INVALID`
- `ActivationKind` used correctly: `INSTANT`, `TOGGLE`
- `AbilityId.parse()` format validated
- `TreePosition` coordinates within bounds (0-241, 0-123)
- `CategoryDefinition` has 5 non-negative integers for controllable skills per level
- `SkillRequirement` proficiency 0-1

### 2. Registration Flow
- `RegisterAbilitiesEvent` listener on mod event bus
- Category registered first with factor item
- All 8 skills registered with correct level (5), prerequisites, activation kinds, tree positions
- Behaviors registered via `event.behaviors(MOD_ID).behavior(skillId, behavior)`

### 3. Skill Behavior Patterns
All 8 skills follow documented patterns:

| Skill | Pattern | Verified |
|-------|---------|----------|
| Spatial Link | TOGGLE two-step placement with pending state map | ✅ |
| Vector Redirect | INSTANT with portal group + dynamic exit direction | ✅ |
| Spatial Exchange | INSTANT with dual safety check + AcademySafety.teleport | ✅ |
| Distortion Field | INSTANT portal group (no portals) + per-tick velocity bending | ✅ |
| Inversion Strike | TOGGLE with delay tick + exit finder + delayed damage | ✅ |
| Fold Step | INSTANT with SafeSpot search + AcademySafety.teleport | ✅ |
| Spatial Cage | TOGGLE 4 portals + soft wall velocity reduction | ✅ |
| Absolute Inversion | INSTANT dual large surfaces + capacity limit | ✅ |

### 4. Safety & Permissions
- All attacks go through `AcademySafety.attack()`
- All teleports go through `AcademySafety.checkTeleport()` + `teleport()`
- No direct `Entity.hurt()` or `Level.setBlock()` calls
- Boss resistance checks for Ender Dragon / Wither
- Owner projectile filtering (`affectOwn` config)
- PvP/team checks delegated to AcademySafety

### 5. Networking
- `SimpleChannel` with protocol version "1"
- Two packets: `FxSpawnPacket` (clientbound), `FxEndPacket` (clientbound)
- `PacketDistributor.NEAR` with 160-block range
- Client handlers via `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)`

### 6. Client Rendering
- `RenderLevelStageEvent.Stage.AFTER_PARTICLES`
- Frustum culling via `event.getFrustum().isVisible(AABB)`
- Additive glow: `SRC_ALPHA, ONE`, `depthMask(false)`, `disableCull()`
- Solid parts: default blend + `depthMask(true)`
- State restoration in `finally` blocks
- Smooth open/close animation (8 ticks)
- Distance-based LOD ready (step 1/2/4 structure)

### 7. Configuration
- `ForgeConfigSpec` with COMMON spec
- Per-skill: cpCost, overloadCost, cooldownSeconds, durationSeconds, ranges
- Global: maxObjectSpeed, momentumKept, maxRedirectsPerObject, safeLandingMaxDrop, extraEligibleEntities, fieldBendStrength
- Registered via `ModLoadingContext.get().registerConfig()`

### 8. Zero-Particle Rule
- No `ParticleTypes`, `addParticle`, `sendParticles`, etc. in any source
- No `particles/` resource folder
- Automated check script passes

### 9. Resources
- `mods.toml` with correct dependencies (`academy` mandatory, both sides)
- `pack.mcmeta` format 12
- Language files: `en_us.json`, `ja_jp.json` with all skill names/descriptions
- Item model for `spatial_factor`

### 10. Build System
- `settings.gradle` with pluginManagement for ForgeGradle
- `build.gradle` with flatDir for `libs/` API JAR
- `implementation fg.deobf('blank:re-academycraft-forge-1.20.1:1.5.0:api')`
- Gradle wrapper copied from Re:AcademyCraft source
- API JAR committed in `libs/`
- Reproducible builds (fixed timestamps, order)

---

## What Was NOT Verified (Requires Java 17 + Internet)

### Compilation
```bash
# This would work with Java 17:
./gradlew build
```
**Blocked by:** VM only has Java 11. ForgeGradle 6.x requires Java 17 toolchain.

### Unit / Integration Tests
- No automated tests exist (not in scope)
- Would need: `./gradlew test` with GameTest server

### In-Game Testing Checklist
| Test | Status |
|------|--------|
| Mod loads on dedicated server | ❌ |
| Mod loads on client | ❌ |
| Category appears in ability developer | ❌ |
| Factor item in creative tab | ❌ |
| All 8 skills learnable via developer | ❌ |
| Spatial Link: two anchors connect, projectiles pass through | ❌ |
| Vector Redirect: projectiles redirected to look direction | ❌ |
| Spatial Exchange: swap with entity, both safe | ❌ |
| Distortion Field: projectiles bend around player | ❌ |
| Inversion Strike: charge → attack from behind/flank | ❌ |
| Fold Step: teleport to safe spot | ❌ |
| Spatial Cage: 4 portals, soft wall effect | ❌ |
| Absolute Inversion: two giant surfaces, capacity limit | ❌ |
| Multiplayer sync (2+ players) | ❌ |
| Late-joining player sees effects | ❌ |
| Config changes take effect on restart | ❌ |
| Death/logout/dimension change cleans up effects | ❌ |
| Shader pack compatibility | ❌ |
| Performance with 6-12 simultaneous effects | ❌ |

---

## Known Limitations (Documented in README)

1. **Re:AcademyCraft beams cannot be redirected** — Railgun, Meltdowner, Ray Barrage, Electron Bomb, Scatter Bomb, Electron Missile are instant rays, not entities. Only `Projectile` subclasses, `ItemEntity`, and configured extra entities (`academy:coin`, `academy:silbarn` by default) are affected.

2. **Late-joining players** — Effects are sent on spawn via NEAR packets; no state sync for players joining after spawn.

3. **Shader packs** — Additive glow rendering (`SRC_ALPHA, ONE`) may conflict with custom shaders.

4. **`/aim learn` with add-on skills** — Not verified if base command accepts add-on skill IDs.

5. **AbstractHurtingProjectile rotation** — `xPower/yPower/zPower` fields need access transformer or reflection; not implemented.

6. **Client cleanup edge cases** — Logout/dimension change handlers exist but may miss rare edge cases.

---

## CP Cost Estimates (Config Defaults — Unverified Gameplay Balance)

| Skill | CP Cost | Overload | Cooldown | Notes |
|-------|---------|----------|----------|-------|
| Spatial Link | 60 | 15 | 10s (+8s duration) | Two-step placement |
| Vector Redirect | 80 | 20 | 16s (+3s duration) | |
| Spatial Exchange | 100 | 25 | 22s | |
| Distortion Field | 120 | 30 | 28s (+5s duration) | |
| Inversion Strike | 140 | 35 | 15s | Delay 6 ticks, damage 12-18 |
| Fold Step | 70 | 18 | 12s | |
| Spatial Cage | 160 | 40 | 38s (+6s duration) | |
| Absolute Inversion | 300 | 80 | 120s (+5s duration) | Ultimate |

**Note:** These are initial guesses. Actual balance requires playtesting.

---

## Files Created (Complete Project)

```
spatialinversion-addon/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── LICENSE
├── NOTICE.md
├── README.md
├── TESTING_REPORT.md
├── libs/re-academycraft-forge-1.20.1-1.5.0-api.jar
├── tools/check_no_particles.py
├── .github/workflows/build.yml
└── src/main/
    ├── java/io/spatialinversion/addon/
    │   ├── SpatialInversion.java (main mod + all 8 behaviors)
    │   ├── config/SpatialConfig.java
    │   ├── core/
    │   │   ├── Portal.java
    │   │   ├── PortalGroup.java
    │   │   ├── PortalManager.java
    │   │   └── SafeSpot.java
    │   ├── network/
    │   │   ├── NetworkHandler.java
    │   │   └── packet/
    │   │       ├── FxSpawnPacket.java
    │   │       └── FxEndPacket.java
    │   └── client/ClientEffectManager.java
    └── resources/
        ├── META-INF/mods.toml
        ├── pack.mcmeta
        └── assets/spatialinversion/
            ├── lang/en_us.json
            ├── lang/ja_jp.json
            └── models/item/spatial_factor.json
```

---

## Next Steps for User

1. **Upload to GitHub:** Create new repo, drag ALL contents (including `.github/` and `libs/`) → commit to `main`
2. **Build:** Actions → Build Spatial Inversion Add-on → Run workflow → Download artifact
3. **Install:** Place both `re-academycraft-forge-1.20.1-1.5.0.jar` (from CurseForge) and the built add-on JAR in `mods/` folder
4. **Test:** Run Forge 1.20.1 server + client, verify all skills work
5. **Report issues:** Any bugs → adjust config or code → rebuild

---

## Honest Assessment

**This add-on is complete in source form and passes all static checks.** However, it has **NOT been compiled or tested in-game** due to environment limitations (Java 11 only). The first real compilation and in-game test will happen when you run the GitHub Actions workflow or build locally with Java 17.

Expect 1-2 iterations to fix:
- Minor API method signature differences (if any)
- Client rendering details (vertex format, shader setup)
- Network packet edge cases
- Config default balancing

The architecture follows all documented patterns from the sample addon and API guide. The zero-particle guarantee is enforced by automated tooling.