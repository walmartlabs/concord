workers="1"
num_retries=0
MAX_RETRIES=5

while [ "$workers" != "0" ] && [ "$num_retries" -lt "$MAX_RETRIES" ]
do
  echo "[$HOSTNAME]: Agent is still executing a process.. enabling maintenance mode and checking the number of workers"
  mmode_response=`wget -qO - --post-data="" http://127.0.0.1:8010/maintenance-mode`

  mmode_enabled=`echo $mmode_response | sed -n 's/^.*\"maintenanceMode\":\([a-z]*\).*$/\1/p'`
  if [ "$mmode_enabled" == "true" ]; then
    echo "[$HOSTNAME]: Maintenance mode enabled: $mmode_enabled"

    workers=`echo $mmode_response | sed -n 's/^.*\"workersAlive\":\([0-9]*\).*$/\1/p'`
    echo "[$HOSTNAME]: Number of workers: $workers"

  else
    echo "[$HOSTNAME]: trouble enabling maintenance mode"
    num_retries=`expr $num_retries + 1`
  fi

sleep 5
done

if [ "$num_retries" -ge "$MAX_RETRIES" ]; then
  echo "[$HOSTNAME]: Number of retries to enable exceeded $MAX_RETRIES times. Exiting ..."
  exit 1
fi

echo "[$HOSTNAME]: There are no processes running on this agent. Terminating..."