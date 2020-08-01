/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import { useCallback } from 'react';
import { get as apiGet, ProjectEntry } from '../../../api/org/project';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

const ProjectProcesses = ({ orgName, projectName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiGet(orgName, projectName);
    }, [orgName, projectName]);

    const { data, error } = useApi<ProjectEntry>(fetchData, {
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

    let columns;
    if (data.meta && data.meta.ui && data.meta.ui.processList) {
        columns = data.meta.ui.processList;
    }
    return (
        <ProcessListActivity
            orgName={orgName}
            projectName={projectName}
            columns={columns}
            usePagination={true}
        />
    );
};

export default ProjectProcesses;
