/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { useEffect, useRef, useState } from 'react';
import { RequestError } from './common';

export const usePolling = (
    request: () => Promise<boolean>,
    interval: number
): [boolean, (RequestError | undefined), () => void] => {
    const poll = useRef<number | undefined>(undefined);
    const currentTimeoutChainId = useRef(0);
    const refreshRef = useRef(() => toggleRefresh((v) => !v));

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [_refresh, toggleRefresh] = useState(false);

    useEffect(() => {
        const fetchData = async (timeoutChainId: number) => {
            setLoading(true);

            let result = false;
            try {
                result = await request();
                setError(undefined);
            } catch (e) {
                setError(e);
            } finally {
                if (result) {
                    if (timeoutChainId === currentTimeoutChainId.current) {
                        poll.current = setTimeout(() => fetchData(timeoutChainId), interval);
                    }
                } else {
                    stopPolling();
                }

                setLoading(false);
            }
        };

        fetchData(currentTimeoutChainId.current);

        return () => stopPolling();
    }, [request, interval, _refresh]);

    const stopPolling = () => {
        if (poll.current) {
            clearTimeout(poll.current);
            poll.current = undefined;
        }
        currentTimeoutChainId.current += 1;
    };

    return [loading, error, refreshRef.current];
};
