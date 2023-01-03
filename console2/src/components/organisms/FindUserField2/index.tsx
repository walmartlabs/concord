/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import * as React from 'react';
import { useEffect, useState } from 'react';
import { Search } from 'semantic-ui-react';

import { get as apiGet, list as apiList, UserEntry } from '../../../api/user';
import { ConcordId, RequestError } from '../../../api/common';

interface Props {
    defaultUserId?: ConcordId;
    placeholder?: string;
    onSelect?: (value: UserEntry) => void;
}

interface Result {
    title: string;
    description: string;
}

const renderDescription = (e: UserEntry): string => (e.email ? `${e.name} - ${e.email}` : e.name);

const renderTitle = (e: UserEntry) => (e.displayName ? e.displayName : e.name);

export default ({ defaultUserId, placeholder, onSelect }: Props) => {
    const [value, setValue] = useState<string | undefined>();
    const [loading, setLoading] = useState(false);
    const [items, setItems] = useState<UserEntry[]>([]);
    const [results, setResults] = useState<Result[]>([]);
    const [error, setError] = useState<RequestError>();

    // perform search whenever the filter changes
    useEffect(() => {
        if (!value || value.trim().length < 3) {
            setResults([]);
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            try {
                const result = await apiList(0, 10, value);
                setItems(result.items);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [value]);

    // convert UserEntries into whatever <Search> accepts
    useEffect(() => {
        const r = items.map((i) => ({
            key: i.id,
            title: renderTitle(i),
            description: renderDescription(i)
        }));

        setResults(r);
    }, [items]);

    // load the default user's data
    useEffect(() => {
        if (!defaultUserId) {
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            try {
                const result = await apiGet(defaultUserId);
                setValue(result ? renderTitle(result) : undefined);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [defaultUserId]);

    return (
        <Search
            fluid={true}
            input={{
                placeholder,
                error: !!error
            }}
            value={value}
            loading={loading}
            results={results}
            onSearchChange={(ev, data) => setValue(data.value)}
            onResultSelect={(ev, data) => {
                setValue(data.result.title);

                if (onSelect) {
                    const item = items.find((i) => i.id === data.result.key);
                    if (item) {
                        onSelect(item);
                    }
                }
            }}
        />
    );
};
