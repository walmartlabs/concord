#!/usr/bin/env bash

set -euo pipefail

out="${1:-/dev/stdout}"

required_vars=(
    MAVEN_MIRROR
    NEXUS_URL
    MAVEN_SITE_URL
    NODE_DOWNLOAD_URL
    NPM_INSTALL_CMD
    AGENT_IMAGE
    ANSIBLE_IMAGE
    CONSOLE_IMAGE
    DB_IMAGE
    DIND_IMAGE
    OLDAP_IMAGE
    S3MOCK_IMAGE
    SELENIUM_IMAGE
    SERVER_IMAGE
    SOCAT_IMAGE
)

for name in "${required_vars[@]}"; do
    if [ -z "${!name:-}" ]; then
        echo "Missing required environment variable: ${name}" >&2
        exit 1
    fi
done

xml_escape() {
    printf '%s' "$1" | sed \
        -e 's/&/\&amp;/g' \
        -e 's/"/\&quot;/g' \
        -e "s/'/\&apos;/g" \
        -e 's/</\&lt;/g' \
        -e 's/>/\&gt;/g'
}

cat >"${out}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings>
    <mirrors>
        <mirror>
            <id>walmart-gec</id>
            <mirrorOf>external:*</mirrorOf>
            <url>$(xml_escape "${MAVEN_MIRROR}")</url>
        </mirror>
    </mirrors>

    <profiles>
        <profile>
            <id>looper</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <scm.connection>\${env.SCM_CONNECTION}</scm.connection>
            </properties>
            <repositories>
                <repository>
                    <id>central</id>
                    <url>http://central</url>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                    <snapshots>
                        <enabled>true</enabled>
                    </snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>central</id>
                    <url>http://central</url>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                    <snapshots>
                        <enabled>true</enabled>
                    </snapshots>
                </pluginRepository>
            </pluginRepositories>
        </profile>

        <profile>
            <id>walmart</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <node.downloadRoot>$(xml_escape "${NODE_DOWNLOAD_URL}")</node.downloadRoot>
                <npm.installCmd>$(xml_escape "${NPM_INSTALL_CMD}")</npm.installCmd>

                <public.serverId>walmart-gec</public.serverId>
                <public.nexusUrl>$(xml_escape "${NEXUS_URL}")</public.nexusUrl>
                <public-release.serverId>\${public.serverId}</public-release.serverId>
                <public-release.url>\${public.nexusUrl}/content/repositories/devtools</public-release.url>
                <public-snapshot.serverId>\${public.serverId}</public-snapshot.serverId>
                <public-snapshot.url>\${public.nexusUrl}/content/repositories/devtools-snapshots</public-snapshot.url>

                <site.id>mvn-site</site.id>
                <site.url>$(xml_escape "${MAVEN_SITE_URL}")</site.url>

                <agent.image>$(xml_escape "${AGENT_IMAGE}")</agent.image>
                <ansible.image>$(xml_escape "${ANSIBLE_IMAGE}")</ansible.image>
                <console.image>$(xml_escape "${CONSOLE_IMAGE}")</console.image>
                <db.image>$(xml_escape "${DB_IMAGE}")</db.image>
                <dind.image>$(xml_escape "${DIND_IMAGE}")</dind.image>
                <oldap.image>$(xml_escape "${OLDAP_IMAGE}")</oldap.image>
                <s3mock.image>$(xml_escape "${S3MOCK_IMAGE}")</s3mock.image>
                <selenium.image>$(xml_escape "${SELENIUM_IMAGE}")</selenium.image>
                <server.image>$(xml_escape "${SERVER_IMAGE}")</server.image>
                <socat.image>$(xml_escape "${SOCAT_IMAGE}")</socat.image>
            </properties>
        </profile>
    </profiles>
</settings>
EOF
