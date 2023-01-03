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

import { ConcordKey, RequestError } from '../../../api/common';
import {
    getProjectConfiguration as apiGetProjectConfiguration,
    updateProjectConfiguration as apiUpdateProjectConfiguration
} from '../../../api/org/project';
import ProjectConfiguration from '../../molecules/ProjectConfiguration';
import { RequestErrorActivity } from '../index';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

export default ({ orgName, projectName, forceRefresh }: Props) => {
    const dispatch = React.useContext(LoadingDispatch);

    const [updating, setUpdating] = useState(false);
    const [updateError, setUpdateError] = useState<RequestError>();

    const fetchData = useCallback(() => {
        return apiGetProjectConfiguration(orgName, projectName);
    }, [orgName, projectName]);

    const { data, error } = useApi<Object>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const update = useCallback((orgName: ConcordKey, projectName: ConcordKey, config: Object) => {
        const update = async () => {
            try {
                setUpdating(true);
                await apiUpdateProjectConfiguration(orgName, projectName, config);
                setUpdateError(undefined);
            } catch (e) {
                setUpdateError(e);
            } finally {
                setUpdating(false);
            }
        };

        update();
    }, []);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }
    if (updateError) {
        return <RequestErrorActivity error={updateError} />;
    }

    return (
        <div>
            <ProjectConfiguration
                config={data}
                submitting={updating}
                submit={(config) => update(orgName, projectName, config)}
            />
        </div>
    );
};
