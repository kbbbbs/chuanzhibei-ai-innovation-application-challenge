#!/usr/bin/env sh
set -eu
repository=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
action=${1:-up}
mode=${2:-managed}
case "$mode" in managed|external) ;; *) echo "Mode must be managed or external." >&2; exit 2 ;; esac
if [ "$action" = init ]; then
    command -v openssl >/dev/null
    mkdir -p "$repository/deploy/secrets"
    chmod 700 "$repository/deploy/secrets"
    for name in managed-db-password mysql-root-password; do
        secret_file="$repository/deploy/secrets/$name"
        if [ ! -e "$secret_file" ]; then
            (umask 077; openssl rand -base64 48 > "$secret_file")
            # The private parent directory restricts host access; Docker mounts
            # the individual secret read-only for the non-root application UID.
            chmod 644 "$secret_file"
        fi
    done
    echo "Database secret files are ready. Existing files were preserved."
    exit 0
fi
set -- docker compose --project-directory "$repository" -f "$repository/compose.yaml"
if [ "$mode" = managed ]; then
    set -- "$@" -f "$repository/compose.mysql.yaml"
fi
case "$action" in
    up) set -- "$@" up -d --build ;;
    down) set -- "$@" down ;;
    status) set -- "$@" ps ;;
    *) echo "Action must be init, up, down, or status." >&2; exit 2 ;;
esac
exec "$@"
