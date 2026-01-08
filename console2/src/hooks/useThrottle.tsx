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

import { useCallback, useRef, useEffect } from 'react';
import { throttle } from 'lodash';

/**
 * Creates a throttled version of the provided callback function.
 *
 * @param callback - The function to throttle
 * @param delay - The number of milliseconds to throttle invocations to
 * @returns A throttled version of the callback that will only execute at most once per delay period
 */
export function useThrottle<T extends (...args: any[]) => any>(
    callback: T,
    delay: number
): T {
    const throttledFnRef = useRef<ReturnType<typeof throttle>>();

    useEffect(() => {
        throttledFnRef.current = throttle(callback, delay, {
            leading: true,
            trailing: true
        });

        return () => {
            throttledFnRef.current?.cancel();
        };
    }, [callback, delay]);

    return useCallback(
        ((...args) => throttledFnRef.current?.(...args)) as T,
        []
    );
}
