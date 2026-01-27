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
import {useCallback, useState} from 'react';
import {Loader} from 'semantic-ui-react';
import {ConcordKey, GenericOperationResult} from '../../../api/common';
import {ResourceAccessEntry} from '../../../api/org';
import {getProjectAccess, updateProjectAccess} from '../../../api/org/project';
import {useApi} from '../../../hooks/useApi';
import {LoadingDispatch} from '../../../App';
import {RequestErrorMessage, TeamAccessList} from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

const ProjectTeamAccessActivity = ({orgName, projectName}: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const fetchData = useCallback(() => {
        return getProjectAccess(orgName, projectName);
    }, [orgName, projectName]);

    const {data, error} = useApi<ResourceAccessEntry[]>(fetchData, {
        fetchOnMount: true,
        dispatch: dispatch,
        forceRequest: refresh
    });

    const [updateValue, setUpdateValue] = useState({
        access: [] as ResourceAccessEntry[]
    });

    const postData = useCallback(async () => {
        const result = await updateProjectAccess(orgName, projectName, updateValue.access);
        toggleRefresh((prevState) => !prevState);
        return result;
    }, [orgName, projectName, updateValue]);

    const update = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        dispatch: dispatch,
        requestByFetch: true
    });

    const handleUpdate = useCallback(
        (entries: ResourceAccessEntry[]) => {
            setUpdateValue({access: entries});
            update.fetch();
        },
        [update]
    );

    if (error) {
        return <RequestErrorMessage error={error}/>;
    }

    if (update.error) {
        return <RequestErrorMessage error={update.error}/>;
    }

    if (!data || update.isLoading) {
        return <Loader active={true}/>;
    }

    const entries = data || [];

    return (
        <div>
            <TeamAccessList
                data={entries}
                submitting={update.isLoading}
                orgName={orgName}
                submit={handleUpdate}
            />
        </div>
    );
};

export default ProjectTeamAccessActivity;
