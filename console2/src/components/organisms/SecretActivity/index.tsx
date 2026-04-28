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
import { Divider, Grid, Header, Icon, Loader, Menu, Segment, Table } from 'semantic-ui-react';
import { parseISO as parseDate } from 'date-fns';

import { ConcordKey, Owner, RequestError } from '../../../api/common';
import {
    SecretEncryptedByType,
    SecretEntry,
    SecretType,
    SecretVisibility,
    typeToText,
} from '../../../api/org/secret';
import { get as apiGetSecret } from '../../../api/org/secret';
import { useApi } from '../../../hooks/useApi';
import {
    HumanizedDuration,
    LocalTimestamp,
    RequestErrorMessage,
    WithCopyToClipboard,
} from '../../molecules';
import {
    AuditLogActivity,
    PublicKeyPopup,
    SecretDeleteActivity,
    SecretOrganizationChangeActivity,
    SecretOwnerChangeActivity,
    SecretProjectActivity,
    SecretRenameActivity,
    SecretTeamAccessActivity,
    SecretVisibilityActivity,
} from '../../organisms';
import { NotFoundPage } from '../../pages';

export type TabLink = 'info' | 'settings' | 'access' | 'audit' | null;

interface ExternalProps {
    activeTab: TabLink;
    orgName: ConcordKey;
    secretName: ConcordKey;
}

const visibilityToText = (v: SecretVisibility) =>
    v === SecretVisibility.PUBLIC ? 'Public' : 'Private';

const encryptedByToText = (t: SecretEncryptedByType) =>
    t === SecretEncryptedByType.SERVER_KEY ? 'Server key' : 'Password';

const renderUser = (e: Owner) => {
    if (!e.userDomain) {
        return e.username;
    }

    return `${e.username}@${e.userDomain}`;
};

const SecretActivity = ({ activeTab, orgName, secretName }: ExternalProps) => {
    const [refreshSecret, toggleRefresh] = React.useState(false);
    const fetchData = React.useCallback(
        () => apiGetSecret(orgName, secretName),
        [orgName, secretName]
    );
    const { data, error, isLoading } = useApi<SecretEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: refreshSecret,
    });

    const reloadSecret = React.useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, [toggleRefresh]);

    const renderPublicKey = (entry: SecretEntry) => (
        <PublicKeyPopup orgName={entry.orgName} secretName={entry.name} />
    );

    const renderInfo = (entry: SecretEntry) => (
        <Grid columns={2}>
            <Grid.Column>
                <Table definition={true}>
                    <Table.Body>
                        <Table.Row>
                            <Table.Cell>Name</Table.Cell>
                            <Table.Cell>{entry.name}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Type</Table.Cell>
                            <Table.Cell>{typeToText(entry.type)}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Visibility</Table.Cell>
                            <Table.Cell>{visibilityToText(entry.visibility)}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Owner</Table.Cell>
                            <Table.Cell>{entry.owner ? renderUser(entry.owner) : '-'}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Actions</Table.Cell>
                            <Table.Cell>
                                {entry.type === SecretType.KEY_PAIR &&
                                    entry.encryptedBy === SecretEncryptedByType.SERVER_KEY &&
                                    renderPublicKey(entry)}
                            </Table.Cell>
                        </Table.Row>
                    </Table.Body>
                </Table>
            </Grid.Column>
            <Grid.Column>
                <Table definition={true}>
                    <Table.Body>
                        <Table.Row>
                            <Table.Cell>Protected by</Table.Cell>
                            <Table.Cell>{encryptedByToText(entry.encryptedBy)}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Restricted to a project</Table.Cell>
                            <Table.Cell>
                                {entry.projects && entry.projects.length > 0
                                    ? entry.projects.map((project, index) => (
                                          <span key={index}>
                                              <Link
                                                  to={`/org/${entry.orgName}/project/${project.name}`}
                                              >
                                                  {project.name}
                                              </Link>
                                              <span>
                                                  {index !== entry.projects.length - 1 ? ', ' : ''}
                                              </span>
                                          </span>
                                      ))
                                    : ' - '}
                            </Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Age</Table.Cell>
                            <Table.Cell>
                                <HumanizedDuration
                                    value={Date.now() - parseDate(entry.createdAt).getTime()}
                                >
                                    <div>
                                        created at:<div>{entry.createdAt}</div>
                                    </div>
                                </HumanizedDuration>
                            </Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Last updated at</Table.Cell>
                            <Table.Cell>
                                {entry.lastUpdatedAt ? (
                                    <LocalTimestamp value={entry.lastUpdatedAt} />
                                ) : (
                                    '-'
                                )}
                            </Table.Cell>
                        </Table.Row>
                    </Table.Body>
                </Table>
            </Grid.Column>
        </Grid>
    );

    const renderSettings = (entry: SecretEntry) => {
        const disabled = !entry;

        return (
            <>
                <Header as="h5" disabled={true} data-testid="secret-settings-id">
                    <WithCopyToClipboard value={entry.id}>ID: {entry.id}</WithCopyToClipboard>
                </Header>

                <Segment>
                    <Header as="h4">Visibility</Header>
                    <SecretVisibilityActivity
                        orgName={entry.orgName}
                        secretId={entry.id}
                        secretName={entry.name}
                        visibility={entry.visibility}
                        onUpdated={reloadSecret}
                    />
                </Segment>

                <Divider horizontal={true} content="Danger Zone" />

                <Segment color="red">
                    <Header as="h4">Projects</Header>
                    <SecretProjectActivity
                        orgName={entry.orgName}
                        secretName={entry.name}
                        projects={entry.projects}
                        onUpdated={reloadSecret}
                    />

                    <Header as="h4">Secret name</Header>
                    <SecretRenameActivity orgName={entry.orgName} secretName={entry.name} />

                    <Header as="h4">Secret owner</Header>
                    <SecretOwnerChangeActivity
                        orgName={entry.orgName}
                        secretName={entry.name}
                        initialOwnerId={entry?.owner?.id}
                        disabled={disabled}
                    />

                    <Header as="h4">Organization</Header>
                    <SecretOrganizationChangeActivity
                        orgName={entry.orgName}
                        secretName={entry.name}
                    />

                    <Header as="h4">Delete Secret</Header>
                    <SecretDeleteActivity orgName={entry.orgName} secretName={entry.name} />
                </Segment>
            </>
        );
    };

    const renderTeamAccess = (entry: SecretEntry) => (
        <SecretTeamAccessActivity
            orgName={entry.orgName}
            secretName={entry.name}
            onUpdated={reloadSecret}
        />
    );

    const renderAuditLog = (entry: SecretEntry) => (
        <AuditLogActivity
            showRefreshButton={false}
            filter={{ details: { orgName: entry.orgName, secretName: entry.name } }}
        />
    );

    if (error) {
        return <RequestErrorMessage error={error as RequestError} />;
    }

    if (isLoading || !data) {
        return <Loader active={true} />;
    }

    const baseUrl = `/org/${orgName}/secret/${secretName}`;

    return (
        <>
            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'info'} data-testid="secret-tab-info">
                    <Icon name="file" />
                    <Link to={`${baseUrl}/info`}>Info</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'access'} data-testid="secret-tab-access">
                    <Icon name="key" />
                    <Link to={`${baseUrl}/access`}>Access</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'settings'} data-testid="secret-tab-settings">
                    <Icon name="setting" />
                    <Link to={`${baseUrl}/settings`}>Settings</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'audit'} data-testid="secret-tab-audit">
                    <Icon name="history" />
                    <Link to={`${baseUrl}/audit`}>Audit Log</Link>
                </Menu.Item>
            </Menu>

            <Routes>
                <Route index={true} element={<Navigate to="info" replace={true} />} />
                <Route path="info" element={renderInfo(data)} />
                <Route path="settings" element={renderSettings(data)} />
                <Route path="access" element={renderTeamAccess(data)} />
                <Route path="audit" element={renderAuditLog(data)} />
                <Route path="*" element={<NotFoundPage />} />
            </Routes>
        </>
    );
};

export default SecretActivity;
