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
import { RedirectButton, RequestErrorActivity } from '../index';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import { useCallback } from 'react';
import { get as apiGet } from '../../../api/org/project';
import { Menu } from 'semantic-ui-react';
import { RepositoryList } from '../../molecules';
import { comparators } from '../../../utils';
import { RepositoryEntry } from '../../../api/org/project/repository';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

const ProjectRepositories = ({ orgName, projectName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData: () => Promise<RepositoryEntry[]> = useCallback(async () => {
        const project = await apiGet(orgName, projectName);
        if (project.repositories === undefined) {
            return [];
        }

        const repositories = project.repositories;
        return Object.keys(repositories)
            .map((k) => repositories[k])
            .sort(comparators.byName);
    }, [orgName, projectName]);

    const { data, error, isLoading } = useApi<RepositoryEntry[]>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item position={'right'}>
                    <RedirectButton
                        icon="plus"
                        positive={true}
                        labelPosition="left"
                        content="Add repository"
                        location={`/org/${orgName}/project/${projectName}/repository/_new`}
                    />
                </Menu.Item>
            </Menu>

            <RepositoryList
                orgName={orgName}
                projectName={projectName}
                data={data}
                loading={isLoading}
            />
        </>
    );
};

export default ProjectRepositories;
