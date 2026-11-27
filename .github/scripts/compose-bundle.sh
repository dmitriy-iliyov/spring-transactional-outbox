#!/bin/sh
# Packs the compose stack of a release into dist/spring-transactional-outbox-compose-<version>.tar.gz: both compose
# files with their env files and the monitoring configuration, laid out as in the repository, with docker/.env
# pinning the servlet stack to the images of that version.
# usage: compose-bundle.sh 0.1.0
set -eu

version="$1"
name="spring-transactional-outbox-compose-$version"
dir="dist/$name"

rm -rf "$dir" "$dir.tar.gz"
mkdir -p "$dir/docker"
cp LICENSE docker-compose.yml .env "$dir/"
cp docker/compose.servlet.yml "$dir/docker/"
cp -R monitoring "$dir/"
printf '# The images of release %s, pulled from GHCR\nAPP_VERSION=%s\n' "$version" "$version" > "$dir/docker/.env"
tar -czf "$dir.tar.gz" -C dist "$name"
echo "$dir.tar.gz"
