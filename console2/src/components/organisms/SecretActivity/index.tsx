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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Link, Redirect, Route, Switch } from 'react-router-dom';
import { Divider, Grid, Header, Icon, Loader, Menu, Segment, Table } from 'semantic-ui-react';

import {
    parseISO as parseDate,
} from 'date-fns';

import { ConcordKey, Owner, RequestError } from '../../../api/common';
import {
    SecretEncryptedByType,
    SecretEntry,
    SecretType,
    SecretVisibility,
    typeToText
} from '../../../api/org/secret';
import { actions, selectors, State } from '../../../state/data/secrets';
import {HumanizedDuration, LocalTimestamp, RequestErrorMessage, WithCopyToClipboard} from '../../molecules';
import {
    AuditLogActivity,
    PublicKeyPopup,
    SecretDeleteActivity,
    SecretOrganizationChangeActivity,
    SecretOwnerChangeActivity,
    SecretProjectActivity,
    SecretRenameActivity,
    SecretTeamAccessActivity,
    SecretVisibilityActivity
} from '../../organisms';
import { NotFoundPage } from '../../pages';

export type TabLink = 'info' | 'settings' | 'access' | 'audit' | null;

interface ExternalProps {
    activeTab: TabLink;
    orgName: ConcordKey;
    secretName: ConcordKey;
    forceRefresh: any;
}

interface StateProps {
    data?: SecretEntry;
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (orgName: ConcordKey, secretName: ConcordKey) => void;
}

type Props = StateProps & DispatchProps & ExternalProps;

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

class SecretActivity extends React.PureComponent<Props> {
    static renderPublicKey(data: SecretEntry) {
        return <PublicKeyPopup orgName={data.orgName} secretName={data.name} />;
    }

    static renderInfo(data: SecretEntry) {
        return (
            <Grid columns={2}>
                <Grid.Column>
                    <Table definition={true}>
                        <Table.Body>
                            <Table.Row>
                                <Table.Cell>Name</Table.Cell>
                                <Table.Cell>{data.name}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Type</Table.Cell>
                                <Table.Cell>{typeToText(data.type)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Visibility</Table.Cell>
                                <Table.Cell>{visibilityToText(data.visibility)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Owner</Table.Cell>
                                <Table.Cell>{data.owner ? renderUser(data.owner) : '-'}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Actions</Table.Cell>
                                <Table.Cell>
                                    {data.type === SecretType.KEY_PAIR &&
                                        data.encryptedBy === SecretEncryptedByType.SERVER_KEY &&
                                        SecretActivity.renderPublicKey(data)}
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
                                <Table.Cell>{encryptedByToText(data.encryptedBy)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Restricted to a project</Table.Cell>
                                <Table.Cell>
                                    {data.projects && data.projects.length > 0
                                        ? data.projects.map((project, index) => (
                                              <span key={index}>
                                                  <Link
                                                      to={`/org/${data.orgName}/project/${project.name}`}
                                                  >
                                                      {project.name}
                                                  </Link>
                                                  <span>
                                                      {index !== data.projects.length - 1
                                                          ? ', '
                                                          : ''}
                                                  </span>
                                              </span>
                                          ))
                                        : ' - '}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Age</Table.Cell>
                                <Table.Cell>
                                    <HumanizedDuration value={Date.now() - parseDate(data.createdAt).getTime()}>
                                        <div>
                                            created at:<div>{data.createdAt}</div>
                                        </div>
                                    </HumanizedDuration>
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Last updated at</Table.Cell>
                                <Table.Cell>
                                    {data.lastUpdatedAt ? <LocalTimestamp value={data.lastUpdatedAt}/> : '-'}
                                </Table.Cell>
                            </Table.Row>
                        </Table.Body>
                    </Table>
                </Grid.Column>
            </Grid>
        );
    }

    static renderSettings(data: SecretEntry) {
        const disabled = !data;

        return (
            <>
                <Header as="h5" disabled={true}>
                    <WithCopyToClipboard value={data.id}>ID: {data.id}</WithCopyToClipboard>
                </Header>

                <Segment>
                    <Header as="h4">Visibility</Header>
                    <SecretVisibilityActivity
                        orgName={data.orgName}
                        secretId={data.id}
                        secretName={data.name}
                        visibility={data.visibility}
                    />
                </Segment>

                <Divider horizontal={true} content="Danger Zone" />

                <Segment color="red">
                    <Header as="h4">Projects</Header>
                    <SecretProjectActivity
                        orgName={data.orgName}
                        secretName={data.name}
                        projects={data.projects}
                    />

                    <Header as="h4">Secret name</Header>
                    <SecretRenameActivity
                        orgName={data.orgName}
                        secretId={data.id}
                        secretName={data.name}
                    />

                    <Header as="h4">Secret owner</Header>
                    <SecretOwnerChangeActivity
                        orgName={data.orgName}
                        secretName={data.name}
                        initialOwnerId={data?.owner?.id}
                        disabled={disabled}
                    />

                    <Header as="h4">Organization</Header>
                    <SecretOrganizationChangeActivity
                        orgName={data.orgName}
                        secretName={data.name}
                    />

                    <Header as="h4">Delete Secret</Header>
                    <SecretDeleteActivity orgName={data.orgName} secretName={data.name} />
                </Segment>
            </>
        );
    }

    static renderTeamAccess(data: SecretEntry) {
        return <SecretTeamAccessActivity orgName={data.orgName} secretName={data.name} />;
    }

    static renderAuditLog(e: SecretEntry) {
        return (
            <AuditLogActivity
                showRefreshButton={false}
                filter={{ details: { orgName: e.orgName, secretName: e.name } }}
            />
        );
    }

    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName, secretName: newSecretName } = this.props;
        const { orgName: oldOrgName, secretName: oldSecretName } = prevProps;

        if (oldOrgName !== newOrgName || oldSecretName !== newSecretName) {
            this.init();
        }
    }

    init() {
        const { load, orgName, secretName } = this.props;
        load(orgName, secretName);
    }

    render() {
        const { error, loading, data } = this.props;
        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !data) {
            return <Loader active={true} />;
        }

        const { activeTab, orgName, secretName } = this.props;
        const baseUrl = `/org/${orgName}/secret/${secretName}`;

        return (
            <>
                <Menu tabular={true} style={{ marginTop: 0 }}>
                    <Menu.Item active={activeTab === 'info'}>
                        <Icon name="file" />
                        <Link to={`${baseUrl}/info`}>Info</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'access'}>
                        <Icon name="key" />
                        <Link to={`${baseUrl}/access`}>Access</Link>
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
                        <Redirect to={`${baseUrl}/info`} />
                    </Route>
                    <Route path={`${baseUrl}/info`} exact={true}>
                        {SecretActivity.renderInfo(data)}
                    </Route>
                    <Route path={`${baseUrl}/access`} exact={true}>
                        {SecretActivity.renderTeamAccess(data)}
                    </Route>
                    <Route path={`${baseUrl}/settings`} exact={true}>
                        {SecretActivity.renderSettings(data)}
                    </Route>
                    <Route path={`${baseUrl}/audit`} exact={true}>
                        {SecretActivity.renderAuditLog(data)}
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

const mapStateToProps = (
    { secrets }: { secrets: State },
    { orgName, secretName }: ExternalProps
): StateProps => ({
    data: selectors.secretByName(secrets, orgName, secretName),
    loading: secrets.listSecrets.running,
    error: secrets.listSecrets.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (orgName: ConcordKey, secretName: ConcordKey) =>
        dispatch(actions.getSecret(orgName, secretName))
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretActivity);
