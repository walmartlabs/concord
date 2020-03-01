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
import { Search, SearchResultData } from 'semantic-ui-react';

import { list as apiFindOrganizations, OrganizationEntry } from '../../../api/org';

interface Props {
    defaultValue: string;
    onSelect: (value: OrganizationEntry) => void;
    onChange?: (value?: string) => void;
    placeholder?: string;
}

export default (props: Props) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<boolean>();
    const [data, setData] = useState<OrganizationEntry[]>([]);
    const [filter, setFilter] = useState(props.defaultValue);

    const toResults = () =>
        data.map((o) => ({
            title: o.name,
            description: o.name,
            key: o.id
        }));

    const fetchData = useCallback(async () => {
        if (filter && filter.length >= 2) {
            try {
                setLoading(true);

                const offset = 0;
                const active = true;
                const limit = -1;

                const organizationList = await apiFindOrganizations(active, offset, limit, filter);

                setData(organizationList.items);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        }
    }, [filter]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const handleSelect = ({ result }: SearchResultData) => {
        if (!result) {
            return;
        }

        const i = data.find((o) => o.name === result.description)!;

        if (!i) {
            return;
        }

        setFilter(i.name);

        props.onSelect(i);
    };

    return (
        <Search
            value={filter}
            input={{
                fluid: true,
                placeholder: props.placeholder
                    ? props.placeholder
                    : 'Search for an Organization...',
                error
            }}
            fluid={true}
            loading={loading}
            showNoResults={!loading}
            onSearchChange={(ev, data) => {
                const filter = data.value;
                setFilter(filter || '');

                if (props.onChange) {
                    props.onChange(data.value);
                }
            }}
            onResultSelect={(ev, data) => handleSelect(data)}
            results={toResults()}
        />
    );
};
