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
import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { isTransientRequestError, RequestErrorData, toRequestError } from './common';

export interface PollingError extends RequestErrorData {
    /** The hook keeps polling after this error. */
    retrying: boolean;
    /** An earlier request succeeded, so the caller has data to keep showing. */
    loaded: boolean;
}

/** Whether the error should replace loaded content rather than wait for the next poll. */
export const isBlockingPollingError = (error?: PollingError): error is PollingError =>
    !!error && !(error.retrying && error.loaded);

/** Receives non-blocking errors of nested polls; `undefined` clears the error of `id`. */
export type PollingErrorReporter = (id: object, error?: PollingError) => void;

/**
 * Lets a page surface errors that nested polls keep hidden behind their loaded content.
 * Without a provider such errors are not shown anywhere.
 */
export const PollingErrorContext = createContext<PollingErrorReporter | undefined>(undefined);

export const usePolling = (
    request: () => Promise<boolean>,
    interval: number,
    loadingHandler: (inc: number) => void,
    refresh: boolean
): PollingError | undefined => {
    const poll = useRef<number | undefined>(undefined);
    const loaded = useRef(false);
    const [error, setError] = useState<PollingError>();
    const reportError = useContext(PollingErrorContext);
    const [reporterId] = useState(() => ({}));

    useEffect(() => {
        let cancelled = false;

        const fetchData = async () => {
            loadingHandler(1);

            let result = false;
            try {
                result = await request();

                if (!cancelled) {
                    loaded.current = true;
                    setError(undefined);
                }
            } catch (e) {
                if (!cancelled) {
                    // Retry transient failures using the configured polling interval.
                    result = isTransientRequestError(e);
                    setError({ ...toRequestError(e), retrying: result, loaded: loaded.current });
                }
            } finally {
                if (!cancelled) {
                    if (result) {
                        poll.current = window.setTimeout(() => fetchData(), interval);
                    } else {
                        stopPolling();
                    }
                }

                loadingHandler(-1);
            }
        };

        fetchData();

        return () => {
            cancelled = true;
            stopPolling();
        };
    }, [request, interval, refresh, loadingHandler]);

    useEffect(() => {
        // Blocking errors are rendered by the caller itself.
        if (!reportError || !error || isBlockingPollingError(error)) {
            return;
        }
        reportError(reporterId, error);
        return () => reportError(reporterId, undefined);
    }, [reportError, reporterId, error]);

    const stopPolling = () => {
        if (poll.current) {
            clearTimeout(poll.current);
            poll.current = undefined;
        }
    };

    return error;
};
