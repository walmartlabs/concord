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
import { ResourceAccessEntry } from '../../../api/org';
import { TeamAccessList } from '../../molecules';
import {
    getAccess as apiGetAccess,
    updateAccess as apiUpdateAccess
} from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import { RequestErrorActivity } from '../../organisms';
import { LoadingDispatch } from '../../../App';

interface ExternalProps {
    orgName: ConcordKey;
    storageName: ConcordKey;
    forceRefresh: boolean;
}

const StoreTeamAccessActivity = ({ orgName, storageName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);
    const [refresh, toggleRefresh] = useState<boolean>(forceRefresh);

    useEffect(() => {
        toggleRefresh((prevState) => !prevState);
    }, [forceRefresh]);

    const fetchData = useCallback(() => {
        return apiGetAccess(orgName, storageName);
    }, [orgName, storageName]);

    const { data, error } = useApi<ResourceAccessEntry[]>(fetchData, {
        fetchOnMount: false,
        dispatch: dispatch,
        forceRequest: refresh
    });

    // using object for making same request after submit error
    const [value, setValue] = useState({ access: [] as ResourceAccessEntry[] });

    const postData = useCallback(async () => {
        const result = await apiUpdateAccess(orgName, storageName, value.access);
        toggleRefresh((prevState) => !prevState);
        return result;
    }, [orgName, storageName, value]);

    const post = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        dispatch: dispatch
    });

    const accessChangeHandler = useCallback((entries: ResourceAccessEntry[]) => {
        setValue({ access: entries });
    }, []);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }
    if (post.error) {
        return <RequestErrorActivity error={post.error} />;
    }

    if (!data) {
        // TODO: add some loader/loading indicator
        return <></>;
    }

    return (
        <div>
            <TeamAccessList
                data={data}
                submitting={post.isLoading}
                orgName={orgName}
                submit={accessChangeHandler}
            />
        </div>
    );
};

export default StoreTeamAccessActivity;
