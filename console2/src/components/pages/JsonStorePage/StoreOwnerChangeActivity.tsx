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
import { ConcordId, ConcordKey, GenericOperationResult } from '../../../api/common';
import EntityOwnerChangeForm from '../../molecules/EntityOwnerChangeForm';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { useApi } from '../../../hooks/useApi';

interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    initialOwnerId?: ConcordId;
    disabled: boolean;
}

const StoreOwnerChangeActivity = ({
    orgName,
    storeName,
    disabled,
    initialOwnerId
}: ExternalProps) => {
    const [value, setValue] = useState(initialOwnerId);

    const postData = useCallback(() => {
        return apiChangeOwner(orgName, storeName, value!);
    }, [orgName, storeName, value]);

    const { error, isLoading, fetch, clearState } = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        requestByFetch: true
    });

    const ownerChangeHandler = useCallback(
        (value: ConcordId) => {
            setValue(value);
            clearState();
            fetch();
        },
        [clearState, fetch]
    );

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <EntityOwnerChangeForm
                originalOwnerId={initialOwnerId}
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
