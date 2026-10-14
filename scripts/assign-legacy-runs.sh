#!/bin/sh
set -eu
# Run only after signing in with the intended Google account. Never runs on startup.
task_owner_id=${1:-}
task_mode=${2:---preview}
if ! printf '%s' "$task_owner_id" | LC_ALL=C grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'; then
  echo "Usage: sh scripts/assign-legacy-runs.sh USER_UUID [--preview|--apply]" >&2
  exit 2
fi
case "$task_mode" in
  --preview)
    docker compose exec -T db psql -X -U reconcilex -d reconcilex -v ON_ERROR_STOP=1 -v owner="$task_owner_id" <<'SQL'
SELECT id, display_name, email FROM app_users WHERE id=:'owner'::uuid;
SELECT COUNT(*) AS unowned_runs FROM reconciliation_runs WHERE owner_id IS NULL;
SQL
    ;;
  --apply)
    docker compose exec -T db psql -X -U reconcilex -d reconcilex -v ON_ERROR_STOP=1 -v owner="$task_owner_id" <<'SQL'
BEGIN;
LOCK TABLE reconciliation_runs IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE assignment_target AS SELECT id FROM app_users WHERE id=:'owner'::uuid;
DO $$ BEGIN IF (SELECT COUNT(*) FROM assignment_target) <> 1 THEN RAISE EXCEPTION 'Target account does not exist'; END IF; END $$;
UPDATE reconciliation_runs SET owner_id=(SELECT id FROM assignment_target) WHERE owner_id IS NULL;
COMMIT;
SQL
    ;;
  *) echo "Use --preview or --apply" >&2; exit 2 ;;
esac
