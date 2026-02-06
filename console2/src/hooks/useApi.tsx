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
import { useCallback, useEffect, useRef, useState } from 'react';
import { RequestError } from '../api/common';
import { LoadingAction } from '../reducers/loading';

export interface Props<S> {
    fetchOnMount?: boolean;
    initialData?: S;
    forceRequest?: any;
    debounceTime?: number | null;
    dispatch?: (value: LoadingAction) => void;
    requestByFetch?: boolean;
}

export function useApi<S>(dataFetcher: () => Promise<S>, props: Props<S>) {
    const { fetchOnMount, initialData, forceRequest, dispatch, requestByFetch, debounceTime } = props;

    const didMountRef = useRef(false);
    const didMountRef2 = useRef(false);
    const fetchNowRef = useRef(false);
    const debounceRef = useRef(null);
    const [fetchNow, toggleFetchNow] = useState<boolean>(false);
    const [data, setData] = useState(initialData);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<RequestError>();

    const clearState = useCallback((initialData?: S) => {
        setData(initialData);
        setIsLoading(false);
        setError(undefined);
    }, []);

    const fetch = useCallback(() => {
        toggleFetchNow((prevState) => !prevState);
    }, []);

    useEffect(() => {
        if (!didMountRef2.current) {
            didMountRef2.current = true;
            return;
        }

        fetchNowRef.current = true;
    }, [fetchNow]);

    useEffect(() => {
        let cancelled = false;
        let loading = false;

        const fetchData = async () => {
            setIsLoading(true);
            setError(undefined);

            if (dispatch) {
                loading = true;
                dispatch(LoadingAction.START);
            }
            try {
                const result = await dataFetcher();
                if (!cancelled) {
                    setData(result);
                }
            } catch (e) {
                if (!cancelled) {
                    setError(e);
                }
            } finally {
                loading = false;
                if (!cancelled) {
                    setIsLoading(false);
                    if (dispatch) {
                        dispatch(LoadingAction.STOP);
                    }
                }
            }
        };

        const startBouncing = () => {
            if(typeof debounceTime === "number" && debounceTime > 0) {
                if(debounceRef.current !== null) {
                    clearTimeout(debounceRef.current);
                }
                debounceRef.current = setTimeout(async ()=>{
                    debounceRef.current = null;
                    await fetchData();
                }, debounceTime);
            } else {
                fetchData();
            }
        }

        if (!didMountRef.current && !fetchOnMount) {
            didMountRef.current = true;
            return;
        }

        if (!requestByFetch) {
            fetchNowRef.current = false;
            startBouncing();
        } else if (requestByFetch && fetchNowRef.current) {
            fetchNowRef.current = false;
            startBouncing();
        }

        return () => {
            cancelled = true;
            if(debounceRef.current !== null) {
                clearTimeout(debounceRef.current);
                debounceRef.current = null;
            }
            if (dispatch && loading) {
                dispatch(LoadingAction.STOP);
            }
        };
    }, [dataFetcher, fetchOnMount, forceRequest, requestByFetch, dispatch, fetchNow, debounceTime]);

    return { data, isLoading, error, fetch, clearState };
}
