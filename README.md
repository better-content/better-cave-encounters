# Better Cave Encounters

## Playtest logging

`bc.playtest.better_cave_encounters.director` observes encounter admission/retirement and
warning/surge/recovery/suspend/resume edges, not repeated pursuit probes or individual ticks.
Existing encounter/family UUIDs correlate phase, suspension, packet cancellation and retirement with
admission and locality topology. Existing native tick is included where supplied; retirement does
not invent one. Cached encounter callbacks do not resolve a world solely to fill dimension/position.
It never invokes the diagnostic Mob-construction probe, adds RNG draws or logs player identities.
DEBUG/TRACE share 120/minute (burst 20), 256 aggregation keys and 2,048-character records with
30-second coalesced summaries. Startup INFO reports effective common-config enablement,
loaded version/mode/limits; unavailable source identity is `UNKNOWN`. Stop/disconnect clears
transient diagnostics without resetting gameplay or process allowances. Pure emitter/contracts
supplement the existing native encounter and geological qualification gates.

## Finite native initial-root replacement

The optional exact-version Damned initial-root transaction and finite nonshipping paid catalogue
seam are documented in [initial-root replacement](docs/initial-root-replacement.md). This grants no
public budget authority or unbounded offspring policy. Source controls do not establish native
acceptance. Native navigation is cache-bearing, not readonly. After true native insertion/return,
the paid actual root receives existing Cave grounding and the unchanged final native path/Forge
predicates. Provisional geometry adds no duplicate path or Cave-position callback. No native movement,
yaw/RNG rewrite, scratch navigator, weaker predicate, extra retry, pacing or schema change is implied.

## Native parser fixture boundary

`verifyFull` now requires **12** owning-namespace cases: the original ten unchanged plus two
registry-dependent parser cases in nonshipping `src/nativeGameTest`. The exact source profile is
`src/nativeGameTest/resources/native-case-inventory.json`. `verifyFast` checks missing/extra/aliased
cases, namespace negatives and fixture archive-leak negatives; the final reobfuscated JAR is checked
for fixture absence. Actual latest/debug logs must be clean and all twelve cases must complete.
The existing world/geological profiles and deadlines are unchanged. Their nonshipping outputs
now attach to the same owning mod/module, with the historical world-only descriptor excluded
from effective fixture resources, not deleted or overlaid. The logical profile labels and
one-case-per-world-profile inventory remain distinct. World cases use event-only selected
registration (no automatic holder annotation); both engines filter their unchanged explicit world
template namespace, not a logical profile label. `verifyFast` checks actual runtime/sources
archive exclusion and processed resources; actual world startup checks its named FML module and
owning descriptor. See [world fixture isolation and API mappings](docs/world-fixture-isolation.md).
JVM tests retain both
pre-registry schema rejections and all four finite-slime/corpse assertions without any bootstrap.
See [the exact assertion mapping](docs/native-parser-fixture.md). Source preparation is not proof
of a passed native gate; failed plain-JVM bootstrap attempts remain retained separately.

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


Operators with permission level 2 may use `/depthdirector inspect <online player>`
to read that player's encounter identity and family, phase, pursuit target and
locality, warned approach, spent/remaining budget, active mob count, and the last
observed suspension or spawn failure reason. `/depthdirector inspect` inspects the
operator's own player. Inspection creates no player track and never changes an
encounter. There is no operator force-spawn command.

A small server-authoritative underground encounter director for Better Content. It
groups nearby players, builds pressure from time and depth below the local terrain surface,
and sends bounded packets from unseen, dark, reachable cave routes. The surface boundary
uses the leaf-ignoring motion-blocking heightmap and reserves the configured shallow band
before Director control begins. The legacy noise ecology blend remains the timing and
unmapped-fallback authority. In mapped Overworld underground neighborhoods, a separate
reloadable geological registry selects inhabitants at each actual warned spawn position.
Other dimensions and fixed-breach eligibility retain their existing paths.

The runtime has no Rail Crawler, Epic Fight, Tinkers' Construct, or mob-mod API
dependency. The pack includes Death’s Door in Downed Player Revival; the Director reads its injury authority at runtime
and scales encounter pacing from active injuries without coupling its core policy to that mod.

Every surge packet now has a 40-tick diegetic telegraph before spawning: the selected ecology
sounds from the approach direction while low rumbles and harmless falling-stone debris cue nearby
participants. The telegraph queues no mobs until it completes and is cancelled if the encounter
enters recovery or retirement, or loses its eligible players.

The five built-in noise profiles (undead, carrion, spirits, sculk and end), plus the pack's
Deep Void overlay, remain available for fallback and save compatibility. Director rosters
exclude base `minecraft:zombie` and `minecraft:skeleton`. Optional entity IDs skip safely
when absent. The pack now deliberately exports twenty specified non-boss species from
native structure/dimension habitats; rewards and terrain behavior remain native under
existing pack policy. Unsupported bosses, ritual actors and extra summoners are not added.

Each roster entry may override its role's budget `cost` and set
`maximum_per_packet` or `maximum_per_encounter`. Missing or zero values retain the old
role cost and unlimited-cap behavior. Encounter caps scale with participant count;
packet caps remain absolute so every packet preserves its intended composition.

## Geological authoring and ordinary populations

Pack definitions live at `kubejs/data/better_cave_encounters/geological_ecologies/`.
Schema 1 declares `raw_rock_tag`, `ordinary_admission`, separate `ordinary`/`director`
entries, and optional `condition`, `spawn_gamerule` and `offspring` policies. Entries use
the existing role/depth/cost/packet/encounter semantics. Shared species must have identical
depth, role, conditions, costs, limits and offspring policy; ordinary weights may differ.
The pack's ten habitats and exact populations are documented in
[`docs/dimension_ecology.md`](../../better-content-modpack/docs/dimension_ecology.md).

The sampler reads the 125 positions at axis offsets `{-4,-2,0,2,4}` around the exact
candidate, using loaded chunks only. Eight mapped raw blocks are required. Every habitat
contributes proportionally, including third and later habitats; player-placed raw rock
counts. Crafted, cobbled, grassy and overgrown derivatives do not. Unassigned rock falls
back; recognized rock with no eligible inhabitants fails closed. A dimension/position/tick
cache expires each tick and on publication/stop. Reload publication waits for bound tags,
rejects overlap, malformed definitions and inconsistent policies, and retains the last
valid immutable snapshot on failure. Cold uses the local Minecraft temperature predicate;
water must be within Euclidean distance four, never inferred from rock color.

Forge potential-spawn/placement/position hooks supply native ordinary monster attempts,
not a periodic spawner. Compat scopes the actual vanilla selection/placement/position calls;
other categories, external spawners using `NATURAL` (including In Control's separate lava
Magma Cube spawner), and native finalization/offspring are outside that substitution scope.
A narrow membership adapter handles freshly reweighted SpawnerData without bypassing an
empty/cancelled potential list or a type removed by another listener. PositionCheck is never
forced to ALLOW; native instance rules and independent vetoes remain authoritative. Habitats normalize their own eligible weights before mixture;
stable largest-remainder allocation totals 10,000 integer weight units. Groups are one.
Silver Haunt admission is 25%, Mineral Rifts 50%, otherwise 100%, interpolated in mixtures.
One density draw is retained through position/finalization checks. Imported habitats waive
only native habitat selection, not independent DENY results, darkness/difficulty, floor,
fluid, obstruction/collision, border, distance or native caps. Ordinary actors and their
children are never marked by proximity. Compat's default-deny legacy query exempts only
an eligible ordinary member; its surface and zombie/skeleton vetoes remain independent.

Director selection is candidate-local, including continuations, with at most eight
species attempts at the already warned position. Native finalization is followed by
another route, collision/border and Forge-veto check. No untelegraphed relocation is used.
Species counters survive rock changes; prepaid family records prevent simultaneous live
heavies across splits, merges, reload and unload. Existing `BlendPrimary`/`BlendSecondary`
and phase/budget saves remain compatible. Inspection appends geology, samples, fallback,
last selected habitat and lineage usage; the old blend is explicitly timing/fallback.

## Finite causal lineages

Compat's causal scopes cover entity/passenger ticks, damage/death, conversions, vanilla
slime removal, TConstruct's independent split loop and Ice and Fire's Dread kill callback.
The immediate actor, not proximity or spawn reason, determines ownership. Nested unowned
victims mask owned attackers. Provenance, cap and base-plus-envelope budget reservations
precede native finalization; root failure removes admitted children and refunds its charge.
A native remover that itself throws is an explicit qualification failure and retains paid
credit rather than refunding potentially live pressure.

Version-1 `better_cave_encounters_lineages` SavedData freezes allowed child types, prepaid
credits, used counts, encounter family, members, corpse/liveness state and heavy identity.
Descendants consume the same nonrenewing account across generations and all admission
caps; no unused credit funds later waves. Unload/dimension transfer is not death, and
retirement does not refill an account. Legacy marked mobs reserve available remaining
encounter budget without killing the parent; diagnostics report capacity-limited records.

Frozen optional-native defaults under
`src/main/resources/data/better_cave_encounters/director_lineage_policies/native.json`
also protect old/unmapped profiles when geological data is absent. Pack declarations must
match these pinned policies: corpse Maggots are 3/2/3/2/4/2 for Barrel Zombie, Door Knight,
Zombie Clown, Lumberjack, Bruiser and Fallen Chaos Knight. Slimes use
`B(n)=4*(1+B(floor(n/2)))`, with `B(1)=0`: permitted size four reserves twenty descendants.
Standalone Dread actors do not reproduce unconditionally; their commander-dependent
conversion path has an authored four-admission allowance across native allowed types,
**not a claimed native lifetime maximum**. Native chance, gamerules, attacks, equipment,
loot and terrain logic are unchanged; hard caps can reject an excess child.

## Verification boundaries

The shared testing policy controls source/runtime authorization and evidence scope.
`verifyFast` remains Minecraft-free;
`verifyFull` includes the existing headless native tests. Prepare the newly built Compat
provider before compiling this Director API consumer; no old provider JAR is silently
replaced. `BC_CUSTOM_MOD_JAR_DIR` retains its existing explicit-provider contract.

`./gradlew verifyWorld`, when ordered, runs the full-cadence catalogue fixture and
the geological fixture. The catalogue fixture now requires the strict exact six current authored
profiles (old five plus Deep Void), all eighteen concurrent player cohorts at Y -56/0/32 and a
surface sentinel. No old cohort, pacing/recovery/witness/cleanup logic, TPS condition or original
80,000-tick deadline is relaxed. Its Deep Void8507128 original archive is checked against the actual
root `mods/the-deep-void.pw.toml` and its mandatory Minecraft1.20.1 metadata closure. The
read-only source/profile contract is `src/worldGameTest/world-profile-inputs.json`. The
geological fixture has a separate source set and twenty-five current-pack archive pins
in `src/worldGameTest/resources/geological-qualification.json`, including native mob
libraries, Unearthed/Hyle, KubeJS/LootJS and In Control. The unused legacy-only Deeper and Darker dependency is removed rather than exempting its invalid
dedicated-server renderer mixin. The unchanged Cataclysm loot-policy sentinel is present only in
the geology profile, which actually binds Cataclysm. Original archives are checked against committed Packwiz
hashes; `BC_PACK_ROOT` may supply the pack root explicitly. Declared pack configuration,
rosters/tags and the actual global loot script/configuration are copied reproducibly.

The pack's active-mod metadata now requests `verifyWorld` before future candidate staging
and binds all declared pack/pin inputs; writing that declaration does not run it. Geological
qualification hashes the built reobfuscated Director artifact without invoking staging.
Encounter species history is apportioned with conserved budgets at locality splits and summed
on rejoin, rather than resetting or multiplying counts. Packet history and family heavy identity
also survive topology changes.

The fixture asserts all twenty targets plus existing reproductive actors, native AI and
selected ranged/grapple/dig/terrain behavior, causal descendants and caps, save/unload/
reload, ordinary spawning through the real server loop and SpawnState cap, independent
potential-list cancellation, non-MONSTER/external-NATURAL negative controls, and native
reward paths under a live loot-policy sentinel. Native chance is not replaced; corpse
qualification uses at most thirty-two independent trials. Missing/failed targets fail
rather than being omitted. The full modpack's remaining loot-modifier combinations,
protection integrations, event-domain interactions and restart acceptance still require
separately ordered same-candidate Dist/Debug observations.

All native runs now use fresh retained `run-gametest/<id>`, `run-world-gametest/<id>` and
`run-geology-gametest/<id>` directories. `-Pbc.nativeFixtureId=NAME` names a **new** run;
reusing a nonempty directory fails without deleting anything. Old fixed run directories
remain untouched. Geological runs write `qualification-inputs.json` (artifact/input hashes)
and `geological-observations.json` (observations/failure). Deliberate invalid-reload tests
log the expected rejection. Native source fixtures are not full-pack or production-JAR
same-artifact acceptance, even when a staged artifact hash is recorded.

### Injury pacing
The average active maim count across participating players scales pressure buildup by `1 / (1 + averageMaims / 3)` and reinforcement intervals by its inverse. Healing HP does not remove this scaling. Semantic zero HP retains the ordinary low-health distress rules. Injuries do not cancel encounters, clear budgets, remove queued work, or exclude players from targeting.
