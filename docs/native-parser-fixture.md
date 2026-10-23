# Geological parser assertion environment

The plain-JVM fixture cannot faithfully initialize Forge's transformed networking boundary.
The direct bootstrap failed for `NetworkEvent.<init>()`; the supported public instance listener-list
path then failed for `NetworkEvent$GatherLoginPayloadsEvent.<init>()`. Both attempts and exact
failed XML are retained by the owning task. No failure is swallowed, no further SDK event classes
are prewarmed, and no partial registry initialization is accepted. The replacement contains no
bootstrap, reflection workaround, conditional skip or new driver/dependency.

## Exact original assertion mapping

| Original JVM assertion | Current environment / unchanged condition |
| --- | --- |
| `ordinaryAdmission()` equals `.25` after parsing the authored valid object | Native `geologicalWeightsMayDifferButDepthAndCostMayNot`: exact `Double.compare` equality |
| Ordinary/director `sameLimits` true with weights 1 and 2 | Same native case; unchanged fixture depth `.35`, cost `4`, roles and weights |
| Changing director `minimum_depth` to `.1` makes `sameLimits` false | Same native case; unchanged negative predicate |
| Schema version `2` throws `IllegalArgumentException` | JVM `badSchemaAndUnknownTopLevelFieldsAreRejectedBeforeRegistryAccess` |
| Ordinary probability `1.1` throws `IllegalArgumentException` | Native `geologicalProbabilityHeaviesAndConditionsAreRejected` |
| Ordinary role `heavy` throws `IllegalArgumentException` | Same native rejection case |
| Condition `rock_color_is_cold` throws `IllegalArgumentException` | Same native rejection case |
| Unknown top-level `historical_geology` throws `IllegalArgumentException` | Same pure JVM schema case |
| Offspring `(20,4,{slime:20})` has credits `20` | Original JVM finite-slime/corpse method unchanged |
| Offspring `(4,4,{slime:4})`, `(0,0,{slime:1})`, `(257,0,{slime:257})` each throw `IllegalArgumentException` | Original three JVM negatives unchanged |

The schema/unknown-top-level checks run before `Registries.BLOCK` is accessed. Successful parsing,
probability and roster/condition validation cross that registry boundary and run in a genuinely
bootstrapped native Forge server. The small native expected-exception assertion adapter accepts
only the original `IllegalArgumentException` subtype family, just like JUnit `assertThrows`;
all unexpected exceptions/errors propagate. It is not a bootstrap catch/allowlist.

## Source profile and packaging

The two new methods compile in the separate `nativeGameTest` source set and are mounted as sources
of the same owning `better_cave_encounters` mod in the source GameTest RunConfig. This keeps
package-private production access without a split-package helper mod or new production API.
They register through the standard mod-bus `RegisterGameTestsEvent`; both require the owning
namespace and the existing `director_cave` template. Each new immediate parser case has the normal
100-tick fixture deadline. None of the existing ten native or world-profile assertions/deadlines
is altered. The normal profile is now exactly ten original plus two new named cases, not aliases.

`verifyNativeParserProfile` checks the exact twelve-name inventory and synthetic missing, extra,
wrong-namespace and archive-leak/missing-production negatives. `verifyNativeParserArchive`
checks the actual final reobfuscated JAR, which must include the production parser and must not
include the new native fixture class or inventory resource. `runGameTestServer` requires an actual
all-twelve completion and clean latest/debug diagnostics. These gates must execute successfully
before acceptance; source/static preparation alone claims none.
