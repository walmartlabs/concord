#!/bin/sh
cat <<EOF
{
    "local": {
        "hosts": ["127.0.0.1"],
        "vars": {
            "ansible_connection": "local"
        }
    }
}
EOF
