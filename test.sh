#!/usr/bin/env bash
# Runs project tests and displays results.

set -u

# Ensure commands run from the project root directory
cd "$(dirname "$0")" || exit 1

# Check prerequisite: Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "Error: Maven is not installed or not in PATH."
    echo "Please install Maven (e.g., 'brew install maven')."
    exit 1
fi

echo "Running tests with 'mvn clean test'..."
mvn clean test
status=$?

if [ $status -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "  ✔ Tests completed successfully!"
    echo "======================================"
else
    echo ""
    echo "======================================"
    echo "  ✘ Tests failed (exit code: $status)"
    echo "======================================"
fi

exit $status

