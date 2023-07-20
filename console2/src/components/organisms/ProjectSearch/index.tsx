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
import { Search } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { useCallback, useEffect, useState } from 'react';
import { SearchProps } from 'semantic-ui-react/dist/commonjs/modules/Search/Search';
import { ProjectEntry, list as apiList, get as apiGet } from '../../../api/org/project';

interface ExternalProps {
    orgName: ConcordKey;
    defaultProjectName?: ConcordKey;
    placeholder?: string;

    fluid?: boolean;
    invalid?: boolean;
    projectsToNotShow?: ProjectEntry[];

    clearOnSelect?: boolean;

    onReset?: (value?: ProjectEntry) => void;
    onClear?: () => void;
    onSelect?: (value: ProjectEntry) => void;
}

interface Result {
    title: string;
    description: string;
}

const renderTitle = (e: ProjectEntry) => `${e.name}`;

const renderDescription = (e: ProjectEntry): string => `${e.description ? e.description : ''}`;

const isEquals = (a?: ProjectEntry, b?: ProjectEntry): boolean => {
    if (a === undefined && b === undefined) {
        return true;
    }
    if (a === undefined || b === undefined) {
        return false;
    }
    return a.id === b.id;
};

export default ({
    orgName,
    defaultProjectName,
    projectsToNotShow,
    clearOnSelect,
    placeholder,
    fluid,
    invalid,
    onClear,
    onReset,
    onSelect
}: ExternalProps) => {
    const [defaultItem, setDefaultItem] = useState<ProjectEntry | undefined>();
    const [value, setValue] = useState<string | undefined>();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [items, setItems] = useState<ProjectEntry[]>([]);
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
                const result = await apiList(orgName, 0, 10, value);
                let projects = result.items;
                if (projectsToNotShow != null) {
                    projects = projects.filter(
                        (project) =>
                            !projectsToNotShow.map((project) => project.id).includes(project.id)
                    );
                }
                setItems(projects);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [orgName, value, projectsToNotShow]);

    // convert ProjectEntries into whatever <Search> accepts
    useEffect(() => {
        const r = items.map((i) => ({
            key: i.id,
            title: renderTitle(i),
            description: renderDescription(i)
        }));

        setResults(r);
    }, [items]);

    // load the default project's data
    useEffect(() => {
        if (!defaultProjectName) {
            setDefaultItem(undefined);
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            try {
                const result = await apiGet(orgName, defaultProjectName);
                setValue(result ? renderTitle(result) : '');
                setDefaultItem(result);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [orgName, defaultProjectName, projectsToNotShow]);

    const onChangeCallBack = useCallback(
        (event: React.MouseEvent<HTMLElement>, data: SearchProps) => {
            setValue(data.value);
        },
        []
    );

    const handleItemSelected = useCallback(
        (item?: ProjectEntry) => {
            setValue(item ? renderTitle(item) : '');

            const isDefault = isEquals(item, defaultItem);
            if (isDefault) {
                onReset?.(item);
            } else if (item) {
                onSelect?.(item);
            } else {
                onClear?.();
            }
        },
        [onReset, onSelect, onClear, defaultItem]
    );

    return (
        <Search
            fluid={fluid}
            input={{
                placeholder,
                error: !!error || invalid
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
            onSearchChange={onChangeCallBack}
            onResultSelect={(ev, data) => {
                const item = items.find((i) => i.id === data.result.key);
                handleItemSelected(item || defaultItem);
                if (clearOnSelect === true) {
                    setValue('');
                }
            }}
        />
    );
};
