#!/usr/bin/env bash
# =============================================================================
# TradePulse - Start All Microservices
# =============================================================================
# Usage:
#   ./scripts/start-services.sh              # start all services
#   ./scripts/start-services.sh auth order   # start specific services
#   ./scripts/start-services.sh --stop       # stop all running services
#   ./scripts/start-services.sh --status     # show status of all services
#   ./scripts/start-services.sh --logs auth  # tail logs for a service
# =============================================================================

set -euo pipefail

# --------------- Config -------------------------------------------------------
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/scripts/logs"
PID_DIR="$PROJECT_ROOT/scripts/pids"
MVN="mvn"

# Colour codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Colour

# --------------- Service definitions -----------------------------------------
# Format: "module-dir-name:display-name:port"
declare -a SERVICES=(
  "api-gateway-service:API Gateway:8080"
  "auth-service:Auth Service:8081"
  "user-service:User Service:8082"
  "order-service:Order Service:8083"
  "market-data-service:Market Data:8084"
  "matching-engine:Matching Engine:8085"
  "portfolio-service:Portfolio Service:8086"
  "notification-service:Notification:8087"
  "reporting-service:Reporting:8088"
)

# --------------- Helpers ------------------------------------------------------
log_info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
log_ok()      { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_section() { echo -e "\n${BOLD}${CYAN}========== $* ==========${NC}"; }

service_dir()  { echo "${1%%:*}"; }            # api-gateway-service
service_name() { local p="${1#*:}"; echo "${p%%:*}"; }   # API Gateway
service_port() { echo "${1##*:}"; }            # 8080
pid_file()     { echo "$PID_DIR/$(service_dir "$1").pid"; }
log_file()     { echo "$LOG_DIR/$(service_dir "$1").log"; }

mkdir -p "$LOG_DIR" "$PID_DIR"

# --------------- Resolve requested services ----------------------------------
resolve_services() {
  local requested=("$@")
  if [[ ${#requested[@]} -eq 0 ]]; then
    printf "%s\n" "${SERVICES[@]}"
    return
  fi

  local result=()
  for svc in "${SERVICES[@]}"; do
    local dir; dir="$(service_dir "$svc")"
    local name; name="$(service_name "$svc")"
    for req in "${requested[@]}"; do
      if [[ "$dir" == *"$req"* || "${name,,}" == *"${req,,}"* ]]; then
        result+=("$svc")
        break
      fi
    done
  done
  if [[ ${#result[@]} -gt 0 ]]; then
    printf "%s\n" "${result[@]}"
  fi
}

# --------------- Start a single service ---------------------------------------
start_service() {
  local svc="$1"
  local dir;  dir="$(service_dir  "$svc")"
  local name; name="$(service_name "$svc")"
  local port; port="$(service_port "$svc")"
  local pid_f; pid_f="$(pid_file "$svc")"
  local log_f; log_f="$(log_file  "$svc")"

  # Check already running
  if [[ -f "$pid_f" ]]; then
    local old_pid; old_pid="$(cat "$pid_f")"
    if kill -0 "$old_pid" 2>/dev/null; then
      log_warn "$name already running (PID $old_pid). Skipping."
      return 0
    else
      rm -f "$pid_f"
    fi
  fi

  log_info "Starting ${BOLD}$name${NC} on port $port ..."

  # Launch in background; redirect stdout+stderr to log file
  nohup "$MVN" spring-boot:run \
    -pl "services/$dir" \
    --no-transfer-progress \
    > "$log_f" 2>&1 &

  local pid=$!
  echo "$pid" > "$pid_f"
  log_ok "$name started (PID $pid) → $(basename "$log_f")"
}

# --------------- Stop a single service ----------------------------------------
stop_service() {
  local svc="$1"
  local name; name="$(service_name "$svc")"
  local pid_f; pid_f="$(pid_file "$svc")"

  if [[ ! -f "$pid_f" ]]; then
    log_warn "$name — no PID file found."
    return 0
  fi

  local pid; pid="$(cat "$pid_f")"
  if kill -0 "$pid" 2>/dev/null; then
    log_info "Stopping $name (PID $pid) ..."
    kill "$pid"
    local waited=0
    while kill -0 "$pid" 2>/dev/null && [[ $waited -lt 30 ]]; do
      sleep 1; ((waited++))
    done
    if kill -0 "$pid" 2>/dev/null; then
      log_warn "$name did not stop gracefully — sending SIGKILL"
      kill -9 "$pid"
    fi
    log_ok "$name stopped."
  else
    log_warn "$name — PID $pid not running."
  fi
  rm -f "$pid_f"
}

# --------------- Status -------------------------------------------------------
show_status() {
  log_section "TradePulse Service Status"
  printf "%-28s %-6s %-8s %s\n" "Service" "Port" "Status" "PID"
  printf '%0.s─' {1..60}; echo
  for svc in "${SERVICES[@]}"; do
    local name; name="$(service_name "$svc")"
    local port; port="$(service_port "$svc")"
    local pid_f; pid_f="$(pid_file "$svc")"
    local status pid_str

    if [[ -f "$pid_f" ]]; then
      local pid; pid="$(cat "$pid_f")"
      if kill -0 "$pid" 2>/dev/null; then
        status="${GREEN}RUNNING${NC}"
        pid_str="$pid"
      else
        status="${RED}DEAD${NC}   "
        pid_str="$pid (stale)"
      fi
    else
      status="${YELLOW}STOPPED${NC}"
      pid_str="—"
    fi

    printf "%-28s %-6s " "$name" "$port"
    echo -e "${status}  ${pid_str}"
  done
  echo
}

# --------------- Tail logs ----------------------------------------------------
tail_logs() {
  local target="${1:-}"
  if [[ -z "$target" ]]; then
    log_error "Usage: $0 --logs <service-name-or-dir>"
    exit 1
  fi

  local matched_log=""
  for svc in "${SERVICES[@]}"; do
    local dir;  dir="$(service_dir  "$svc")"
    local name; name="$(service_name "$svc")"
    if [[ "$dir" == *"$target"* || "${name,,}" == *"${target,,}"* ]]; then
      matched_log="$(log_file "$svc")"
      break
    fi
  done

  if [[ -z "$matched_log" ]]; then
    log_error "No service matching '$target' found."
    exit 1
  fi

  if [[ ! -f "$matched_log" ]]; then
    log_warn "Log file not found yet: $matched_log"
    exit 0
  fi

  log_info "Tailing $matched_log (Ctrl+C to stop)"
  tail -f "$matched_log"
}

# --------------- Check prerequisites -----------------------------------------
check_prereqs() {
  if ! command -v "$MVN" &>/dev/null; then
    log_error "Maven not found. Please install Maven and ensure it is on your PATH."
    exit 1
  fi

  if [[ ! -f "$PROJECT_ROOT/pom.xml" ]]; then
    log_error "Root pom.xml not found at $PROJECT_ROOT. Are you in the right directory?"
    exit 1
  fi

  # Check Docker infra
  log_info "Checking infrastructure containers..."
  local infra_ok=true
  for container in tradepulse-postgres tradepulse-kafka tradepulse-redis tradepulse-mongo; do
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${container}$"; then
      log_warn "Container '$container' is not running."
      infra_ok=false
    fi
  done

  if [[ "$infra_ok" == false ]]; then
    echo
    log_warn "Some infrastructure containers are not running."
    log_warn "Start them with: cd docker && docker-compose up -d"
    echo
    read -rp "Continue anyway? [y/N] " ans
    [[ "${ans,,}" == "y" ]] || exit 1
  else
    log_ok "All infrastructure containers are running."
  fi
}

# --------------- Main ---------------------------------------------------------
main() {
  local mode="start"
  local args=()

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --stop)    mode="stop";   shift ;;
      --status)  mode="status"; shift ;;
      --logs)    mode="logs";   shift; args+=("${1:-}"); shift ;;
      --help|-h)
        echo "Usage: $0 [--stop|--status|--logs <svc>] [service...]"
        exit 0 ;;
      *) args+=("$1"); shift ;;
    esac
  done

  case "$mode" in
    status) show_status; exit 0 ;;
    logs)   tail_logs "${args[0]:-}"; exit 0 ;;
    stop)
      log_section "Stopping TradePulse Services"
      local to_stop
      mapfile -t to_stop < <(resolve_services "${args[@]}")
      for svc in "${to_stop[@]}"; do
        [[ -n "$svc" ]] && stop_service "$svc"
      done
      log_ok "Done."
      exit 0
      ;;
    start)
      log_section "TradePulse Service Launcher"
      check_prereqs

      local to_start
      mapfile -t to_start < <(resolve_services "${args[@]}")

      if [[ ${#to_start[@]} -eq 0 || -z "${to_start[0]}" ]]; then
        log_error "No matching services found for: ${args[*]:-}"
        exit 1
      fi

      log_info "Services to start: ${#to_start[@]}"
      echo

      for svc in "${to_start[@]}"; do
        [[ -n "$svc" ]] && start_service "$svc"
        sleep 2  # stagger startup to avoid port/DB contention
      done

      echo
      log_section "Startup Complete"
      log_info "Logs are in: $LOG_DIR"
      log_info "Run '${BOLD}$0 --status${NC}' to check service health."
      log_info "Run '${BOLD}$0 --logs <service>${NC}' to tail a service log."
      log_info "Run '${BOLD}$0 --stop${NC}' to stop all services."
      echo
      ;;
  esac
}

main "$@"
