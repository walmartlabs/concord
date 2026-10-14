#!/bin/sh

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <socket-directory> <image-archive>" >&2
    exit 2
fi

socket_dir=$1
socket_path=$socket_dir/docker.sock
image_archive=$2

# The concord-agent image runs as concord with GID 456.
agent_gid=456

if ! mkdir "$socket_dir"; then
    echo "failed to create socket directory: $socket_dir" >&2
    exit 1
fi

dockerd_pid=
cleanup() {
    status=$?
    trap - EXIT TERM INT
    if [ -n "$dockerd_pid" ]; then
        kill -TERM "$dockerd_pid" 2>/dev/null || :
        ticks=0
        while kill -0 "$dockerd_pid" 2>/dev/null && [ "$ticks" -lt 5 ]; do
            sleep 1
            ticks=$((ticks + 1))
        done
        if kill -0 "$dockerd_pid" 2>/dev/null; then
            kill -KILL "$dockerd_pid" 2>/dev/null || :
        fi
        wait "$dockerd_pid" 2>/dev/null || :
    fi
    rm -f "$socket_path"
    rmdir "$socket_dir" 2>/dev/null || :
    exit "$status"
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

dockerd-entrypoint.sh dockerd --host "unix://$socket_path" --group="$agent_gid" --tls=false &
dockerd_pid=$!

seconds=0
while [ "$seconds" -lt 60 ]; do
    if docker --host "unix://$socket_path" info >/dev/null 2>&1; then
        break
    fi
    if ! kill -0 "$dockerd_pid" 2>/dev/null; then
        echo "dockerd exited before becoming ready" >&2
        exit 1
    fi
    sleep 1
    seconds=$((seconds + 1))
done

if [ "$seconds" -ge 60 ]; then
    echo "dockerd did not become ready within 60 seconds" >&2
    exit 1
fi

if ! docker --host "unix://$socket_path" load --input "$image_archive"; then
    echo "failed to load image archive: $image_archive" >&2
    exit 1
fi

printf '%s\n' CONCORD_IT_DIND_READY
wait "$dockerd_pid"
