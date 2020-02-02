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
import { useCallback, useState } from 'react';

import { changeOwner as apiChangeOwner } from '../../../api/org/jsonstore';
import { ConcordKey, GenericOperationResult, Owner } from '../../../api/common';
import EntityOwnerChangeForm from '../../molecules/EntityOwnerChangeForm';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { useApi } from '../../../hooks/useApi';

interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    initialOwner?: Owner;
    disabled: boolean;
}

const StoreOwnerChangeActivity = ({
    orgName,
    storeName,
    disabled,
    initialOwner
}: ExternalProps) => {
    // using object for making same request after submit error
    const [value, setValue] = useState({ owner: initialOwner });

    const postData = useCallback(() => {
        return apiChangeOwner(orgName, storeName, value.owner!);
    }, [orgName, storeName, value]);

    const { error, isLoading, fetch, clearState } = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        requestByFetch: true
    });

    const ownerChangeHandler = useCallback(
        (value: Owner) => {
            setValue({
                owner: {
                    username: value.username,
                    userDomain: value.userDomain
                }
            });
            clearState();
            fetch();
        },
        [clearState, fetch]
    );

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <EntityOwnerChangeForm
                originalOwner={initialOwner || { username: '' }}
                confirmationHeader="Change storage owner?"
                confirmationContent="Are you sure you want to change the storage's owner?"
                onSubmit={ownerChangeHandler}
                submitting={isLoading}
                disabled={disabled}
            />
        </>
    );
};

export default StoreOwnerChangeActivity;
