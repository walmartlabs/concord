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
import { get as apiGet, ProjectEntry } from '../../../api/org/project';
import { Divider, Header, Segment } from 'semantic-ui-react';
import EntityId from '../../molecules/EntityId';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

const ProjectSettings = ({ orgName, projectName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiGet(orgName, projectName);
    }, [orgName, projectName]);

    const { data, error, isLoading } = useApi<ProjectEntry>(fetchData, {
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

export default ProjectSettings;
