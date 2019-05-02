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
import { useState, useEffect, useLayoutEffect } from 'react';

import { parseQueryParams, QueryParams } from '../../../../api/common';

/**
 * Custom React Hook to provide the current query parameters
 * When this hook is used it creates a HachChangeEvent listener
 *
 * When the event fires query params in state are updated.
 */
export function useQueryParams() {
    const [queryParams, setQueryParams] = useState<QueryParams>();

    const [currentUrl, setCurrentUrl] = useState<string>('');
    const [oldUrl, setOldUrl] = useState<string>('');

    /**
     * Get url parameters directly
     * Useful if you need to load parameters on initial render
     *
     * @returns QueryParameterObject
     */
    const getCurrentParams = (): QueryParams => {
        // Parse, decode, return
        return decodeAllUriValues(parseQueryParams(window.location.href));
    };

    /**
     * Remove empty values from the queryParamObject
     * @param params a query object to check
     */
    const removeEmptyValues = (params: QueryParams): QueryParams => {
        const newValues = Object.entries(params).reduce((previous, current) => {
            // is the value empty?
            if (current[1] === '') {
                // value is empty don't add to the result
                return { ...previous };
            } else {
                // there is a value so add it to the result
                return { ...previous, [current[0]]: current[1] };
            }
        }, {});

        return newValues;
    };

    /**
     * decode all uri parameters
     * @param params key/value pairs to iterate through
     */
    const decodeAllUriValues = (params: QueryParams): QueryParams => {
        let dec = decodeURIComponent;

        let decodedResult = {};
        Object.keys(params).forEach((key) => {
            decodedResult[key] = dec(params[key]);
        });

        return decodedResult;
    };

    /**
     * Replaces all query params in the url with the object provided
     * Store that value in queryParams state
     *
     * @param params a QueryParams e.g. { key: value, ... }
     */
    const replaceQueryParams = (params: QueryParams = {}) => {
        // Construct URLSearchParams instance
        let UrlParams = new URLSearchParams();
        Object.keys(removeEmptyValues(params)).forEach((i) => UrlParams.append(i, params[i]));

        // The full url that shows in the browser currently
        let baseUrl = window.location.href;

        // Store oldUrl in state
        setOldUrl(baseUrl);

        // Edgecase if the url happens to have a ? in it
        if (baseUrl.includes('?')) {
            baseUrl = baseUrl.split('?')[0];
        }

        // Generate the new complete href
        let newUrl = baseUrl;
        // Only add query params if there are query params
        if (UrlParams.toString().length > 0) newUrl += `?${UrlParams.toString()}`;

        // Set the query params in the URL
        window.location.assign(newUrl);

        // Save new url to current
        setCurrentUrl(newUrl);

        // Store params in state
        setQueryParams(parseQueryParams(UrlParams.toString()));
    };

    /**
     * Sets the queryParams state values if the URL hash changes
     * Saves the old and new url to state
     * @param event Window event object containing new and old url addresses
     */
    const onHashChange = (event: HashChangeEvent) => {
        if (event.newURL === event.oldURL) {
            // No change to URL.  do_nothing();
            return;
        }

        // Store so we can expose these
        setOldUrl(event.oldURL);
        setCurrentUrl(event.newURL);

        // Parse, decode, set
        setQueryParams(decodeAllUriValues(parseQueryParams(event.newURL)));
    };

    /**
     * Sets up a hashchange event listener for any changes to the url
     * This Effect only runs on initial mount
     *
     * On unmount the hashchange event listener is removed via a useEffect
     * cleanup function
     */
    useEffect(() => {
        const eventName = 'hashchange';

        window.addEventListener(eventName, onHashChange, false);

        return () => {
            window.removeEventListener(eventName, onHashChange, false);
        };
    }, []);

    /**
     * Set queryParams on first render
     */
    useLayoutEffect(() => {
        setQueryParams(getCurrentParams());
    }, []);

    return {
        queryParams,
        replaceQueryParams,
        decodeAllUriValues,
        getCurrentParams,
        currentUrl,
        oldUrl
    };
}

export default useQueryParams;
