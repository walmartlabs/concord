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
import { useCallback, useEffect, useState } from 'react';
import { Search } from 'semantic-ui-react';

import { list as apiFindOrganizations, get as apiGet, OrganizationEntry } from '../../../api/org';
import { SearchProps } from 'semantic-ui-react/dist/commonjs/modules/Search/Search';

interface Props {
    defaultOrgName?: string;
    placeholder?: string;
    required?: boolean;

    onReset?: (value?: OrganizationEntry) => void;
    onClear?: () => void;
    onSelect?: (value: OrganizationEntry) => void;
}

interface Result {
    title: string;
    description: string;
}

const renderTitle = (e: OrganizationEntry) => `${e.name}`;

const renderDescription = (e: OrganizationEntry): string => '';

export default ({ defaultOrgName, placeholder, required, onClear, onReset, onSelect }: Props) => {
    const [defaultItem, setDefaultItem] = useState<OrganizationEntry | undefined>();
    const [value, setValue] = useState<string | undefined>();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<boolean>();
    const [items, setItems] = useState<OrganizationEntry[]>([]);
    const [results, setResults] = useState<Result[]>([]);

    // perform search whenever the filter changes
    useEffect(() => {
        if (!value || value.trim().length < 3) {
            setResults([]);
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            try {
                const result = await apiFindOrganizations(true, 0, 10, value);
                setItems(result.items);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [value]);

    // convert OrganizationEntries into whatever <Search> accepts
    useEffect(() => {
        const r = items.map((i) => ({
            key: i.id,
            title: renderTitle(i),
            description: renderDescription(i)
        }));

        setResults(r);
    }, [items]);

    // load the default organization's data
    useEffect(() => {
        if (!defaultOrgName) {
            setDefaultItem(undefined);
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            try {
                const result = await apiGet(defaultOrgName);
                setValue(result ? renderTitle(result) : '');
                setDefaultItem(result);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [defaultOrgName]);

    const onChangeCallBack = useCallback(
        (event: React.MouseEvent<HTMLElement>, data: SearchProps) => {
            setValue(data.value);
        },
        []
    );

    const handleItemSelected = useCallback(
        (item?: OrganizationEntry) => {
            setValue(item ? renderTitle(item) : '');

            const isDefault = item?.id === defaultItem?.id;
            if (isDefault) {
                onReset?.(item);
            } else if (item) {
                onSelect?.(item);
            } else {
                if (required) {
                    setValue(defaultItem ? renderTitle(defaultItem) : '');
                    onReset?.(defaultItem);
                } else {
                    onClear?.();
                }
            }
        },
        [required, onReset, onSelect, onClear, defaultItem]
    );

    return (
        <Search
            fluid={true}
            input={{
                fluid: true,
                placeholder,
                error
            }}
            value={value}
            loading={loading}
            results={results}
            onBlur={(event, data) => {
                if (data.value !== '') {
                    const item = items.find((i) => i.name === data.value);
                    handleItemSelected(item || defaultItem);
                } else {
                    handleItemSelected(undefined);
                }
            }}
            showNoResults={!loading}
            onSearchChange={onChangeCallBack}
            onResultSelect={(ev, data) => {
                const item = items.find((i) => i.id === data.result.key);
                handleItemSelected(item || defaultItem);
            }}
        />
    );
};
