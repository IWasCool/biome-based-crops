#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Override the defaults with positional Minecraft versions, e.g. bash build-all.sh 1.21.1 1.21.8
if [ "$#" -eq 0 ]; then
    set -- 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11
fi
for minecraft_target in "$@"; do
    if [ "$minecraft_target" = 1.21.0 ]; then minecraft_target=1.21; fi
    bash ./gradlew build "-PmcVersion=$minecraft_target" --project-cache-dir ".gradle/versions/$minecraft_target" -PincludeLocalMods=false -PfastBuild=true -PcompatSmoke=true --max-workers=1 --no-daemon
    mkdir -p build/releases
    cp "build/versions/$minecraft_target/libs/"*.jar build/releases/
done
