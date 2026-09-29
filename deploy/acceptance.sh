#!/usr/bin/env bash
# The acceptance checks in deploy/README.md §6, as one command, run from a laptop
# against the live site. Every check attacks or probes the deployment rather than
# reading its config, and prints the evidence next to its verdict.
#
#   ./deploy/acceptance.sh                  # after every redeploy (README §7)
#
# Exits 1 on any FAIL. A check that cannot run prints "?" and never PASS --
# "could not check" and "checked, fine" must not look the same (session-check.sh).
#
# Check 9 reads rankings, and every read needs an account since 2026-09-29: set
# PROD_TOKEN and LOCAL_TOKEN to a member's access token on each side (the
# "accessToken" a sign-in returns; it lives 15 minutes).
#
# Not covered, on purpose: 3 needs a real browser and a real account; 4b needs a
# second real client (a phone off wifi); 8 is the ingest command in §6, which runs
# on the VM. 4a and 4c spend this machine's auth bucket for up to a minute: wait that
# long between runs, or 4a reads the empty bucket it left behind as a FAIL.
set -u
API=${API:-https://api.fantasykai.com}
WWW=${WWW:-https://www.fantasykai.com}
VM=${VM:-163.192.220.190}
LOCAL_API=${LOCAL_API:-http://localhost:8080}
PROD_TOKEN=${PROD_TOKEN:-}
LOCAL_TOKEN=${LOCAL_TOKEN:-}
fails=0
pass()    { printf 'PASS  %-4s %s\n' "$1" "$2"; }
fail()    { printf 'FAIL  %-4s %s\n' "$1" "$2"; fails=$((fails+1)); }
unknown() { printf '?     %-4s %s\n' "$1" "$2"; }
login() { # $1 = path, rest = extra curl args; prints the status code
  local path=$1; shift
  curl -sS -o /dev/null -w '%{http_code}' -X POST "$API$path" \
    -H 'Content-Type: application/json' "$@" \
    --data '{"email":"acceptance-check@fantasykai.invalid","password":"not-the-password-1"}'
}

# 1 -- liveness
body=$(curl -sS -m 10 "$API/actuator/health/liveness")
[[ $body == '{"status":"UP"}' ]] && pass 1 "liveness $body" || fail 1 "liveness said: $body"

# 2 -- anonymous aggregate health: a status, and no component internals.
# "groups" (probe group names) is expected; "components"/"details" would be a leak.
body=$(curl -sS -m 10 "$API/actuator/health")
[[ $body == '{"status":"'* && $body != *components* && $body != *details* ]] \
  && pass 2 "health $body" || fail 2 "health leaked or failed: $body"

# 4a -- six logins, each with a different forged X-Forwarded-For; the 6th must be 429
codes=""
for i in 1 2 3 4 5 6; do codes+="$(login /api/v1/auth/login -H "X-Forwarded-For: 198.51.100.$i") "; done
[[ $codes == "401 401 401 401 401 429 " ]] && pass 4a "codes: $codes" || fail 4a "codes: $codes (expected 401x5 then 429)"

# 4c -- bucket is now full: each bypass attempt must also be 429
c1=$(login /api/v1/auth/login -H 'Forwarded: for=203.0.113.77')
c2=$(login /api/v1/auth/login -H 'X-Forwarded-Prefix: /x')
c3=$(login /api/v1/%61uth/login)
[[ "$c1 $c2 $c3" == "429 429 429" ]] && pass 4c "Forwarded=$c1 Prefix=$c2 encoded=$c3" \
  || fail 4c "Forwarded=$c1 Prefix=$c2 encoded=$c3 (401 anywhere = bypass live)"

# 5 -- CORS: hostile origins refused, the real one allowed with credentials
evil=$(curl -sS -o /dev/null -w '%{http_code}' -X OPTIONS "$API/api/v1/rankings" \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: GET')
prev=$(curl -sS -o /dev/null -w '%{http_code}' -X OPTIONS "$API/api/v1/rankings" \
  -H 'Origin: https://fantasykai.vercel.app' -H 'Access-Control-Request-Method: GET')
real=$(curl -sS -D - -o /dev/null -X OPTIONS "$API/api/v1/rankings" \
  -H "Origin: $WWW" -H 'Access-Control-Request-Method: GET' | tr -d '\r' | grep -iE '^access-control-allow-(origin|credentials)' | sort | tr '\n' ' ')
[[ $evil == 403 && $prev == 403 && $real == *"allow-credentials: true"*"allow-origin: $WWW"* ]] \
  && pass 5 "evil=$evil vercel.app=$prev real: $real" || fail 5 "evil=$evil vercel.app=$prev real: $real"

# 6 -- http redirects to https; HSTS actually sent
redir=$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' "http://${API#https://}/actuator/health/liveness")
hsts=$(curl -sS -D - -o /dev/null "$API/actuator/health/liveness" | tr -d '\r' | grep -i '^strict-transport-security')
[[ $redir == "308 https://"* && -n $hsts ]] && pass 6 "$redir · $hsts" || fail 6 "redirect: $redir · hsts: ${hsts:-none}"

# 7 -- database, redis and the backend's own port are not on the internet.
# A failed connect is only evidence of "closed" if the same nc, from here, can open
# a port that IS open -- otherwise a missing nc, a netcat whose -G means something
# else (macOS: connect timeout), or a laptop with no network reads as three closed
# ports and a PASS. 443 is the positive control.
probe() { nc -z -G 5 "$VM" "$1" >/dev/null 2>&1 && echo open || echo closed; }
if ! command -v nc >/dev/null 2>&1 || [[ $(probe 443) != open ]]; then
    unknown 7 "cannot open $VM:443 with this nc -- no evidence either way about 5432/6379/8080"
else
    p5432=$(probe 5432); p6379=$(probe 6379); p8080=$(probe 8080)
    [[ "$p5432 $p6379 $p8080" == "closed closed closed" ]] \
      && pass 7 "5432/6379/8080 closed; control 443 open" \
      || fail 7 "5432=$p5432 6379=$p6379 8080=$p8080"
fi

# 9 -- production and a local backend agree: 2025 top 10 under every preset.
# Needs the local stack on $LOCAL_API. A side that errors or comes back short is a
# FAIL, never a match: two identical errors once compared equal here (F12).
if ! curl -sf -m 3 -o /dev/null "$LOCAL_API/actuator/health/liveness"; then
    unknown 9 "no local backend on $LOCAL_API -- start one to compare"
else
    out=$(API="$API" LOCAL_API="$LOCAL_API" PROD_TOKEN="$PROD_TOKEN" LOCAL_TOKEN="$LOCAL_TOKEN" python3 - 2>&1 <<'PY'
import json, os, urllib.request
TOKENS = {os.environ["API"]: os.environ["PROD_TOKEN"], os.environ["LOCAL_API"]: os.environ["LOCAL_TOKEN"]}
def top10(base, pid):
    url = f"{base}/api/v1/rankings?profileId={pid}&season=2025&size=10"
    request = urllib.request.Request(url)
    if TOKENS[base]:
        request.add_header("Authorization", f"Bearer {TOKENS[base]}")
    with urllib.request.urlopen(request, timeout=20) as r:
        rows = json.load(r)["content"]
    if len(rows) != 10:
        raise SystemExit(f"{base} returned {len(rows)} rows for profile {pid}")
    return [(x["playerId"], x["points"]) for x in rows]
bad = [p for p in (1, 2, 3, 4) if top10(os.environ["API"], p) != top10(os.environ["LOCAL_API"], p)]
print("differ: " + ",".join(map(str, bad)) if bad else "identical")
PY
    )
    [[ $out == identical ]] && pass 9 "2025 top 10 identical to local under presets 1-4" \
        || fail 9 "production vs local: ${out##*$'\n'}"
fi

# 10 -- the reads need an account (since 2026-09-29): anonymous is refused with
# problem+json, on a read and on the scoring profiles alike.
r10=$(curl -sS -o /dev/null -w '%{http_code} %{content_type}' "$API/api/v1/rankings?profileId=2&season=2025&size=1")
p10=$(curl -sS -o /dev/null -w '%{http_code}' "$API/api/v1/scoring-profiles")
[[ $r10 == "401 application/problem+json"* && $p10 == 401 ]] && pass 10 "anonymous rankings=$r10 · profiles=$p10" \
  || fail 10 "anonymous rankings=$r10 · profiles=$p10 (anything but 401 means an anonymous caller got past the chain)"

exit $((fails > 0))
