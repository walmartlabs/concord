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
import { get as apiGet, list as apiList, SecretEntry } from '../../../api/org/secret';
import { useCallback, useEffect, useState } from 'react';
import { SearchProps } from 'semantic-ui-react/dist/commonjs/modules/Search/Search';

interface ExternalProps {
    fluid?: boolean;

    invalid?: boolean;
    orgName: ConcordKey;
    defaultSecretName?: ConcordKey;
    placeholder?: string;
    onBlur?: (value?: SecretEntry) => void;
    onChange?: (value?: string) => void;
    onSelect?: (value: SecretEntry) => void;
}

interface Result {
    title: string;
    description: string;
}

const renderTitle = (e: SecretEntry) => `${e.name}`;
const renderDescription = (e: SecretEntry): string => `${e.type} - ${e.visibility}`;

export default ({
    orgName,
    defaultSecretName,
    placeholder,
    fluid,
    invalid,
    onBlur,
    onChange,
    onSelect
}: ExternalProps) => {
    const [value, setValue] = useState<string | undefined>();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [items, setItems] = useState<SecretEntry[]>([]);
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
                setItems(result.items);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [orgName, value]);

    // convert SecretEntries into whatever <Search> accepts
    useEffect(() => {
        const r = items.map((i) => ({
            key: i.id,
            title: renderTitle(i),
            description: renderDescription(i)
        }));

        setResults(r);
    }, [items]);

    // load the default secret's data
    useEffect(() => {
        if (!defaultSecretName) {
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            try {
                const result = await apiGet(orgName, defaultSecretName);
                setValue(result ? renderTitle(result) : undefined);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [orgName, defaultSecretName]);

    const onChangeCallBack = useCallback(
        (event: React.MouseEvent<HTMLElement>, data: SearchProps) => {
            setValue(data.value);
            if (onChange) {
                onChange(data.value);
            }
        },
        [onChange]
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
                if (onBlur) {
                    const item = items.find((i) => i.name === data.value);
                    onBlur(item);
                }
            }}
            onSearchChange={onChangeCallBack}
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
