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
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { getProcessCardData, startProcessCard } from '../../../api/service/console/user';
import { createFormHost, isInstanceId, isRecord, isStartMessage, PROTOCOL } from './bridge';

export default function ProcessCardForm({ cardId }: { cardId: string }) {
    const frame = useRef<HTMLIFrameElement>(null);
    const [error, setError] = useState(false);
    const loaded = useRef(false);
    const initialize = useRef<() => void>(() => undefined);
    // The trusted shell constrains navigation of the untrusted inner frame as well as its loads.
    const host = useMemo(
        () => createFormHost(cardId, crypto.randomUUID(), window.location.origin),
        [cardId]
    );

    useEffect(() => {
        let active = true;
        let channel: string | undefined;
        let pending = false;
        loaded.current = false;
        const reply = (message: Record<string, unknown>) => {
            if (active && channel) {
                // The trusted relay validates its opaque child; bind to the relay window and nonce.
                frame.current?.contentWindow?.postMessage(
                    { protocol: PROTOCOL, channel, ...message },
                    window.location.origin
                );
            }
        };
        initialize.current = () => {
            if (loaded.current) {
                channel = undefined;
                setError(true);
                return;
            }
            loaded.current = true;
            channel = crypto.randomUUID();
            getProcessCardData(cardId).then(
                (data) => reply({ type: 'init', data }),
                () => {
                    if (active) setError(true);
                }
            );
        };
        const receive = (event: MessageEvent<unknown>) => {
            if (
                active &&
                channel &&
                event.source === frame.current?.contentWindow &&
                isRecord(event.data) &&
                Object.keys(event.data).length === 3 &&
                event.data.protocol === PROTOCOL &&
                event.data.channel === channel &&
                event.data.type === 'invalidated'
            ) {
                channel = undefined;
                setError(true);
                return;
            }
            if (
                !active ||
                !channel ||
                event.source !== frame.current?.contentWindow ||
                !isStartMessage(event.data, channel) ||
                pending
            ) {
                return;
            }
            pending = true;
            startProcessCard(cardId, event.data.arguments).then(
                (result) => {
                    if (!active || !channel) return;
                    if (!isInstanceId(result.instanceId)) {
                        reply({ type: 'error', message: 'Unable to start process.' });
                        pending = false;
                        return;
                    }
                    reply({ type: 'started', instanceId: result.instanceId });
                    window.location.assign(`/#/process/${result.instanceId}/log`);
                },
                () => {
                    if (!active) return;
                    pending = false;
                    reply({
                        type: 'error',
                        message: 'Unable to start process. Check access and arguments.',
                    });
                }
            );
        };
        window.addEventListener('message', receive);
        return () => {
            active = false;
            channel = undefined;
            initialize.current = () => undefined;
            window.removeEventListener('message', receive);
        };
    }, [cardId]);

    return (
        <>
            {error && (
                <div role="alert">Unable to initialize the process card. Reload to try again.</div>
            )}
            <iframe
                ref={frame}
                key={cardId}
                title={cardId}
                srcDoc={host}
                referrerPolicy="no-referrer"
                height="100%"
                width="100%"
                frameBorder={0}
                onLoad={() => initialize.current()}
            />
        </>
    );
}
