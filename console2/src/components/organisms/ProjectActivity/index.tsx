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
import { Redirect, Route, Switch } from 'react-router';
import { Link } from 'react-router-dom';
import { Icon, Menu } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { AuditLogActivity, ProjectTeamAccessActivity } from '../../organisms';
import { NotFoundPage } from '../../pages';
import ProjectConfigurationActivity from '../ProjectConfigurationActivity';
import ProjectProcesses from './ProjectProcesses';
import ProjectRepositories from './ProjectRepositories';
import ProjectSettings from './ProjectSettings';
import ProjectCheckpoints from './ProjectCheckpoints';

export type TabLink =
    | 'process'
    | 'checkpoint'
    | 'repository'
    | 'settings'
    | 'access'
    | 'configuration'
    | 'audit'
    | null;

interface ExternalProps {
    activeTab: TabLink;
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

const ProjectActivity = ({ activeTab, orgName, projectName, forceRefresh }: ExternalProps) => {
    const baseUrl = `/org/${orgName}/project/${projectName}`;

    return (
        <>
            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'process'}>
                    <Icon name="tasks" />
                    <Link to={`${baseUrl}/process`}>Processes</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'checkpoint'}>
                    <Icon name="checkmark" />
                    <Link to={`${baseUrl}/checkpoint`}>Checkpoints</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'repository'}>
                    <Icon name="code" />
                    <Link to={`${baseUrl}/repository`}>Repositories</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'access'}>
                    <Icon name="key" />
                    <Link to={`${baseUrl}/access`}>Access</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'configuration'}>
                    <Icon name="save" />
                    <Link to={`${baseUrl}/configuration`}>Configuration</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'settings'}>
                    <Icon name="setting" />
                    <Link to={`${baseUrl}/settings`}>Settings</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'audit'}>
                    <Icon name="history" />
                    <Link to={`${baseUrl}/audit`}>Audit Log</Link>
                </Menu.Item>
            </Menu>

            <Switch>
                <Route path={baseUrl} exact={true}>
                    <Redirect to={`${baseUrl}/process`} />
                </Route>

                <Route path={`${baseUrl}/process`} exact={true}>
                    <ProjectProcesses
                        orgName={orgName}
                        projectName={projectName}
                        forceRefresh={forceRefresh}
                    />
                </Route>
                <Route path={`${baseUrl}/checkpoint`} exact={true}>
                    <ProjectCheckpoints
                        orgName={orgName}
                        projectName={projectName}
                        forceRefresh={forceRefresh}
                    />
                </Route>
                <Route path={`${baseUrl}/repository`} exact={true}>
                    <ProjectRepositories
                        orgName={orgName}
                        projectName={projectName}
                        forceRefresh={forceRefresh}
                    />
                </Route>
                <Route path={`${baseUrl}/access`} exact={true}>
                    <ProjectTeamAccessActivity orgName={orgName} projectName={projectName} />
                </Route>
                <Route path={`${baseUrl}/configuration`} exact={true}>
                    <ProjectConfigurationActivity
                        orgName={orgName}
                        projectName={projectName}
                        forceRefresh={forceRefresh}
                    />
                </Route>
                <Route path={`${baseUrl}/settings`} exact={true}>
                    <ProjectSettings
                        orgName={orgName}
                        projectName={projectName}
                        forceRefresh={forceRefresh}
                    />
                </Route>
                <Route path={`${baseUrl}/audit`} exact={true}>
                    <AuditLogActivity
                        showRefreshButton={false}
                        filter={{ details: { orgName: orgName, projectName: projectName } }}
                        forceRefresh={forceRefresh}
                    />
                </Route>

                <Route component={NotFoundPage} />
            </Switch>
        </>
    );
};

export default ProjectActivity;
