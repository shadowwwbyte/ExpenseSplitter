#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
exec "$DIR/gradle/wrapper/gradle-9.2/bin/gradle" "$@"
