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
import { ProjectEntry } from '../../../api/org/project';
import { actions, selectors, State } from '../../../state/data/projects';
import { comparators } from '../../../utils';
import { RepositoryList, RequestErrorMessage } from '../../molecules';
import { EncryptValueActivity, ProjectTeamAccessActivity } from '../../organisms';
import { NotFoundPage } from '../../pages';
import {
    ProcessListActivity,
    ProjectDeleteActivity,
    ProjectRawPayloadActivity,
    ProjectRenameActivity,
    RedirectButton,
    EditProjectActivity
} from '../index';

export type TabLink = 'process' | 'repository' | 'settings' | 'access' | null;

interface ExternalProps {
    activeTab: TabLink;
    orgName: ConcordKey;
    projectName: ConcordKey;
}

interface StateProps {
    data?: ProjectEntry;
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (orgName: ConcordKey, projectName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectActivity extends React.PureComponent<Props> {
    static renderProcesses(p: ProjectEntry) {
        if (
            p.meta !== undefined &&
            p.meta.ui !== undefined &&
            p.meta.ui.processList !== undefined
        ) {
            return <ProcessListActivity orgName={p.orgName} columns={p.meta.ui.processList} />;
        } else {
            return <ProcessListActivity orgName={p.orgName} />;
        }
    }

    static renderRepositories(p: ProjectEntry) {
        const repos = p.repositories;
        if (!repos) {
            return <h3>No repositories found</h3>;
        }

        const l = Object.keys(repos)
            .map((k) => repos[k])
            .sort(comparators.byName);

        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="Add repository"
                            location={`/org/${p.orgName}/project/${p.name}/repository/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <RepositoryList orgName={p.orgName} projectName={p.name} data={l} />
            </>
        );
    }

    static renderTeamAccess(p: ProjectEntry) {
        return <ProjectTeamAccessActivity orgName={p.orgName} projectName={p.name} />;
    }

    static renderSettings(p: ProjectEntry) {
        return (
            <>
                <Header as="h5" disabled={true}>
                    ID: {p.id}
                </Header>

                <Segment>
                    <Header as="h4">Allow payload archives</Header>
                    <ProjectRawPayloadActivity
                        orgName={p.orgName}
                        projectId={p.id}
                        acceptsRawPayload={p.acceptsRawPayload}
                    />
                </Segment>

                <Segment>
                    <Header as="h4">Encrypt a value</Header>
                    <EncryptValueActivity orgName={p.orgName} projectName={p.name} />
                </Segment>

                <Segment>
                    <EditProjectActivity orgName={p.orgName} projectName={p.name} />
                </Segment>

                <Divider horizontal={true} content="Danger Zone" />

                <Segment color="red">
                    <Header as="h4">Project name</Header>
                    <ProjectRenameActivity
                        orgName={p.orgName}
                        projectId={p.id}
                        projectName={p.name}
                    />

                    <Header as="h4">Delete project</Header>
                    <ProjectDeleteActivity orgName={p.orgName} projectName={p.name} />
                </Segment>
            </>
        );
    }

    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName, projectName: newProjectName } = this.props;
        const { orgName: oldOrgName, projectName: oldProjectName } = prevProps;

        if (oldOrgName !== newOrgName || oldProjectName !== newProjectName) {
            this.init();
        }
    }

    init() {
        const { orgName, projectName, load } = this.props;
        load(orgName, projectName);
    }

    render() {
        const { loading, error, data } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !data) {
            return <Loader active={true} />;
        }

        const { activeTab, orgName, projectName } = this.props;
        const baseUrl = `/org/${orgName}/project/${projectName}`;

        return (
            <>
                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'process'}>
                        <Icon name="tasks" />
                        <Link to={`${baseUrl}/process`}>Processes</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'repository'}>
                        <Icon name="code" />
                        <Link to={`${baseUrl}/repository`}>Repositories</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'access'}>
                        <Icon name="key" />
                        <Link to={`${baseUrl}/access`}>Access</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'settings'}>
                        <Icon name="setting" />
                        <Link to={`${baseUrl}/settings`}>Settings</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={baseUrl} exact={true}>
                        <Redirect to={`${baseUrl}/process`} />
                    </Route>

                    <Route path={`${baseUrl}/process`} exact={true}>
                        {ProjectActivity.renderProcesses(data)}
                    </Route>
                    <Route path={`${baseUrl}/repository`} exact={true}>
                        {ProjectActivity.renderRepositories(data)}
                    </Route>
                    <Route path={`${baseUrl}/access`} exact={true}>
                        {ProjectActivity.renderTeamAccess(data)}
                    </Route>
                    <Route path={`${baseUrl}/settings`} exact={true}>
                        {ProjectActivity.renderSettings(data)}
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

const mapStateToProps = (
    { projects }: { projects: State },
    { orgName, projectName }: ExternalProps
): StateProps => ({
    data: selectors.projectByName(projects, orgName, projectName),
    loading: projects.loading,
    error: projects.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (orgName, projectName) => dispatch(actions.getProject(orgName, projectName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectActivity);
