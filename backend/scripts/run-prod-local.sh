#!/usr/bin/env bash

set -euo pipefail

ENV_FILE="${HOME}/.config/myfitnesslog/prod.env"

[[ -f "$ENV_FILE" ]] || {
    echo "Missing $ENV_FILE" >&2
    exit 1
}

set -a
source "$ENV_FILE"
set +a

exec mvn spring-boot:run