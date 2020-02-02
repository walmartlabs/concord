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

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import {
    createOrUpdateStorageQuery as apiCreate,
    executeQuery as apiExecuteQuery,
    getStorageQuery as apiGetQuery,
    StorageQueryEntry
} from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import { Redirect } from 'react-router';
import { RequestErrorActivity } from '../../organisms';
import EditStoreQueryForm from './EditStoreQueryForm';
import ExecuteQueryResult from './ExecuteQueryResult';
import { LoadingDispatch } from '../../../App';

interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    queryName: ConcordKey;
    forceRefresh: any;
}

const EditStoreQueryActivity = (props: ExternalProps) => {
    const { orgName, storeName, queryName, forceRefresh } = props;

    const dispatch = React.useContext(LoadingDispatch);
    const [query, setQuery] = useState();
    const [queryForExecute, setQueryForExecute] = useState();

    const loadQuery = useCallback(() => {
        return apiGetQuery(orgName, storeName, queryName);
    }, [orgName, storeName, queryName]);

    const postData = useCallback(() => {
        return apiCreate(orgName, storeName, queryName, query);
    }, [orgName, storeName, queryName, query]);

    const execQuery = useCallback(() => {
        return apiExecuteQuery(orgName, storeName, queryForExecute);
    }, [orgName, storeName, queryForExecute]);

    const { fetch: loadQueryFetch, clearState: loadQueryClear, data: loadQueryData } = useApi<
        StorageQueryEntry
    >(loadQuery, { fetchOnMount: true });
    useEffect(() => {
        loadQueryClear();
        loadQueryFetch();
    }, [loadQueryFetch, loadQueryClear, forceRefresh]);

    const { error, isLoading, data, fetch } = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch
    });

    const {
        error: execError,
        isLoading: isExecLoading,
        data: execData,
        fetch: execFetch,
        clearState: execClearState
    } = useApi<Object>(execQuery, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch
    });

    const handleSubmit = useCallback(
        (query: string) => {
            setQuery(query);
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
            <EditStoreQueryForm
                submitting={isLoading}
                executing={isExecLoading}
                onSubmit={handleSubmit}
                onExecute={handleExecute}
                initialQuery={loadQueryData?.text}
            />
        </>
    );
};

export default EditStoreQueryActivity;
