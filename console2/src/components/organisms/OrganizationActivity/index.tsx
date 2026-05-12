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
import { Navigate, Route, Routes } from 'react-router';
import { Link } from 'react-router';
import { Icon, Menu } from 'semantic-ui-react';
import { ConcordKey } from '../../../api/common';
import {
    AuditLogActivity,
    ProjectListActivity,
    SecretListActivity,
    TeamListActivity,
} from '../../organisms';

import { NotFoundPage } from '../../pages';
import StorageListActivity from '../../pages/JsonStorePage/StoreListActivity';
import OrganizationSettings from './OrganizationSettings';
import OrganizationProcesses from './OrganizationProcesses';

export type TabLink =
    | 'process'
    | 'project'
    | 'secret'
    | 'team'
    | 'jsonstore'
    | 'settings'
    | 'audit'
    | null;

interface ExternalProps {
    activeTab: TabLink;
    orgName: ConcordKey;
    forceRefresh: any;
}

const OrganizationActivity = ({ activeTab, orgName, forceRefresh }: ExternalProps) => {
    const baseUrl = `/org/${orgName}`;

    return (
        <>
            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'project'}>
                    <Icon name="sitemap" />
                    <Link to={`${baseUrl}/project`}>Projects</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'process'}>
                    <Icon name="tasks" />
                    <Link to={`${baseUrl}/process`}>Processes</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'secret'}>
                    <Icon name="lock" />
                    <Link to={`${baseUrl}/secret`}>Secrets</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'team'}>
                    <Icon name="users" />
                    <Link to={`${baseUrl}/team`}>Teams</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'jsonstore'}>
                    <Icon name="database" />
                    <Link to={`${baseUrl}/jsonstore`}>JSON Stores</Link>
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

            <Routes>
                <Route index={true} element={<Navigate to="project" replace={true} />} />
                <Route
                    path="project"
                    element={<ProjectListActivity orgName={orgName} forceRefresh={forceRefresh} />}
                />
                <Route
                    path="process"
                    element={
                        <OrganizationProcesses orgName={orgName} forceRefresh={forceRefresh} />
                    }
                />
                <Route
                    path="secret"
                    element={<SecretListActivity orgName={orgName} forceRefresh={forceRefresh} />}
                />
                <Route
                    path="team"
                    element={<TeamListActivity orgName={orgName} forceRefresh={forceRefresh} />}
                />
                <Route
                    path="jsonstore"
                    element={<StorageListActivity orgName={orgName} forceRefresh={forceRefresh} />}
                />
                <Route
                    path="settings"
                    element={<OrganizationSettings orgName={orgName} forceRefresh={forceRefresh} />}
                />
                <Route
                    path="audit"
                    element={
                        <AuditLogActivity
                            showRefreshButton={false}
                            filter={{ details: { orgName: orgName } }}
                            forceRefresh={forceRefresh}
                        />
                    }
                />
                <Route path="*" element={<NotFoundPage />} />
            </Routes>
        </>
    );
};

export default OrganizationActivity;
