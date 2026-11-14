/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
export const PROTOCOL = 'concord-process-card-v1';
export const MAX_ARGUMENT_SIZE = 65536;

export const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === 'object' && value !== null && !Array.isArray(value);

const isJson = (value: unknown, seen: Set<object>, depth: number): boolean => {
    if (depth > 16) {
        return false;
    }
    if (value === null || typeof value === 'string' || typeof value === 'boolean') {
        return true;
    }
    if (typeof value === 'number') {
        return Number.isFinite(value);
    }
    if (typeof value !== 'object' || seen.has(value)) {
        return false;
    }
    seen.add(value);
    const valid = Array.isArray(value)
        ? value.every((v) => isJson(v, seen, depth + 1))
        : Object.getPrototypeOf(value) === Object.prototype &&
          Object.entries(value).every(
              ([key, v]) =>
                  !['__proto__', 'constructor', 'prototype'].includes(key) &&
                  isJson(v, seen, depth + 1)
          );
    seen.delete(value);
    return valid;
};

export interface StartMessage {
    protocol: typeof PROTOCOL;
    type: 'start';
    channel: string;
    arguments: Record<string, unknown>;
}

export const isStartMessage = (value: unknown, channel: string): value is StartMessage =>
    isRecord(value) &&
    Object.keys(value).length === 4 &&
    value.protocol === PROTOCOL &&
    value.type === 'start' &&
    value.channel === channel &&
    isRecord(value.arguments) &&
    isJson(value.arguments, new Set(), 0) &&
    JSON.stringify(value.arguments).length <= MAX_ARGUMENT_SIZE;

export const isInstanceId = (value: unknown): value is string =>
    typeof value === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

export const createFormHost = (cardId: string, nonce: string, origin: string): string => {
    const src = `${origin}/api/v1/processcard/${encodeURIComponent(cardId)}/form`;
    return `<!doctype html><html><head>
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; frame-src ${src}; base-uri 'none'; form-action 'none'; object-src 'none'">
<style>html,body,iframe{border:0;margin:0;width:100%;height:100%;display:block}</style>
</head><body>
<iframe title="Process card content" src="${src}" sandbox="allow-scripts" referrerpolicy="no-referrer"></iframe>
<script nonce="${nonce}">
const frame = document.querySelector('iframe');
const protocol = '${PROTOCOL}';
const hostOrigin = ${JSON.stringify(origin)};
let channel;
let loaded = false;
frame.addEventListener('load', () => {
    if (loaded && channel) parent.postMessage({protocol, channel, type:'invalidated'}, hostOrigin);
    loaded = true;
});
window.addEventListener('message', event => {
    const message = event.data;
    if (!message || message.protocol !== protocol) return;
    if (event.source === parent && ['init','started','error'].includes(message.type)) {
        if (message.type === 'init') channel = message.channel;
        frame.contentWindow.postMessage(message, '*');
    } else if (event.source === frame.contentWindow && message.type === 'start') {
        parent.postMessage(message, hostOrigin);
    }
});
</script></body></html>`;
};
