#!/usr/bin/env bash

# Commands:
#   put <key> <value>   Insert or update a key
#   get <key>           Get the value of a key
#   get-all             Print all key-value pairs
#   perf <n>            Run n parallel get-all clients, write CSV files
#   compact             Trigger segment compaction
#   help                Show this help
#   exit / quit         Exit the shell

HOST="${BITCASK_HOST:-localhost}"
PORT="${BITCASK_PORT:-8080}"
BASE_URL="http://${HOST}:${PORT}"

BOLD=$'\033[1m'
DIM=$'\033[2m'
GREEN=$'\033[32m'
CYAN=$'\033[36m'
YELLOW=$'\033[33m'
RED=$'\033[31m'
R=$'\033[0m'

api_get_all() { curl -sf --max-time 5 "${BASE_URL}/keys"; }
api_get()     { curl -sf --max-time 5 "${BASE_URL}/keys/${1}"; }
api_put()     { curl -sf -X PUT --max-time 5 -H "Content-Type: text/plain" -d "$2" "${BASE_URL}/keys/${1}"; }
api_compact() { curl -sf -X POST --max-time 30 "${BASE_URL}/compact"; }

json_to_csv() {
    python3 -c "
import sys, json, csv
data = json.load(sys.stdin)
writer = csv.writer(sys.stdout)
writer.writerow(['key','value'])
for k, v in data.items():
    writer.writerow([k, v])
"
}

print_help() {
    echo
    echo "  ${BOLD}Commands:${R}"
    echo "  ${CYAN}put${R} <key> <value>   Insert or update a key"
    echo "  ${CYAN}get${R} <key>           Get value of a key"
    echo "  ${CYAN}get-all${R}             Print all key-value pairs"
    echo "  ${CYAN}perf${R} <n>            Spawn n parallel clients, write CSV files"
    echo "  ${CYAN}compact${R}             Compact immutable segments"
    echo "  ${CYAN}help${R}                Show this help"
    echo "  ${CYAN}exit${R}                Exit"
    echo
}

echo
echo "  ${BOLD}Bitcask Shell${R}  ${DIM}${BASE_URL}${R}"
echo "  Type ${CYAN}help${R} for available commands."
echo


while true; do
    printf "${GREEN}bitcask>${R} "
    read -r line
    [[ -z "$line" ]] && continue

    read -ra parts <<< "$line"
    cmd="${parts[0]}"

    case "$cmd" in

        put)
            if [[ ${#parts[@]} -lt 3 ]]; then
                echo "  ${RED}Usage: put <key> <value>${R}"
                continue
            fi
            key="${parts[1]}"
            value="${parts[@]:2}"
            api_put "$key" "$value" > /dev/null
            echo "  ${GREEN}OK${R}"
            ;;

        get)
            if [[ ${#parts[@]} -lt 2 ]]; then
                echo "  ${RED}Usage: get <key>${R}"
                continue
            fi
            key="${parts[1]}"
            result=$(api_get "$key")
            if [[ -z "$result" ]]; then
                echo "  ${YELLOW}(not found)${R}"
            else
                echo "  $result"
            fi
            ;;

        get-all)
            json=$(api_get_all)
            if [[ -z "$json" ]]; then
                echo "  ${RED}Could not reach server.${R}"
                continue
            fi
            ts=$(date +%s)
            outfile="${ts}.csv"

            echo "$json" | json_to_csv > "$outfile"

            # Display to terminal
            count=0
            while IFS=, read -r k v; do
                [[ "$k" == "key" ]] && continue
                printf "  ${CYAN}%-30s${R}  %s\n" "$k" "$v"
                (( count++ ))
            done < "$outfile"
            echo "  ${DIM}${count} key(s) — saved to ${outfile}${R}"
            ;;

        perf)
            if [[ ${#parts[@]} -lt 2 ]] || ! [[ "${parts[1]}" =~ ^[0-9]+$ ]]; then
                echo "  ${RED}Usage: perf <n>${R}"
                continue
            fi
            n="${parts[1]}"
            ts=$(date +%s)
            echo "  ${YELLOW}Spawning ${n} clients...${R}"

            worker() {
                local tid="$1" ts="$2" base="$3"
                local outfile="${ts}_thread_${tid}.csv"
                local json
                json=$(curl -sf --max-time 5 "${base}/keys" 2>/dev/null)
                echo "$json" | python3 -c "
import sys, json, csv
data = json.load(sys.stdin)
writer = csv.writer(sys.stdout)
writer.writerow(['key','value'])
for k, v in data.items():
    writer.writerow([k, v])
" > "$outfile"
                echo "  [thread ${tid}] → ${outfile}"
            }
            export -f worker
            pids=()
            for (( i=1; i<=n; i++ )); do
                bash -c "worker $i $ts '$BASE_URL'" &
                pids+=($!)
            done
            for pid in "${pids[@]}"; do wait "$pid"; done
            echo "  ${GREEN}Done.${R}"
            ;;

        compact)
            echo "  ${DIM}Compacting...${R}"
            api_compact > /dev/null
            echo "  ${GREEN}OK${R}"
            ;;

        help)
            print_help
            ;;

        exit|quit)
            echo "  Bye!"
            break
            ;;

        *)
            echo "  ${RED}Unknown command:${R} ${cmd}  (type ${CYAN}help${R})"
            ;;
    esac
done