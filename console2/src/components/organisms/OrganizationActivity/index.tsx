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
import { connect, Dispatch } from 'react-redux';
import { Redirect, Route, Switch } from 'react-router';
import { Link } from 'react-router-dom';
import { Divider, Header, Icon, Loader, Menu, Segment } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { OrganizationEntry } from '../../../api/org';
import { actions, selectors, State } from '../../../state/data/orgs';
import { RequestErrorMessage } from '../../molecules';
import {
    ProcessListActivity,
    ProjectDeleteActivity,
    ProjectListActivity,
    ProjectRenameActivity,
    SecretListActivity,
    TeamListActivity
} from '../../organisms';

import { NotFoundPage } from '../../pages';
import OrganizationOwnerChangeActivity from '../OrganizationOwnerChangeActivity/OrganizationOwnerChangeActivity';

export type TabLink = 'process' | 'project' | 'secret' | 'team' | 'settings' | null;

interface ExternalProps {
    activeTab: TabLink;
    orgName: ConcordKey;
}

interface StateProps {
    data?: OrganizationEntry;
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (orgName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class OrganizationActivity extends React.PureComponent<Props> {
    static renderProcesses(e: OrganizationEntry) {
        if (
            e.meta !== undefined &&
            e.meta.ui !== undefined &&
            e.meta.ui.processList !== undefined
        ) {
            return (
                <ProcessListActivity
                    orgName={e.name}
                    columns={e.meta.ui.processList}
                    usePagination={true}
                />
            );
        } else {
            return <ProcessListActivity orgName={e.name} usePagination={true} />;
        }
    }

    static renderProjects(orgName: string) {
        return <ProjectListActivity orgName={orgName} />;
    }

    static renderSecrets(orgName: string) {
        return <SecretListActivity orgName={orgName} />;
    }

    static renderTeams(orgName: string) {
        return <TeamListActivity orgName={orgName} />;
    }

    static renderSettings(e: OrganizationEntry) {
        return (
            <>
                <Header as="h5" disabled={true}>
                    ID: {e.id}
                </Header>

                <Divider horizontal={true} content="Danger Zone" />

                <Segment color="red">
                    <Header as="h4">Organization owner</Header>
                    <OrganizationOwnerChangeActivity
                        orgId={e.id}
                        orgName={e.name}
                        owner={e.owner && e.owner.username}
                    />
                </Segment>
            </>
        );
    }

    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName } = this.props;
        const { orgName: oldOrgName } = prevProps;

        if (oldOrgName !== newOrgName) {
            this.init();
        }
    }

    init() {
        const { orgName, load } = this.props;
        load(orgName);
    }

    render() {
        const { loading, error, data } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !data) {
            return <Loader active={true} />;
        }

        const { activeTab, orgName } = this.props;

        const baseUrl = `/org/${orgName}`;

        return (
            <>
                <Menu tabular={true}>
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
                    <Menu.Item active={activeTab === 'settings'}>
                        <Icon name="setting" />
                        <Link to={`${baseUrl}/settings`}>Settings</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={baseUrl} exact={true}>
                        <Redirect to={`${baseUrl}/project`} />
                    </Route>
                    <Route path={`${baseUrl}/project`}>
                        {OrganizationActivity.renderProjects(data.name)}
                    </Route>
                    <Route path={`${baseUrl}/process`}>
                        {OrganizationActivity.renderProcesses(data)}
                    </Route>
                    <Route path={`${baseUrl}/secret`} exact={true}>
                        {OrganizationActivity.renderSecrets(data.name)}
                    </Route>
                    <Route path={`${baseUrl}/team`} exact={true}>
                        {OrganizationActivity.renderTeams(data.name)}
                    </Route>
                    <Route path={`${baseUrl}/settings`} exact={true}>
                        {OrganizationActivity.renderSettings(data)}
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

const mapStateToProps = ({ orgs }: { orgs: State }, { orgName }: ExternalProps): StateProps => ({
    data: selectors.orgByName(orgs, orgName),
    loading: orgs.loading,
    error: orgs.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (orgName) => dispatch(actions.getOrg(orgName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(OrganizationActivity);
