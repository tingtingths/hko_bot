#!/usr/bin/env sh

HKO_BOT_BUILD_TAG=$1
docker build -t registry.itdog.me/hko_bot:${HKO_BOT_BUILD_TAG} . \
    && docker tag registry.itdog.me/hko_bot:${HKO_BOT_BUILD_TAG} registry.itdog.me/hko_bot:latest \
    && docker push registry.itdog.me/hko_bot:${HKO_BOT_BUILD_TAG} \
    && docker push registry.itdog.me/hko_bot:latest
