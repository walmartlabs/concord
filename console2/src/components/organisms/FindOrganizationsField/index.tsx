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
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Search } from 'semantic-ui-react';

import { list as apiFindOrganizations, OrganizationEntry } from '../../../api/org';
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

const MAX_RESULTS = 10;

const renderTitle = (e: OrganizationEntry) => `${e.name}`;

const renderDescription = (_e: OrganizationEntry): string => '';

export default ({ defaultOrgName, placeholder, required, onClear, onReset, onSelect }: Props) => {
    const [allOrgs, setAllOrgs] = useState<OrganizationEntry[]>([]);
    const [defaultItem, setDefaultItem] = useState<OrganizationEntry | undefined>();
    const [value, setValue] = useState<string | undefined>();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<boolean>();

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true);
            try {
                // onlyCurrent=true: only orgs the user belongs to; large limit to get all of them
                const result = await apiFindOrganizations(true, 0, 500);
                setAllOrgs(result.items);
            } catch (e) {
                setError(true);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, []);

    // Once the org list is loaded, resolve the default org from the cache
    useEffect(() => {
        if (!defaultOrgName) {
            setDefaultItem(undefined);
            return;
        }

        if (allOrgs.length === 0) return;

        const found = allOrgs.find((o) => o.name === defaultOrgName);
        setValue(found ? renderTitle(found) : defaultOrgName);
        setDefaultItem(found);
    }, [defaultOrgName, allOrgs]);

    // Filter the cached list client-side; show up to MAX_RESULTS
    const results: Result[] = useMemo(() => {
        if (!value || value.trim().length < 1) return [];
        const lower = value.trim().toLowerCase();
        return allOrgs
            .filter((o) => o.name.toLowerCase().includes(lower))
            .slice(0, MAX_RESULTS)
            .map((o) => ({
                key: o.id,
                title: renderTitle(o),
                description: renderDescription(o)
            }));
    }, [value, allOrgs]);

    const onChangeCallBack = useCallback(
        (_event: React.MouseEvent<HTMLElement>, data: SearchProps) => {
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
                    const item = allOrgs.find((o) => o.name === data.value);
                    handleItemSelected(item || defaultItem);
                } else {
                    handleItemSelected(undefined);
                }
            }}
            showNoResults={!loading && !!value && value.trim().length > 0}
            onSearchChange={onChangeCallBack}
            onResultSelect={(ev, data) => {
                const item = allOrgs.find((o) => o.id === data.result.key);
                handleItemSelected(item || defaultItem);
            }}
        />
    );
};
