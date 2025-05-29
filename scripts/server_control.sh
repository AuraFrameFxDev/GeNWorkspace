#!/bin/bash

# Server control script for Genesis AI
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="/tmp/genesis_ai.pid"
LOG_FILE="$SERVER_DIR/server.log"

# Function to start the server
start_server() {
    if [ -f "$PID_FILE" ]; then
        echo "Server is already running (PID: $(cat $PID_FILE))"
        return 1
    fi
    
    echo "Starting Genesis AI server..."
    cd "$SERVER_DIR" || { echo "Error: Could not change to server directory"; return 1; }
    
    # Install requirements if not already installed
    if ! pip show -q fastapi uvicorn firebase-admin; then
        echo "Installing Python dependencies..."
        pip install -r requirements.txt || { echo "Failed to install dependencies"; return 1; }
    fi
    
    # Start the server in the background
    nohup python -m uvicorn server.app:app --host 0.0.0.0 --port 8000 >> "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    echo "Server started with PID: $(cat $PID_FILE)"
    echo "Logs: $LOG_FILE"
}

# Function to stop the server
stop_server() {
    if [ ! -f "$PID_FILE" ]; then
        echo "Server is not running"
        return 1
    fi
    
    echo "Stopping Genesis AI server (PID: $(cat $PID_FILE))..."
    kill -9 "$(cat "$PID_FILE")" 2>/dev/null
    rm -f "$PID_FILE"
    echo "Server stopped"
}

# Function to check server status
status_server() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "Server is running (PID: $(cat $PID_FILE))"
        echo "API: http://localhost:8000"
        echo "Docs: http://localhost:8000/docs"
    else
        echo "Server is not running"
        [ -f "$PID_FILE" ] && rm -f "$PID_FILE"
    fi
}

# Function to view logs
tail_logs() {
    if [ ! -f "$LOG_FILE" ]; then
        echo "No log file found at $LOG_FILE"
        return 1
    fi
    
    echo "Tailing server logs (Ctrl+C to exit)..."
    tail -f "$LOG_FILE"
}

# Main script logic
case "$1" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        stop_server
        sleep 2
        start_server
        ;;
    status)
        status_server
        ;;
    logs)
        tail_logs
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        echo "  start   - Start the server"
        echo "  stop    - Stop the server"
        echo "  restart - Restart the server"
        echo "  status  - Check server status"
        echo "  logs    - View server logs"
        exit 1
        ;;
esac

exit 0
