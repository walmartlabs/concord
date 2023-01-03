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
import { useCallback } from 'react';

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import { deleteStorageQuery as apiDelete } from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';

interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    storageQueryName: ConcordKey;
    trigger: (onClick: (event: React.SyntheticEvent) => void) => React.ReactNode;
    onDone: () => void;
}

const StoreQueryDeleteActivity = (props: ExternalProps) => {
    const { orgName, storeName, storageQueryName, trigger, onDone } = props;

    const deleteDataRequest = useCallback(() => {
        return apiDelete(orgName, storeName, storageQueryName);
    }, [orgName, storeName, storageQueryName]);

    const { data, isLoading, error, clearState, fetch } = useApi<GenericOperationResult>(
        deleteDataRequest,
        { fetchOnMount: false, requestByFetch: true }
    );

    const resetHandler = useCallback(() => {
        clearState();
    }, [clearState]);

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Delete storage query?"
            introMsg={
                <p>
                    Are you sure you want to delete the storage query '<b>{storageQueryName}</b>'?
                </p>
            }
            running={isLoading}
            success={data !== undefined}
            successMsg={<p>Storage query was deleted successfully.</p>}
            error={error}
            reset={resetHandler}
            onConfirm={fetch}
            onDone={onDone}
        />
    );
};

export default StoreQueryDeleteActivity;
