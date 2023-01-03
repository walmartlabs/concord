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

import { ConcordKey } from '../../../api/common';
import { ProcessListActivity, RequestErrorActivity } from '../index';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import { get as apiGet, OrganizationEntry } from '../../../api/org';
import { useCallback } from 'react';

interface ExternalProps {
    orgName: ConcordKey;
    forceRefresh: any;
}

const OrganizationProcesses = ({ orgName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiGet(orgName);
    }, [orgName]);

    const { data, error } = useApi<OrganizationEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    if (!data) {
        return <></>;
    }

    if (
        data.meta !== undefined &&
        data.meta.ui !== undefined &&
        data.meta.ui.processList !== undefined
    ) {
        return (
            <ProcessListActivity
                orgName={orgName}
                columns={data.meta.ui.processList}
                usePagination={true}
            />
        );
    } else {
        return <ProcessListActivity orgName={orgName} usePagination={true} />;
    }
};

export default OrganizationProcesses;
