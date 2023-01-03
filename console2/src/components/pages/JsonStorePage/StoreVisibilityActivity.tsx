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
import { DropdownProps, Form, Icon } from 'semantic-ui-react';

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import {
    StorageVisibility,
    updateVisibility as apiUpdateVisibility
} from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import { RequestErrorActivity } from '../../organisms';

interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    initialVisibility?: StorageVisibility;
    disabled: boolean;
}

const StoreVisibilityActivity = ({
    orgName,
    storeName,
    initialVisibility,
    disabled
}: ExternalProps) => {
    const [value, setValue] = useState<StorageVisibility | undefined>(initialVisibility);

    const postData = useCallback(() => {
        return apiUpdateVisibility(orgName, storeName, value!);
    }, [orgName, storeName, value]);

    const { error, isLoading, data, fetch, clearState } = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        requestByFetch: true
    });

    const visibilityChangeHandler = useCallback(
        (v: StorageVisibility) => {
            setValue(v);
            clearState();
            fetch();
        },
        [clearState, fetch]
    );

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <Form loading={isLoading}>
                <Form.Group>
                    <RestorableDropdown
                        options={[
                            {
                                text: 'Public',
                                icon: 'unlock',
                                value: StorageVisibility.PUBLIC
                            },
                            {
                                text: 'Private',
                                icon: 'lock',
                                value: StorageVisibility.PRIVATE
                            }
                        ]}
                        initialValue={initialVisibility}
                        onChangeValue={visibilityChangeHandler}
                        error={error !== undefined}
                        disabled={disabled}
                    />

                    {data && !isLoading && (
                        <Form.Field>
                            <Icon
                                name={'check'}
                                color={'green'}
                                style={{ verticalAlign: 'middle' }}
                            />
                        </Form.Field>
                    )}
                </Form.Group>
            </Form>
        </>
    );
};

interface RestorableDropdownProps {
    initialValue?: boolean | number | string | (boolean | number | string)[];
    onChangeValue: (data?: any) => void;
    error: boolean;
}

// Dropdown that restore previous value on error change
const RestorableDropdown = ({
    initialValue,
    onChangeValue,
    error,
    ...rest
}: RestorableDropdownProps & DropdownProps) => {
    const [value, setValue] = useState({ prev: initialValue, current: initialValue });

    const onChangeHandler = useCallback(
        (event: React.SyntheticEvent<HTMLElement>, data: DropdownProps) => {
            onChangeValue(data.value);
            setValue((prevState) => ({ prev: prevState.current, current: data.value }));
        },
        [onChangeValue]
    );

    // restore previous dropdown value on error
    useEffect(() => {
        if (error) {
            setValue((prevState) => ({ prev: prevState.prev, current: prevState.prev }));
        }
    }, [error]);

    useEffect(() => {
        setValue({ prev: initialValue, current: initialValue });
    }, [initialValue]);

    return (
        <Form.Dropdown
            selection={true}
            value={value.current}
            onChange={onChangeHandler}
            {...rest}
        />
    );
};

export default StoreVisibilityActivity;
