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
import { Dropdown, DropdownItemProps } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { comparators } from '../../../utils';
import { list as apiList, TeamEntry } from '../../../api/org/team';

interface Props {
    onSelect: (item: TeamEntry) => void;
    orgName: ConcordKey;
    name: string;
}

const makeOptions = (data: TeamEntry[]): DropdownItemProps[] => {
    if (!data || data.length === 0) {
        return [];
    }

    return data.sort(comparators.byName).map(({ name, id }) => ({
        value: id,
        text: name
    }));
};

export default ({ orgName, name, onSelect, ...rest }: Props) => {
    const [items, setItems] = useState<TeamEntry[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError | undefined>();

    const handleChange = useCallback(
        (id: ConcordKey) => {
            const item = items.find((i) => i.id === id);
            if (!item) {
                return;
            }

            onSelect(item);
        },
        [items, onSelect]
    );

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            setError(undefined);
            try {
                setItems(await apiList(orgName));
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        load();
    }, [orgName]);

    return (
        <Dropdown
            placeholder="Select team"
            loading={loading}
            error={!!error}
            selection={true}
            search={true}
            options={makeOptions(items)}
            {...rest}
            onChange={(ev, { value }) => handleChange(value as ConcordKey)}
        />
    );
};
