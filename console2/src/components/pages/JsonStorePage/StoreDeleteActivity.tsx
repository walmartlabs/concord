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
import { ConcordKey, GenericOperationResult } from '../../../api/common';
import { ButtonWithConfirmation } from '../../molecules';
import { useCallback, useState } from 'react';
import { deleteStorage as apiDelete } from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { Redirect } from 'react-router';

interface ExternalProps {
    orgName: ConcordKey;
    storageName: ConcordKey;
    disabled: boolean;
}

const StoreDeleteActivity = ({ orgName, storageName, disabled }: ExternalProps) => {
    const [forceRequest, toggleForceRequest] = useState<boolean>(false);

    const deleteData = useCallback(async () => {
        return await apiDelete(orgName, storageName);
    }, [orgName, storageName]);

    const { data, error, isLoading } = useApi<GenericOperationResult>(deleteData, {
        fetchOnMount: false,
        forceRequest
    });

    const confirmHandler = useCallback(() => {
        toggleForceRequest((prevState) => !prevState);
    }, []);

    if (data) {
        return <Redirect to={`/org/${orgName}/jsonstore`} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <ButtonWithConfirmation
                primary={true}
                negative={true}
                content="Delete"
                loading={isLoading}
                confirmationHeader="Delete the storage?"
                confirmationContent="Are you sure you want to delete the storage?"
                onConfirm={confirmHandler}
                disabled={disabled}
            />
        </>
    );
};

export default StoreDeleteActivity;
