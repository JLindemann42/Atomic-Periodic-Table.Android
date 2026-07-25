#!/usr/bin/env python3
"""Cross-check FieldRegistry.kt against the shipped element JSON.

Catches the class of bug where the agent reads a JSON key that does not exist. Three keys the
legacy agent read - element_period, element_group_number and element_type - were present on
0 of 118 elements, so those branches silently answered with empty strings.

Reports in both directions:
  * registry keys that no element defines  -> the agent would always see Missing
  * JSON keys no registry field covers     -> data the agent cannot reach

Exits non-zero when a registry key is unbacked, or an element_group value cannot be canonicalised.

Usage: python scripts/check_field_registry.py
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REGISTRY = os.path.join(ROOT, "app/src/main/java/com/jlindemann/science/ai/data/FieldRegistry.kt")
ELEMENTS = os.path.join(ROOT, "app/src/main/assets/elements_en.json")

# Keys that are intentionally unreachable: internal ids, media URLs and one-off legacy leftovers.
IGNORED_JSON_KEYS = {
    "element_code", "link", "element_model", "spectral_img", "note_text",
    "resistivity_mult", "element_crystal_structure", "element_electrical_conductivity",
    "element_magnetic_type", "element_volume_magnetic_susceptibility",
}

# Mirrors SeriesCanon.series in Kotlin. Keep the two in step.
def canonical_series(raw):
    key = re.sub(r"[^a-z0-9]", "", raw.lower())
    if not key:
        return None
    if "alkalineearth" in key or "alkaliearth" in key: return "ALKALINE_EARTH_METAL"
    if "alkali" in key: return "ALKALI_METAL"
    if "posttransition" in key: return "POST_TRANSITION_METAL"
    if "transition" in key: return "TRANSITION_METAL"
    if "metalloid" in key or "semimetal" in key: return "METALLOID"
    if "noblegas" in key or "inertgas" in key: return "NOBLE_GAS"
    if "halogen" in key: return "HALOGEN"
    if key.startswith("lanthan"): return "LANTHANOID"
    if key.startswith("actin"): return "ACTINIDE"
    if "reactivenonmetal" in key: return "REACTIVE_NONMETAL"
    if "othernonmetal" in key or "nonmetal" in key: return "OTHER_NONMETAL"
    return None


def registry_json_keys(source):
    """Every JSON key named in FieldRegistry.kt.

    Reads only the JSON-key argument positions, not every quoted string, so a field id that
    happens to share a name with an unrelated JSON key cannot mask a genuine gap.
    """
    keys = set()
    # spec("id", "json_key", ...) and abundance("id", "json_key", ...)
    keys.update(re.findall(r'\b(?:spec|abundance)\(\s*"[a-z_0-9]+"\s*,\s*"([a-z_0-9]+)"', source))
    # multiUnit("id", listOf("k1", "k2", "k3"), ...) and the FieldSpec(...) literal form
    for block in re.findall(r'listOf\(((?:\s*"[a-z_0-9]+"\s*,?)+)\)', source):
        keys.update(re.findall(r'"([a-z_0-9]+)"', block))
    # The ionization bank is generated as (1..30).map { "element_ionization_energy$it" }.
    if "element_ionization_energy" in source:
        keys.update("element_ionization_energy%d" % i for i in range(1, 31))
    return keys


def main():
    with open(ELEMENTS, encoding="utf-8") as fh:
        elements = json.load(fh)
    with open(REGISTRY, encoding="utf-8") as fh:
        registry_source = fh.read()

    present = set()
    for row in elements.values():
        present.update(row.keys())

    isotope_keys = {k for k in present
                    if re.match(r"^(iso_|decay_type_)", k)}
    present_non_isotope = present - isotope_keys

    declared = registry_json_keys(registry_source)
    referenced = declared & (present | {"health", "flammability", "instability", "special"})

    unbacked = sorted(k for k in declared
                      if k.startswith(("element_", "iso_")) and k not in present)
    uncovered = sorted(present_non_isotope - declared - IGNORED_JSON_KEYS)

    print("elements: %d   distinct non-isotope keys: %d   isotope keys: %d"
          % (len(elements), len(present_non_isotope), len(isotope_keys)))
    print("registry-declared keys backed by data: %d" % len(referenced))

    failures = 0

    if unbacked:
        failures += len(unbacked)
        print("\nFAIL - registry declares keys no element defines:")
        for k in unbacked:
            print("   %s" % k)
    else:
        print("\nOK - every registry key is backed by real data")

    if uncovered:
        print("\nNOTE - JSON keys not covered by any registry field (%d):" % len(uncovered))
        for k in uncovered:
            n = sum(1 for r in elements.values() if k in r)
            print("   %-38s %3d/%d elements" % (k, n, len(elements)))
    else:
        print("\nOK - every data key is reachable")

    bad_groups = sorted({g for g in (r.get("element_group", "") for r in elements.values())
                         if canonical_series(g) is None})
    if bad_groups:
        failures += len(bad_groups)
        print("\nFAIL - element_group values that do not canonicalise:")
        for g in bad_groups:
            print("   %r" % g)
    else:
        groups = {canonical_series(r.get("element_group", "")) for r in elements.values()}
        print("\nOK - all %d element_group spellings canonicalise into %d series"
              % (len({r.get("element_group") for r in elements.values()}), len(groups)))

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
