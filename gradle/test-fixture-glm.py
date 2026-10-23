#!/usr/bin/env python3
"""Targeted existing-decode-defect controls on actual owning originals; no Minecraft bootstrap."""
import argparse
import copy
import importlib.util
import json
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('fixture_glm', Path(__file__).with_name('prepare-fixture-glm.py'))
GLM = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GLM)


def rejects(body):
    try:
        body()
    except ValueError:
        return
    raise AssertionError('Fixture GLM control accepted a synthetic defect')


def controls(bindings, root):
    scope = json.loads(GLM.SCOPE.read_bytes())
    root_index = json.loads((root / 'kubejs' / GLM.INDEX).read_bytes())
    assertions = 0
    for name in ('legacy', 'geology'):
        inventory = []
        for actual, expected in zip(bindings['profiles'][name], scope['profiles'][name]['originals']):
            assert actual['coordinate'] == expected['coordinate']
            raw = Path(actual['path']).read_bytes()
            assert GLM.sha(raw) == expected['sha256']
            inventory += GLM.components(raw, actual['coordinate'], actual['path'])
        for actual in bindings['supplemental']:
            inventory += GLM.components(Path(actual['path']).read_bytes(), actual['coordinate'], actual['path'])
        invoke = lambda items=inventory, policy=root_index, override=lambda resource: False: GLM.project(scope, name, policy, items, override)
        result = invoke()
        assert len(result['rows']) == {'legacy': 29, 'geology': 38}[name]
        assert len(result['explicit_exclusions']) == 4
        assert all('goety:' + key in result['explicit_exclusions'] for key in
                   ('chill_fabric_dig', 'dark_fabric_dig', 'gale_fabric_dig', 'jade_dig'))
        assert result['gameplay_effect_acceptance'] is False
        assert len(result['excluded_original_resources']) == 4 and all(item['original_definition_missing']
            and item['reason'] == 'explicit_canonical_Root_nonmembership' for item in result['excluded_original_resources'])
        assertions += 5
        # An included resource may never become an exclusion because a file/type/registrar failed.
        row = result['rows'][0]
        owner = next(item for item in inventory if row['resource'] in item['resources'])
        broken = copy.deepcopy(inventory)
        next(item for item in broken if item['origin'] == owner['origin'])['resources'].pop(row['resource'])
        rejects(lambda: invoke(broken))
        assertions += 1
        for field, value in [('type', 'unregistered:outer'), ('conditions', [{'condition': 'unregistered:nested'}])]:
            broken = copy.deepcopy(inventory)
            target = next(item for item in broken if item['origin'] == owner['origin'])
            definition = json.loads(target['resources'][row['resource']])
            definition[field] = value
            target['resources'][row['resource']] = GLM.encode(definition)
            rejects(lambda: invoke(broken))
            assertions += 1
        # Concrete namespace trap: TConstruct supplies definitions using Mantle's codec registration.
        cross_owner = [item for item in result['rows'] if item['data_owner_coordinate'].startswith('curse.maven:tinkers-construct-') and item['codec'].startswith('mantle:')]
        assert cross_owner
        broken = copy.deepcopy(inventory)
        for item in broken:
            item['classes'].pop('slimeknights.mantle.loot.MantleLoot', None)
        rejects(lambda: invoke(broken))
        assertions += 2
        broken = copy.deepcopy(inventory)
        for item in broken:
            if 'slimeknights.mantle.loot.MantleLoot' in item['classes']:
                item['classes']['slimeknights.mantle.loot.MantleLoot'] = '0' * 64
        rejects(lambda: invoke(broken))
        assertions += 1
        duplicate = copy.deepcopy(owner)
        rejects(lambda: invoke(inventory + [duplicate]))
        assertions += 1
        rejects(lambda: invoke(override=lambda resource: resource == row['resource']))
        assertions += 1
        empty = copy.deepcopy(root_index)
        empty['entries'] = []
        rejects(lambda: invoke(policy=empty))
        assertions += 1
        changed = copy.deepcopy(root_index)
        changed['entries'].remove(row['id'])
        changed['entries'].append('synthetic:unrelated')
        rejects(lambda: invoke(policy=changed))
        assertions += 1
        reversed_order = copy.deepcopy(root_index)
        reversed_order['entries'].reverse()
        rejects(lambda: invoke(policy=reversed_order))
        assertions += 1
        full = copy.deepcopy(inventory)
        full.append({'coordinate': 'synthetic:full226:0', 'origin': 'synthetic-not-an-archive', 'sha256': '0' * 64,
                     'indexes': [{'resource': GLM.INDEX, 'sha256': '0' * 64,
                                  'entries': [identifier for identifier in root_index['entries'] if identifier not in result['ordered_ids']]}],
                     'resources': {}, 'classes': {}})
        rejects(lambda: invoke(full))
        assertions += 1
    print(f'Fixture GLM targeted SOURCE controls PASS: {assertions} assertions, both actual own domains; no native decode/effect claim')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--bindings', required=True, type=Path)
    parser.add_argument('--root', required=True, type=Path)
    args = parser.parse_args()
    controls(json.loads(args.bindings.read_bytes()), args.root)
