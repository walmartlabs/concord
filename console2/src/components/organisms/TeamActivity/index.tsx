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
import { Link, Navigate, Route, Routes } from 'react-router';
import { Divider, Header, Icon, Loader, Menu, Segment } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { TeamEntry, get as apiGetTeam } from '../../../api/org/team';
import { useApi } from '../../../hooks/useApi';
import { RequestErrorMessage, WithCopyToClipboard } from '../../molecules';
import { NotFoundPage } from '../../pages';
import {
    AuditLogActivity,
    TeamDeleteActivity,
    TeamMemberList2,
    TeamLdapGroupList2,
    TeamRenameActivity,
} from '../../organisms';

export type TabLink = 'members' | 'ldapGroups' | 'settings' | 'audit' | null;

interface ExternalProps {
    orgName: ConcordKey;
    teamName: ConcordKey;
    activeTab: TabLink;
}

const TeamActivity = ({ orgName, teamName, activeTab }: ExternalProps) => {
    const fetchData = React.useCallback(() => apiGetTeam(orgName, teamName), [orgName, teamName]);
    const { data, error, isLoading } = useApi<TeamEntry>(fetchData, {
        fetchOnMount: true,
    });

    const renderSetting = (team: TeamEntry) => (
        <>
            <Header as="h5" disabled={true} data-testid="team-settings-id">
                <WithCopyToClipboard value={team.id}>ID: {team.id}</WithCopyToClipboard>
            </Header>

            <Divider horizontal={true} content="Danger Zone" />

            <Segment color="red">
                <Header as="h4">Team name</Header>
                <TeamRenameActivity orgName={team.orgName} teamId={team.id} teamName={team.name} />

                <Header as="h4">Delete team</Header>
                <TeamDeleteActivity orgName={team.orgName} teamName={team.name} />
            </Segment>
        </>
    );

    if (error) {
        return <RequestErrorMessage error={error as RequestError} />;
    }

    if (isLoading || !data) {
        return <Loader active={true} />;
    }

    const baseUrl = `/org/${orgName}/team/${teamName}`;

    return (
        <>
            <Menu tabular={true}>
                <Menu.Item active={activeTab === 'members'} data-testid="team-tab-members">
                    <Icon name="users" />
                    <Link to={`/org/${orgName}/team/${teamName}/members`}>Members</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'ldapGroups'} data-testid="team-tab-ldapGroups">
                    <Icon name="users" />
                    <Link to={`/org/${orgName}/team/${teamName}/ldapGroups`}>LDAP Groups</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'settings'} data-testid="team-tab-settings">
                    <Icon name="setting" />
                    <Link to={`/org/${orgName}/team/${teamName}/settings`}>Settings</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'audit'} data-testid="team-tab-audit">
                    <Icon name="history" />
                    <Link to={`/org/${orgName}/team/${teamName}/audit`}>Audit Log</Link>
                </Menu.Item>
            </Menu>

            <Routes>
                <Route index={true} element={<Navigate to="members" replace={true} />} />
                <Route
                    path="members"
                    element={<TeamMemberList2 orgName={orgName} teamName={teamName} />}
                />
                <Route
                    path="ldapGroups"
                    element={<TeamLdapGroupList2 orgName={orgName} teamName={teamName} />}
                />
                <Route path="settings" element={renderSetting(data)} />
                <Route
                    path="audit"
                    element={<AuditLogActivity filter={{ details: { orgName, teamName } }} />}
                />
                <Route path="*" element={<NotFoundPage />} />
            </Routes>
        </>
    );
};

export default TeamActivity;
