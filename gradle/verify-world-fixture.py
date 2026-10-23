#!/usr/bin/env python3
"""Minecraft-free source/inventory/archive controls; runtime module proof lives in the subscriber."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / 'src/worldGameTest/resources/world-case-inventory.json'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def require_inventory(actual, expected):
    require(len(actual) == 2 and len(set(actual)) == 2 and set(actual) == set(expected),
            'Missing/duplicate/extra/wrong-namespace original world case')


def require_engine_filters(filters):
    require(filters == ['better_cave_encounters_world_tests'] * 2,
            'Both selected engines must filter the explicit original template namespace')


def check_deep_void(pin, archive):
    profile = json.loads((ROOT / 'src/worldGameTest/world-profile-inputs.json').read_text())
    spec = profile['deep_void']
    require(spec['pin'] == 'mods/the-deep-void.pw.toml'
            and spec['coordinate'] == 'curse.maven:the-deep-void-982999:8507128', 'Wrong approved Deep Void input')
    data = tomllib.loads(pin.read_text())
    require(data['update']['curseforge'] == {'file-id': 8507128, 'project-id': 982999}, 'Root pin coordinate changed')
    require(data['download']['hash-format'] == 'sha1', 'Root pin hash format changed')
    require(hashlib.sha1(archive.read_bytes()).hexdigest() == data['download']['hash'], 'Deep Void archive does not match actual root pin')
    with zipfile.ZipFile(archive) as z:
        descriptor = z.read('META-INF/mods.toml').decode()
        metadata = tomllib.loads(descriptor)
    require(metadata['modLoader'] == 'javafml' and metadata['loaderVersion'] == '[47,)',
            'Deep Void requires the existing Forge47 JavaFML loader, not a guessed SDK')
    require(len(metadata['mods']) == 1 and metadata['mods'][0]['modId'] == spec['mod_id']
            and metadata['mods'][0]['version'] == spec['mod_version'], 'Wrong Deep Void metadata')
    dependencies = metadata['dependencies'][spec['mod_id']]
    mandatory = sorted(d['modId'] for d in dependencies if d['mandatory'])
    optional = sorted(d['modId'] for d in dependencies if not d['mandatory'])
    require(mandatory == spec['mandatory_dependencies'] == ['minecraft'], 'Mandatory dependency closure changed')
    require(optional == spec['optional_dependencies_not_added'] == ['geckolib', 'jei'], 'Optional metadata changed; do not add guessed dependencies')
    minecraft = next(d for d in dependencies if d['modId'] == 'minecraft')
    require(minecraft['versionRange'] == '[1.20.1]' and 'minecraft_version=1.20.1' in (ROOT / 'gradle.properties').read_text(),
            'Existing Minecraft SDK must satisfy sole mandatory dependency')
    return {'scope': 'READ_ONLY_ORIGINAL_ARCHIVE_METADATA_AND_CURRENT_ROOT_PIN_NOT_NATIVE_ACCEPTANCE',
            'root_pin': str(pin.resolve()), 'root_pin_sha256': hashlib.sha256(pin.read_bytes()).hexdigest(),
            'archive': str(archive.resolve()), 'archive_sha1': hashlib.sha1(archive.read_bytes()).hexdigest(),
            'archive_sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
            'metadata_sha256': hashlib.sha256(descriptor.encode()).hexdigest(),
            'mandatory_dependencies': [minecraft], 'optional_dependencies_not_added': optional,
            'current_catalogue_ids': profile['catalogue_ids'], 'required_cohorts': profile['required_cohorts']}


def require_archive(archive, sources=False):
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        production = 'com/bettercontent/bettercaveencounters/DepthDirectorMod.' + ('java' if sources else 'class')
        require(production in names, 'Production owning mod missing from archive')
        forbidden = ('DepthDirectorWorldGameTests', 'GeologicalWorldGameTests',
                     'DepthDirectorWorldTestMod', 'GeologicalDefinitionGameTests', 'CatalogueRootAccount', 'CatalogueRootAccess',
                     'CaveFixtureGlm', 'FixtureGlmLoadMixin', 'bc_cave_fixture_glm', 'glm-profiles.json',
                     'world-case-inventory.json', 'world-profile-inputs.json', 'world-initial-root-adaptations.json', 'native-case-inventory.json',
                     'geological-qualification.json', 'data/better_cave_encounters_world_tests/')
        require(not any(any(token in n for token in forbidden) for n in names),
                'Nonshipping fixture leaked into archive')
        require(names.count('META-INF/mods.toml') == 1, 'Missing/duplicate owning descriptor')
        descriptor = z.read('META-INF/mods.toml').decode()
        require('better_cave_encounters_world_tests' not in descriptor, 'Second world mod descriptor leaked')
        require(len(re.findall(r'^\s*\[\[mods\]\]', descriptor, re.M)) == 1,
                'Archive must describe exactly one owning mod')
        if not sources:
            require(re.search(r'modId\s*=\s*"better_cave_encounters"', descriptor), 'Wrong owning runtime descriptor')


def rejects(body):
    try:
        body()
    except ValueError:
        return
    raise AssertionError('Synthetic defect was accepted')


def synthetic_archive(sources=False, leak=None, duplicate_descriptor=False, wrong_mod=False):
    memory = io.BytesIO()
    with zipfile.ZipFile(memory, 'w') as z:
        z.writestr('com/bettercontent/bettercaveencounters/DepthDirectorMod.' + ('java' if sources else 'class'), '')
        descriptor = '[[mods]]\nmodId="' + ('wrong_mod' if wrong_mod else 'better_cave_encounters') + '"\n'
        if duplicate_descriptor:
            descriptor += '[[mods]]\nmodId="better_cave_encounters_world_tests"\n'
        z.writestr('META-INF/mods.toml', descriptor)
        if leak:
            z.writestr(leak, '')
    memory.seek(0)
    return memory


def require_initial_root_adaptation(text, case):
    spec = json.loads((ROOT / case['initial_root_adaptations']).read_text())
    require(spec['source'] == case['source']
            and spec['before_sha256'] == case['pre_initial_root_transaction_sha256']
            and spec['after_sha256'] == case['adapted_sha256'], 'Wrong approved initial-root source context')
    require(hashlib.sha256(text.encode()).hexdigest() == spec['after_sha256'], 'Unexpected initial-root source change')
    require(len(spec['replacements']) == 4, 'Initial-root adaptation must remain the four approved local regions')
    restored = text
    for replacement in spec['replacements']:
        require(replacement['before'] and replacement['after'] and restored.count(replacement['after']) == 1,
                'Missing/ambiguous approved initial-root source region')
        restored = restored.replace(replacement['after'], replacement['before'], 1)
    require(hashlib.sha256(restored.encode()).hexdigest() == spec['before_sha256'],
            'Unrelated original setup/cadence/query/RNG/assertion/deadline bytes changed')


def check_source():
    inv = json.loads(INVENTORY.read_text())
    require(inv['owning_mod'] == 'better_cave_encounters', 'Wrong owning mod')
    require(inv['required_case_counts'] == {'world': 1, 'geology': 1}, 'Original 1/1 profiles changed')
    expected = [(c['profile_namespace'], c['method']) for c in inv['cases']]
    require({c['profile_namespace'] for c in inv['cases']} == {
        'better_cave_encounters_world_tests', 'better_cave_encounters_geology_tests'}, 'Logical profile labels changed')
    actual = []
    for c in inv['cases']:
        path = ROOT / c['source']
        text = path.read_text()
        require(hashlib.sha256(path.read_bytes()).hexdigest() == c['adapted_sha256'],
                'Original world assertion/setup/query/RNG/deadline body changed: ' + c['source'])
        if c.get('initial_root_adaptations'):
            require_initial_root_adaptation(text, c)
            rejects(lambda: require_initial_root_adaptation(text + '\n// unexpected change', c))
        require('@GameTestHolder' not in text, 'Automatic holder registration defeats selected profile')
        require(c['engine_template_filter'] == 'better_cave_encounters_world_tests', 'Wrong engine template filter')
        methods = re.findall(r'@GameTest\(([^)]*)\)\s*public static void (\w+)\(', text)
        require(len(methods) == 1, 'World source must retain exactly one original real case')
        annotation, method = methods[0]
        require('templateNamespace = DepthDirectorWorldTestMod.MOD_ID' in annotation, 'Template namespace changed')
        require(c['template_namespace'] == 'better_cave_encounters_world_tests' and c['template'] == 'director_cave', 'Template inventory changed')
        timeout = re.search(r'timeoutTicks\s*=\s*([\w_]+)', annotation).group(1)
        if not timeout.replace('_', '').isdigit():
            timeout = re.search(r'\b' + timeout + r'\s*=\s*([\d_]+)', text).group(1)
        require(int(timeout.replace('_', '')) == c['timeout_ticks'], 'Original timeout changed')
        actual.append((c['profile_namespace'], method))
    require(sum(len(re.findall(r'@GameTest\(', p.read_text()))
                for p in (ROOT / 'src/worldGameTest/java').rglob('*.java')) == 2,
            'Extra world source case outside original inventory')
    require_inventory(actual, expected)
    filters = [c['engine_template_filter'] for c in inv['cases']]
    require_engine_filters(filters)
    rejects(lambda: require_engine_filters([filters[0], 'better_cave_encounters_geology_tests']))
    rejects(lambda: require_engine_filters(filters[:1]))
    rejects(lambda: require_inventory(actual[:-1], expected))
    rejects(lambda: require_inventory(actual + actual[:1], expected))
    rejects(lambda: require_inventory([actual[0], actual[0]], expected))
    rejects(lambda: require_inventory(actual + [('unexpected', 'alias')], expected))
    rejects(lambda: require_inventory([('wrong_namespace', actual[0][1]), actual[1]], expected))
    production = ROOT / 'src/main/java/com/bettercontent/bettercaveencounters'
    saved = (production / 'DirectorSavedData.java').read_text()
    runtime = (production / 'DirectorRuntime.java').read_text()
    require('void recoveryUntil(long value) { recoveryUntil = value; }' in saved
            and 'now < track.recoveryUntil()' in runtime, 'Pacing receiver contract changed; review API mapping')
    diagnostics = (production / 'DepthDirectorDiagnostics.java').read_text()
    require('SpawnLocator.inspectCandidate(level, players, position, mob)' in diagnostics
            and 'SpawnLocator.eventSpawnPositionAllowed(mob, level)' in diagnostics
            and 'mob.discard();' in diagnostics, 'Typed diagnostic receiver contract changed')
    require('static boolean isDirectorMob(Mob mob)' in (production / 'SpawnLocator.java').read_text()
            and '!(event.parent() instanceof Mob parent)' in (production / 'DirectorLineages.java').read_text(),
            'Offspring typed receiver contract changed')
    descriptor = ROOT / 'src/worldGameTest/resources/META-INF/mods.toml'
    require(hashlib.sha256(descriptor.read_bytes()).hexdigest() == inv['historical_descriptor_sha256'],
            'Authored historical world descriptor must be retained unchanged')
    build = (ROOT / 'build.gradle.kts').read_text()
    require(build.count('resources.exclude("META-INF/mods.toml"') == 2, 'Both effective fixture resources must exclude old descriptor')
    sentinel = 'data/better_cave_encounters_world_tests/loot_tables/policy_sentinel.json'
    require('resources.exclude("META-INF/mods.toml", "' + sentinel + '")' in build, 'Legacy sentinel must be excluded')
    require('deeper-and-darker-659011' not in build, 'Unused legacy client-mixin dependency remains')
    for profile in ('worldGameTest', 'geologyGameTest'):
        require('getByName(property("mod_id") as String) { source(' + profile + ') }' in build, 'Fixture must mount on same owning mod')
    require('create("better_cave_encounters_world_tests")' not in build, 'Second JPMS module remains')
    require(build.count('property("forge.enabledGameTestNamespaces", "better_cave_encounters_world_tests")') == 2,
            'Both world engines must filter the actual explicit template namespace')
    require('property("forge.enabledGameTestNamespaces", "better_cave_encounters_geology_tests")' not in build,
            'Logical geology profile label is not an engine template filter')
    world = (ROOT / inv['cases'][0]['source']).read_text()
    require('definitions.size() == 6' in world and '.containsAll(List.of(id("undead")' not in world,
            'Current catalogue must be strict exact six, never at-least old five')
    exact_set = re.search(r'\.equals\(Set\.of\(([^\n]+)\)\)', world)
    profile = json.loads((ROOT / 'src/worldGameTest/world-profile-inputs.json').read_text())
    ids = sorted('better_cave_encounters:' + x for x in re.findall(r'id\("([^"]+)"\)', exact_set.group(1))) if exact_set else []
    authored = sorted('better_cave_encounters:' + p.stem for p in
                      (ROOT / 'src/main/resources/data/better_cave_encounters/director_ecologies').glob('*.json'))
    require(len(ids) == 6 and ids == profile['catalogue_ids'] == authored, 'Exact six IDs must equal current authored production catalogue')
    require(profile['depths'] == [-56, 0, 32] and profile['required_cohorts'] == 18
            and 'int[] DEPTHS = {-56, 0, 32}' in world
            and 'for (EcologyDefinition ecology : ecologies)' in world and 'for (int y : DEPTHS)' in world,
            'All six x three original depths must remain covered')
    subscriber = (ROOT / 'src/worldGameTest/java/com/bettercontent/bettercaveencounters/worldtest/DepthDirectorWorldTestMod.java').read_text()
    require('public static final String MOD_ID = "better_cave_encounters_world_tests";' in subscriber,
            'Original logical template namespace changed')
    require('@Mod.EventBusSubscriber(modid = DepthDirectorMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)' in subscriber,
            'Must subscribe to owning mod bus')
    require(not re.search(r'@Mod\s*\(', subscriber) and 'FMLJavaModLoadingContext' not in subscriber and 'addListener' not in subscriber,
            'Second mod/constructor registration remains')
    require(subscriber.count('event.register(GeologicalWorldGameTests.class)') == 1
            and subscriber.count('event.register(DepthDirectorWorldGameTests.class)') == 1
            and 'if (Boolean.getBoolean("bc.geologyQualification"))' in subscriber, 'Original single selected registration changed')
    require('DepthDirectorMod.class.getModule()' in subscriber and 'descriptorMods.equals(java.util.List.of(DepthDirectorMod.MOD_ID))' in subscriber,
            'Missing actual FML module/descriptor runtime guards')
    for sources in (False, True):
        require_archive(synthetic_archive(sources), sources)
        for leak in ('com/bettercontent/bettercaveencounters/CatalogueRootAccount.' + ('java' if sources else 'class'),
                     'com/bettercontent/bettercaveencounters/CatalogueRootAccess.' + ('java' if sources else 'class'),
                     'com/bettercontent/bettercaveencounters/GeologicalWorldGameTests.' + ('java' if sources else 'class'),
                     'com/bettercontent/bettercaveencounters/worldtest/CaveFixtureGlm.' + ('java' if sources else 'class'),
                     'com/bettercontent/bettercaveencounters/worldtest/mixin/FixtureGlmLoadMixin.' + ('java' if sources else 'class'),
                     'bc_cave_fixture_glm.mixins.json', 'bc_cave_fixture_glm_scope.json', 'glm-profiles.json',
                     'world-case-inventory.json', 'data/better_cave_encounters_world_tests/loot_tables/policy_sentinel.json'):
            rejects(lambda leak=leak: require_archive(synthetic_archive(sources, leak), sources))
        rejects(lambda: require_archive(synthetic_archive(sources, duplicate_descriptor=True), sources))
    rejects(lambda: require_archive(synthetic_archive(wrong_mod=True)))
    return expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('world-resources', 'geology-resources', 'runtime-archive', 'sources-archive',
                 'root-pin', 'deep-void-archive', 'pin-proof'):
        parser.add_argument('--' + name, type=Path)
    args = parser.parse_args()
    cases = check_source()
    for directory in (args.world_resources, args.geology_resources):
        if directory is not None:
            require(directory.is_dir(), 'Processed fixture resources missing')
            require(not (directory / 'META-INF/mods.toml').exists(), 'Effective second descriptor remains')
            require((directory / 'world-case-inventory.json').is_file(), 'Fixture inventory resource missing')
            require((directory / 'bc_cave_fixture_glm_scope.json').read_bytes() ==
                    (ROOT / 'src/worldGameTest/glm-profiles.json').read_bytes(), 'Processed owning GLM scope differs')
            require((directory / 'bc_cave_fixture_glm.mixins.json').read_bytes() ==
                    (ROOT / 'src/worldGameTest/resources/bc_cave_fixture_glm.mixins.json').read_bytes(), 'Required processed observer config differs')
            sentinel = directory / 'data/better_cave_encounters_world_tests/loot_tables/policy_sentinel.json'
            require(sentinel.is_file() == (directory == args.geology_resources), 'Sentinel must exist only in actual geology resources')
    if args.root_pin is not None or args.deep_void_archive is not None:
        require(args.root_pin is not None and args.deep_void_archive is not None, 'Both actual root pin and original archive required')
        proof = check_deep_void(args.root_pin, args.deep_void_archive)
        if args.pin_proof is not None:
            args.pin_proof.parent.mkdir(parents=True, exist_ok=True)
            text = json.dumps(proof, indent=2) + '\n'
            require(not args.pin_proof.exists() or args.pin_proof.read_text() == text, 'Refusing to replace immutable pin proof')
            if not args.pin_proof.exists():
                args.pin_proof.write_text(text)
        print('Actual root Deep Void8507128 pin/archive/mandatory metadata closure PASS', proof)
    if args.runtime_archive is not None:
        require_archive(args.runtime_archive)
    if args.sources_archive is not None:
        require_archive(args.sources_archive, True)
    print('World fixture source controls PASS: original 1/1 cases, exact adapted bodies, namespaces, descriptor exclusion, archive negatives', cases)
    print('Actual FML module execution and actual final archives remain unproven unless their owning native/archive tasks ran.')


if __name__ == '__main__':
    main()
