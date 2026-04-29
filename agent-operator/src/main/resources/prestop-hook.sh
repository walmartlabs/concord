#!/usr/bin/env bash

echo "PreStop hook started at $(date)"

MAX_RETRIES=5
RETRY_DELAY=1

current_workers="1"
num_retries=0

while [ "$current_workers" != "0" ] && [ "$num_retries" -lt "$MAX_RETRIES" ]
do
  echo "[$HOSTNAME]: Agent is still executing a process.. enabling maintenance mode and checking the number of current_workers"

  response=$(exec 3<>/dev/tcp/127.0.0.1/8010;
             echo -e "POST /maintenance-mode HTTP/1.1\r\nHost: 127.0.0.1:8010\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" >&3;
             cat <&3;
             exec 3>&-)

  mmode_response=$(echo "$response" | sed -n '/^\r*$/,$p' | tail -n +2)
  mmode_enabled=$(echo "$mmode_response" | sed -n 's/^.*\"maintenanceMode\":\([a-z]*\).*$/\1/p')

  if [ "$mmode_enabled" == "true" ]; then
    echo "[$HOSTNAME]: Maintenance mode enabled: $mmode_enabled"
    current_workers=$(echo "$mmode_response" | sed -n 's/^.*\"workersAlive\":\([0-9]*\).*$/\1/p')
    echo "[$HOSTNAME]: Number of current_workers: $current_workers"
  else
    echo "[$HOSTNAME]: trouble enabling maintenance mode"
    num_retries=$(("$num_retries" + 1))
  fi

  sleep ${RETRY_DELAY}
done

if [ "$num_retries" -ge "$MAX_RETRIES" ]; then
  echo "[$HOSTNAME]: Number of retries to enable exceeded $MAX_RETRIES times. Exiting ..."
  exit 1
fi

echo "[$HOSTNAME]: There are no processes running on this agent. Terminating..."
