#!/usr/bin/env python3
"""Exact nonshipping Cave GLM projection; original archives and Root are read-only inputs."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCOPE = ROOT / 'src/worldGameTest/glm-profiles.json'
INDEX = 'data/forge/loot_modifiers/global_loot_modifiers.json'
ID = re.compile(r'[a-z0-9_.-]+:[a-z0-9_./-]+')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def read(path, inputs):
    path = Path(path).resolve()
    data = path.read_bytes()
    require(str(path) not in inputs or inputs[str(path)] == sha(data), 'Concurrent input change')
    inputs[str(path)] = sha(data)
    return data


def typed_ids(value):
    result = set()
    if isinstance(value, dict):
        for key, item in value.items():
            if key in ('condition', 'function', 'type') and isinstance(item, str) and ':' in item:
                result.add(key + '|' + item)
            result |= typed_ids(item)
    elif isinstance(value, list):
        for item in value:
            result |= typed_ids(item)
    return result


def components(data, coordinate, origin, depth=0):
    require(depth <= 4, 'Nested SDK depth exceeded')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate SDK archive entry')
        indexes, resources, classes = [], {}, {}
        for name in names:
            if re.fullmatch(r'data/[^/]+/loot_modifiers/global_loot_modifiers.json', name):
                raw = archive.read(name)
                index = json.loads(raw)
                require(isinstance(index.get('entries', []), list), 'Invalid original SDK index')
                require(all(isinstance(i, str) and ID.fullmatch(i) for i in index.get('entries', [])), 'Invalid declared ID')
                indexes.append({'resource': name, 'sha256': sha(raw), 'entries': index.get('entries', [])})
            elif re.fullmatch(r'data/[^/]+/loot_modifiers/.+\.json', name):
                resources[name] = archive.read(name)
            elif name.endswith('.class'):
                classes[name[:-6].replace('/', '.')] = sha(archive.read(name))
        result = [{'coordinate': coordinate, 'origin': origin, 'sha256': sha(data),
                   'indexes': indexes, 'resources': resources, 'classes': classes}]
        for name in names:
            if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                result += components(archive.read(name), coordinate, origin + '!/' + name, depth + 1)
    return result


def project(scope, profile, root_index, inventory, root_override):
    """Selection is declaration INTERSECT explicit Root membership, before resource/type checks."""
    spec = scope['profiles'][profile]
    root_ids = root_index['entries']
    require(root_index['replace'] is True and len(root_ids) == 226 and len(set(root_ids)) == 226,
            'Canonical Root226 policy missing/duplicate/changed schema')
    declarations = {}
    for component in inventory:
        for index in component['indexes']:
            for identifier in index['entries']:
                declarations.setdefault(identifier, []).append((component, index))
    selected = [identifier for identifier in root_ids if identifier in declarations]
    excluded = sorted(set(declarations) - set(root_ids))
    require(selected == spec['ordered_ids'], 'Exact Root-ordered own-domain membership drift')
    require(excluded == spec['explicit_exclusions'], 'Explicit Root exclusion drift')
    require(len(selected) == {'legacy': 29, 'geology': 38}[profile], 'Empty/full/out-of-domain projection')
    require(len(declarations) == len(selected) + 4, 'Original SDK declaration domain drift')
    rows, used, witnesses = [], set(), set()
    for identifier in selected:
        require(len(declarations[identifier]) == 1, 'Duplicate in-scope SDK declaration')
        namespace, path = identifier.split(':', 1)
        resource = f'data/{namespace}/loot_modifiers/{path}.json'
        candidates = [(component, component['resources'][resource]) for component in inventory if resource in component['resources']]
        require(len(candidates) == 1, 'Included resource missing/duplicate; never prune')
        require(not root_override(resource), 'Root-authored override requires explicit own closure review')
        component, raw = candidates[0]
        definition = json.loads(raw)
        codec = definition.get('type')
        require(codec in scope['outer_codecs'], 'Unclosed included outer codec; never prune')
        refs = typed_ids(definition)
        require(refs <= scope['typed_registration_owners'].keys(), 'Unclosed included nested type; never prune')
        used |= refs
        for role in refs:
            witnesses.add(scope['typed_registration_owners'][role]['registration_witness_class'])
        witnesses.update(scope['outer_codecs'][codec].values())
        rows.append({'id': identifier, 'codec': codec, 'resource': resource, 'sha256': sha(raw),
                     'original_origin': component['origin'], 'data_owner_coordinate': component['coordinate']})
    require(sorted(used) == spec['typed_ids'], 'Exact in-scope typed dependency closure drift')
    # All reviewed helper/bus/registration classes present in this profile are also bound. These
    # hashes pin inspected registration instructions, not namespace/modId/class-existence guesses.
    coordinates = {component['coordinate'] for component in inventory}
    witnesses |= {name for name, item in scope['original_class_witnesses'].items() if item['coordinate'] in coordinates}
    for name in witnesses:
        expected = scope['original_class_witnesses'][name]
        candidates = [component['classes'][name] for component in inventory
                      if component['coordinate'] == expected['coordinate'] and name in component['classes']]
        require(candidates == [expected['sha256']], 'Actual registration/codec/bus bytecode missing/drifted: ' + name)
    omitted = []
    for identifier in excluded:
        namespace, key = identifier.split(':', 1)
        resource = f'data/{namespace}/loot_modifiers/{key}.json'
        omitted.append({'id': identifier, 'reason': 'explicit_canonical_Root_nonmembership',
                        'original_definition_missing': not any(resource in item['resources'] for item in inventory)})
    return {'ordered_ids': selected, 'explicit_exclusions': excluded, 'excluded_original_resources': omitted, 'rows': rows,
            'typed_ids': sorted(used), 'registration_class_witnesses': sorted(witnesses),
            'original_index_sha256s': sorted({index['sha256'] for component in inventory for index in component['indexes']}),
            'root_entries_outside_domain_informational_only': len(set(root_ids) - declarations.keys()),
            'gameplay_effect_acceptance': False}


def prepare(bindings_path, pack_root, profile, output):
    inputs = {}
    bindings = json.loads(read(bindings_path, inputs))
    for relative in ('build.gradle.kts', 'gradle/fixture-glm.gradle', 'gradle/prepare-fixture-glm.py',
                     'src/worldGameTest/world-profile-inputs.json',
                     'src/worldGameTest/resources/geological-qualification.json',
                     'gradle/test-fixture-glm.py', 'gradle/verify-world-fixture.py',
                     'src/worldGameTest/resources/bc_cave_fixture_glm.mixins.json',
                     'src/worldGameTest/java/com/bettercontent/bettercaveencounters/worldtest/CaveFixtureGlm.java',
                     'src/worldGameTest/java/com/bettercontent/bettercaveencounters/worldtest/mixin/FixtureGlmLoadMixin.java'):
        read(ROOT / relative, inputs)
    scope_bytes = read(SCOPE, inputs)
    scope = json.loads(scope_bytes)
    require(scope['schema'] == 'bc.cave.fixture_glm_scope.v1', 'Owned scope schema')
    expected = scope['profiles'][profile]['originals']
    actual = bindings['profiles'][profile]
    require([item['coordinate'] for item in actual] == [item['coordinate'] for item in expected], 'Actual original SDK graph differs')
    inventory = []
    for actual_item, spec in zip(actual, expected):
        raw = read(actual_item['path'], inputs)
        require(sha(raw) == spec['sha256'], 'Original SDK archive differs: ' + spec['coordinate'])
        if spec['root_pin']:
            pin = tomllib.loads(read(pack_root / spec['root_pin'], inputs).decode())
            require(pin['download']['hash-format'] == 'sha1' and hashlib.sha1(raw).hexdigest() == pin['download']['hash'], 'Root pin does not bind original SDK')
        inventory += components(raw, spec['coordinate'], str(Path(actual_item['path']).resolve()))
    for item in bindings['supplemental']:
        inventory += components(read(item['path'], inputs), item['coordinate'], str(Path(item['path']).resolve()))
    root_path = pack_root / 'kubejs' / INDEX
    root_bytes = read(root_path, inputs)
    projection = project(scope, profile, json.loads(root_bytes), inventory,
                         lambda resource: (pack_root / 'kubejs' / resource).is_file())
    # Preserve absence witnesses too: a new Root-authored included definition cannot sneak in
    # between preparation and engine admission simply because no file existed at preparation.
    absent = [str((pack_root / 'kubejs' / row['resource']).resolve()) for row in projection['rows']]
    require(all(not Path(path).exists() for path in absent), 'Unexpected authored override')
    index_bytes = encode({'replace': True, 'entries': projection['ordered_ids']})
    pack_id = 'bc_cave_fixture_glm_' + profile
    result = {'schema': 'bc.cave.fixture_glm_manifest.v1', 'profile': profile,
              'attempt': output.parent.name, 'pack_id': pack_id, 'scope_sha256': sha(scope_bytes),
              'canonical_root_index_sha256': sha(root_bytes), 'index_sha256': sha(index_bytes),
              'projection': projection, 'inputs': inputs, 'absent_root_overrides': absent}
    require(not output.exists(), 'Refusing existing fixture output; retain and choose fresh invocation')
    verify_inputs(result)
    output.mkdir(parents=True)
    (output / 'pack' / INDEX).parent.mkdir(parents=True)
    (output / 'pack' / INDEX).write_bytes(index_bytes)
    (output / 'pack/pack.mcmeta').write_bytes(encode({'pack': {'pack_format': 15, 'description': pack_id}}))
    (output / 'root-parent-global-loot-modifiers.json').write_bytes(root_bytes)
    (output / 'manifest.json').write_bytes(encode(result))
    verify(output / 'manifest.json')
    return result


def verify_inputs(data):
    for path, expected in data['inputs'].items():
        require(Path(path).is_file() and sha(Path(path).read_bytes()) == expected, 'Original/source input changed: ' + path)
    require(all(not Path(path).exists() for path in data['absent_root_overrides']), 'Root override appeared')


def verify(path):
    data = json.loads(path.read_bytes())
    verify_inputs(data)
    scope = json.loads(SCOPE.read_bytes())
    require(data['schema'] == 'bc.cave.fixture_glm_manifest.v1' and sha(SCOPE.read_bytes()) == data['scope_sha256'], 'Manifest/scope schema or hash drift')
    projection = data['projection']
    spec = scope['profiles'][data['profile']]
    require(projection['ordered_ids'] == spec['ordered_ids'], 'Manifest own domain drift')
    require(projection['explicit_exclusions'] == spec['explicit_exclusions'] and projection['typed_ids'] == spec['typed_ids'], 'Manifest authority/type closure drift')
    require([row['id'] for row in projection['rows']] == spec['ordered_ids'], 'Manifest resource coverage/order drift')
    for row in projection['rows']:
        namespace, key = row['id'].split(':', 1)
        require(row['resource'] == f'data/{namespace}/loot_modifiers/{key}.json' and
                row['codec'] in scope['outer_codecs'] and re.fullmatch('[a-f0-9]{64}', row['sha256']), 'Manifest included resource/codec drift')
    require(projection['gameplay_effect_acceptance'] is False, 'Loading is not gameplay-effect acceptance')
    require(sha((path.parent / 'root-parent-global-loot-modifiers.json').read_bytes()) == data['canonical_root_index_sha256'], 'Retained canonical parent differs')
    raw = (path.parent / 'pack' / INDEX).read_bytes()
    require(sha(raw) == data['index_sha256'] and json.loads(raw) == {'replace': True, 'entries': data['projection']['ordered_ids']}, 'Prepared fixed index drift')
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bindings', type=Path)
    parser.add_argument('--root', type=Path)
    parser.add_argument('--profile', choices=('legacy', 'geology'))
    parser.add_argument('--output', type=Path)
    parser.add_argument('--verify', type=Path)
    args = parser.parse_args()
    data = verify(args.verify) if args.verify else prepare(args.bindings, args.root, args.profile, args.output)
    print(f"Cave fixture GLM {data['profile']}: {len(data['projection']['ordered_ids'])} explicit Root members; SOURCE preparation/verification only")


if __name__ == '__main__':
    main()
