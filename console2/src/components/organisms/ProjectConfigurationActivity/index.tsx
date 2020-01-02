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
import { Loader } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import {
    getProjectConfiguration as apiGetProjectConfiguration,
    updateProjectConfiguration as apiUpdateProjectConfiguration
} from '../../../api/org/project';
import { RequestErrorMessage } from '../../molecules';
import ProjectConfiguration from '../../molecules/ProjectConfiguration';

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

export default ({ orgName, projectName }: Props) => {
    const [config, setConfig] = useState<Object>({});
    const [loading, setLoading] = useState(false);
    const [updating, setUpdating] = useState(false);
    const [error, setError] = useState<RequestError>();

    useEffect(() => {
        const load = async () => {
            try {
                setLoading(true);
                const v = await apiGetProjectConfiguration(orgName, projectName);
                setConfig(v);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        load();
    }, [orgName, projectName]);

    const update = useCallback((orgName: ConcordKey, projectName: ConcordKey, config: Object) => {
        const update = async () => {
            try {
                setUpdating(true);
                await apiUpdateProjectConfiguration(orgName, projectName, config);
                setConfig(config);
            } catch (e) {
                setError(e);
            } finally {
                setUpdating(false);
            }
        };

        update();
    }, []);

    if (loading || updating) {
        return <Loader active={true} />;
    }

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    return (
        <div>
            <ProjectConfiguration
                config={config}
                submitting={updating}
                submit={(config) => update(orgName, projectName, config)}
            />
        </div>
    );
};
