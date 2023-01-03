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
import { useCallback, useState } from 'react';

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import {
    createOrUpdateStorageQuery as apiCreate,
    executeQuery as apiExecuteQuery
} from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import { Redirect } from 'react-router';
import { RequestErrorActivity } from '../../organisms';
import NewStorageQueryForm, { NewStorageQueryFormValues } from './NewStorageQueryForm';
import ExecuteQueryResult from './ExecuteQueryResult';
import { LoadingDispatch } from '../../../App';

interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
}

const INIT_VALUES = {
    name: '',
    query: 'select cast(item_data as varchar) \nfrom json_store_data'
};

const NewStorageQueryActivity = (props: ExternalProps) => {
    const { orgName, storeName } = props;

    const dispatch = React.useContext(LoadingDispatch);
    const [queryForExecute, setQueryForExecute] = useState('');
    const [values, setValues] = useState(INIT_VALUES);

    const postQuery = useCallback(() => {
        return apiCreate(orgName, storeName, values.name, values.query);
    }, [orgName, storeName, values]);

    const { error, isLoading, data, fetch } = useApi<GenericOperationResult>(postQuery, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch: dispatch
    });

    const execQuery = useCallback(() => {
        return apiExecuteQuery(orgName, storeName, queryForExecute);
    }, [orgName, storeName, queryForExecute]);

    const {
        error: execError,
        isLoading: isExecLoading,
        data: execData,
        fetch: execFetch,
        clearState: execClearState
    } = useApi<Object>(execQuery, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch: dispatch
    });

    const handleSubmit = useCallback(
        (values: NewStorageQueryFormValues) => {
            setValues({ ...values });
            fetch();
        },
        [fetch]
    );

    const handleExecute = useCallback(
        (query: string) => {
            setQueryForExecute(query);
            execFetch();
        },
        [execFetch]
    );

    if (data) {
        return <Redirect to={`/org/${orgName}/jsonstore/${storeName}/query`} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            {execError && <RequestErrorActivity error={execError} />}
            {execData && <ExecuteQueryResult data={execData} onClose={execClearState} />}
            <NewStorageQueryForm
                orgName={orgName}
                storeName={storeName}
                submitting={isLoading}
                executing={isExecLoading}
                onSubmit={handleSubmit}
                onExecute={handleExecute}
                initial={INIT_VALUES}
            />
        </>
    );
};

export default NewStorageQueryActivity;
