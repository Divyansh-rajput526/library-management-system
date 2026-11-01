#!/usr/bin/env bash
# Launches the Library Management System console application.

set -u

# Locate project root relative to this script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

# Ensure commands run from the project root directory
cd "$SCRIPT_DIR" || exit 1

# Check prerequisites: Java and Maven
if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java is not installed or not in PATH."
    echo "Please install Java 17 (e.g., 'brew install openjdk@17')."
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "Error: Maven is not installed or not in PATH."
    echo "Please install Maven (e.g., 'brew install maven')."
    exit 1
fi

# Check for .env file
if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env file not found at $ENV_FILE"
    echo ""
    echo "Please create a .env file in the project root with the following variables:"
    echo "  DB_HOST=localhost"
    echo "  DB_PORT=5432"
    echo "  DB_NAME=library_db"
    echo "  DB_USER=your_db_user"
    echo "  DB_PASS=your_db_password"
    exit 1
fi

# Load DB_HOST, DB_PORT, DB_NAME, DB_USER, and DB_PASS from .env
# Preserves variables already explicitly set in the terminal environment
while IFS= read -r line || [ -n "$line" ]; do
    # Trim leading and trailing whitespace
    trimmed=$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    # Skip empty lines and comments
    case "$trimmed" in
        \#*|"") continue ;;
    esac

    if [[ "$trimmed" =~ ^([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=[[:space:]]*(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        val="${BASH_REMATCH[2]}"
        # Strip outer quotes (double or single)
        if [[ "$val" =~ ^\"(.*)\"$ ]] || [[ "$val" =~ ^\'(.*)\'$ ]]; then
            val="${BASH_REMATCH[1]}"
        fi

        case "$key" in
            DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASS)
                # Only set if not already set and non-empty in the terminal
                if [ -z "${!key:-}" ]; then
                    export "$key"="$val"
                fi
                ;;
        esac
    fi
done < "$ENV_FILE"

# Check required database environment variables
missing_vars=()
[ -z "${DB_NAME:-}" ] && missing_vars+=("DB_NAME")
[ -z "${DB_USER:-}" ] && missing_vars+=("DB_USER")
[ -z "${DB_PASS:-}" ] && missing_vars+=("DB_PASS")
[ -z "${DB_HOST:-}" ] && missing_vars+=("DB_HOST")
[ -z "${DB_PORT:-}" ] && missing_vars+=("DB_PORT")

if [ ${#missing_vars[@]} -ne 0 ]; then
    echo "Error: The following required variable(s) are missing or empty:"
    for var in "${missing_vars[@]}"; do
        echo "  - $var"
    done
    echo ""
    echo "Please configure them in $ENV_FILE or in your terminal environment."
    exit 1
fi

# Launch application using existing Maven configuration
mvn exec:java
exit $?
