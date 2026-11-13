#!/usr/bin/env python3
"""Writes Hamlet's copies of vanilla's village house pools, without the houses of retired jobs.

Usage: tools/strip_job_houses.py <minecraft 1.21.1 jar>
(Loom's copy: ~/.gradle/caches/fabric-loom/1.21.1/minecraft-common.jar)

Rerun it if vanilla's pools change, rather than editing the copies by hand.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

RETIRED_HOUSE = re.compile(r"_(armorer|butchers?_shop|fletcher|tannery|tool_smith|weapon_?smith)(_house)?_\d+$")
POOLS = re.compile(r"data/minecraft/worldgen/template_pool/village/[a-z]+/(zombie/)?houses\.json$")
OUT = Path(__file__).resolve().parent.parent / "hamlet-1.21.1/src/main/resources"


def location(entry):
    return entry["element"].get("location", "")


with zipfile.ZipFile(sys.argv[1]) as jar:
    for name in sorted(n for n in jar.namelist() if POOLS.match(n)):
        pool = json.loads(jar.read(name))
        kept = [e for e in pool["elements"] if not RETIRED_HOUSE.search(location(e))]
        removed = len(pool["elements"]) - len(kept)
        pool["elements"] = kept
        target = OUT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(pool, indent=2) + "\n")
        print(f"{name}: removed {removed}")
