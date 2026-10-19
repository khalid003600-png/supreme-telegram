#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
check_dir="$(mktemp -d)"
trap 'rm -rf "$check_dir"' EXIT
javac -d "$check_dir" "$project_dir/app/src/main/java/com/wolfox/gps/util/WFHostPolicy.java" "$project_dir/verification/HostPolicyCheck.java"
java -cp "$check_dir" HostPolicyCheck
