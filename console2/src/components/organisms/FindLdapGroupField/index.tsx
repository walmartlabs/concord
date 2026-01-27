/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { useCallback, useEffect, useState } from 'react';
import { Search, SearchResultData, SearchResultProps } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import {
    findLdapGroups as apiFindLdapGroups,
    LdapGroupSearchResult
} from '../../../api/service/console';
import { useThrottle } from '../../../hooks/useThrottle';

interface Props {
    onSelect: (value: LdapGroupSearchResult) => void;
    onChange?: (value?: string) => void;
    placeholder?: string;
}

// TODO remove when the Search component will support custom result types
const toResults = (items: LdapGroupSearchResult[]) =>
    items.map((i) => ({
        title: i.displayName,
        description: i.groupName
    }));

// TODO remove when the Search component will support custom result types
const resultToItem = (result: SearchResultProps, items: LdapGroupSearchResult[]) =>
    items.find((i) => i.groupName === result.description)!;

const FindLdapGroupField = ({ onSelect, onChange, placeholder }: Props) => {
    const [filter, setFilter] = useState<string>('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [items, setItems] = useState<LdapGroupSearchResult[]>([]);

    const performSearch = useCallback(async (searchFilter: string) => {
        if (searchFilter.length < 5) {
            setItems([]);
            return;
        }

        try {
            setLoading(true);
            setError(undefined);
            const result = await apiFindLdapGroups(searchFilter);
            setItems(result || []);
        } catch (e) {
            setError(e);
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, []);

    const throttledSearch = useThrottle(performSearch, 2000);

    useEffect(() => {
        throttledSearch(filter);
    }, [filter, throttledSearch]);

    const handleSelect = useCallback(
        ({ result }: SearchResultData) => {
            if (!result) {
                return;
            }

            const item = resultToItem(result, items);
            if (!item) {
                return;
            }

            onSelect(item);
        },
        [items, onSelect]
    );

    return (
        <Search
            input={{
                fluid: true,
                placeholder: placeholder ? placeholder : 'Search for a user...',
                error: !!error
            }}
            fluid={true}
            loading={loading}
            showNoResults={!loading}
            onSearchChange={(ev, data) => {
                const newFilter = data.value || '';
                setFilter(newFilter);
                if (onChange) {
                    onChange(newFilter);
                }
            }}
            onResultSelect={(ev, data) => handleSelect(data)}
            results={toResults(items)}
        />
    );
};

export default FindLdapGroupField;
