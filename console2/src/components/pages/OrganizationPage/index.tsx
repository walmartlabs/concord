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
import { Redirect, Route, RouteComponentProps, Switch, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';
import {
    ProcessList,
    ProjectListActivity,
    RedirectButton,
    SecretList,
    TeamList
} from '../../organisms';
import { NotFoundPage } from '../index';

interface RouteProps {
    orgName: string;
}

type TabLink = 'process' | 'project' | 'secret' | 'team' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/process')) {
        return 'process';
    } else if (s.endsWith('/project')) {
        return 'project';
    } else if (s.endsWith('/secret')) {
        return 'secret';
    } else if (s.endsWith('/team')) {
        return 'team';
    }

    return null;
};

class OrganizationPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        // TODO move into OrganizationActivity
        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to="/org">Organizations</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{orgName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'project'}>
                        <Icon name="sitemap" />
                        <Link to={`${url}/project`}>Projects</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'process'}>
                        <Icon name="tasks" />
                        <Link to={`${url}/process`}>Processes</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'secret'}>
                        <Icon name="lock" />
                        <Link to={`${url}/secret`}>Secrets</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'team'}>
                        <Icon name="users" />
                        <Link to={`${url}/team`}>Teams</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/project`} />
                    </Route>
                    <Route path={`${url}/project`}>{this.renderProjects()}</Route>
                    <Route path={`${url}/process`}>
                        <ProcessList orgName={orgName} />
                    </Route>
                    <Route path={`${url}/secret`} exact={true}>
                        {this.renderSecrets()}
                    </Route>
                    <Route path={`${url}/team`} exact={true}>
                        {this.renderTeams()}
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }

    renderProjects() {
        const { orgName } = this.props.match.params;
        return <ProjectListActivity orgName={orgName} />;
    }

    renderSecrets() {
        const { orgName } = this.props.match.params;
        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="New secret"
                            location={`/org/${orgName}/secret/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <SecretList orgName={orgName} />
            </>
        );
    }

    renderTeams() {
        const { orgName } = this.props.match.params;
        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="New team"
                            location={`/org/${orgName}/team/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <TeamList orgName={orgName} />
            </>
        );
    }
}

export default withRouter(OrganizationPage);
