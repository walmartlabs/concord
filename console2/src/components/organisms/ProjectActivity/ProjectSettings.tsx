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
import {
    EditProjectActivity,
    EncryptValueActivity,
    ProjectDeleteActivity,
    ProjectOrganizationChangeActivity,
    ProjectOwnerChangeActivity,
    ProjectRawPayloadModeActivity,
    ProjectOutVariablesModeActivity,
    ProjectRenameActivity,
    RequestErrorActivity
} from '../index';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import { useCallback } from 'react';
import {get as apiGet, getCapacity as apiGetCapacity, KVCapacity, ProjectEntry} from '../../../api/org/project';
import {Divider, Header, Icon, Progress, Segment} from 'semantic-ui-react';
import EntityId from '../../molecules/EntityId';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

const ProjectSettings = ({ orgName, projectName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(async () => {
        const project = await apiGet(orgName, projectName);
        const capacity = await apiGetCapacity(orgName, projectName);
        return { ...project, ...capacity };
    }, [orgName, projectName]);

    const { data, error, isLoading } = useApi<ProjectEntry & KVCapacity>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <Header as="h5" disabled={true}>
                <EntityId id={data?.id} />
            </Header>

            <Segment disabled={isLoading}>
                <Header as="h4">KV capacity</Header>
                <Capacity org={data?.orgName} project={data?.name} current={data?.size} max={data?.maxSize} />
            </Segment>

            <Segment disabled={isLoading}>
                <Header as="h4">Allow payload archives</Header>
                {data && (
                    <ProjectRawPayloadModeActivity
                        orgName={orgName}
                        projectId={data.id}
                        initialValue={data.rawPayloadMode}
                    />
                )}
            </Segment>

            <Segment disabled={isLoading}>
                <Header as="h4">Allow out variable names in request</Header>
                {data && (
                    <ProjectOutVariablesModeActivity
                        orgName={orgName}
                        projectId={data.id}
                        initialValue={data.outVariablesMode}
                    />
                )}
            </Segment>

            <Segment disabled={isLoading}>
                <Header as="h4">Encrypt a value</Header>
                <EncryptValueActivity orgName={orgName} projectName={projectName} />
            </Segment>

            <Segment disabled={isLoading}>
                <EditProjectActivity orgName={orgName} projectName={projectName} initial={data} />
            </Segment>

            <Divider horizontal={true} content="Danger Zone" disabled={isLoading} />

            <Segment color="red" disabled={isLoading}>
                <Header as="h4">Project name</Header>
                <ProjectRenameActivity
                    orgName={orgName}
                    projectId={data?.id}
                    projectName={projectName}
                    disabled={isLoading}
                />

                <Header as="h4">Project owner</Header>
                <ProjectOwnerChangeActivity
                    orgName={orgName}
                    projectName={projectName}
                    initialOwnerId={data?.owner?.id}
                    disabled={isLoading}
                />

                <Header as="h4">Organization</Header>
                <ProjectOrganizationChangeActivity
                    orgName={orgName}
                    projectName={projectName}
                    disabled={isLoading}
                />

                <Header as="h4">Delete project</Header>
                <ProjectDeleteActivity
                    orgName={orgName}
                    projectName={projectName}
                    disabled={isLoading}
                />
            </Segment>
        </>
    );
};

interface CapacityProps {
    org?: ConcordKey;
    project?: ConcordKey;
    current?: number;
    max?: number;
}

const Capacity = ({ org, project, current, max }: CapacityProps) => {
    if (org === undefined || project === undefined) {
        return (
            <div>
                <em>Loading</em>
            </div>
        );
    }

    return (
        <>
            {(current !== undefined && max !== undefined) &&
            <div>
                <Progress
                    percent={(current / max) * 100}
                    size={'tiny'}
                    color={'red'}
                    style={{ width: '30%' }}
                />
            </div>
            }
            <Header size="tiny" style={{ marginTop: '0px', color: 'rgba(0, 0, 0, 0.5)' }}>
                <a
                    href={`/api/v1/org/${org}/project/${project}/kv`}
                    rel="noopener noreferrer"
                    target="_blank">
                    <Icon name={'database'} color={'blue'} link={true}/>
                    Used {current} of {max || 'Unlimited'}
                </a>
            </Header>
        </>
    );
};

export default ProjectSettings;
