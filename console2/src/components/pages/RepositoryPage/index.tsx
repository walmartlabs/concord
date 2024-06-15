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
import { useCallback, useRef, useState } from 'react';
import { Redirect, Route, RouteComponentProps, Switch } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { MainToolbar } from '../../molecules';
import { ConcordKey } from '../../../api/common';
import { LoadingState } from '../../../App';
import { NotFoundPage } from '../index';
import RepositoryEventsActivity from './RepositoryEventsActivity';
import { EditRepositoryActivity } from '../../organisms';
import RepositoryTriggersActivity from './RepositoryTriggersActivity';

interface RouteProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

type TabLink = 'settings' | 'events' | 'triggers' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/settings')) {
        return 'settings';
    } else if (s.endsWith('/events')) {
        return 'events';
    } else if (s.endsWith('/triggers')) {
        return 'triggers';
    }

    return null;
};

const RepositoryPage = (props: RouteComponentProps<RouteProps>) => {
    const { orgName, projectName, repoName } = props.match.params;
    const activeTab = pathToTab(props.location.pathname);

    const stickyRef = useRef(null);
    const loading = React.useContext(LoadingState);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    const baseUrl = `/org/${orgName}/project/${projectName}/repository/${repoName}`;

    return (
        <div ref={stickyRef}>
            <MainToolbar
                loading={loading}
                refresh={refreshHandler}
                stickyRef={stickyRef}
                breadcrumbs={renderBreadcrumbs(orgName, projectName, repoName)}
            />

            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'settings'}>
                    <Icon name="setting" />
                    <Link to={`${baseUrl}/settings`}>Settings</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'triggers'}>
                    <Icon name="lightning" />
                    <Link to={`${baseUrl}/triggers`}>Triggers</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'events'}>
                    <Icon name="search" />
                    <Link to={`${baseUrl}/events`}>Events</Link>
                </Menu.Item>
            </Menu>

            <Switch>
                <Route path={baseUrl} exact={true}>
                    <Redirect to={`${baseUrl}/settings`} />
                </Route>

                <Route path={`${baseUrl}/settings`} exact={true}>
                    <EditRepositoryActivity
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                        forceRefresh={refresh}
                    />
                </Route>
                <Route path={`${baseUrl}/triggers`} exact={true}>
                    <RepositoryTriggersActivity
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                    />
                </Route>
                <Route path={`${baseUrl}/events`} exact={true}>
                    <RepositoryEventsActivity
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                        forceRefresh={refresh}
                    />
                </Route>
                <Route component={NotFoundPage} />
            </Switch>
        </div>
    );
};

const renderBreadcrumbs = (orgName: ConcordKey, projectName: ConcordKey, repoName: ConcordKey) => {
    return (
        <Breadcrumb size="big">
            <Breadcrumb.Section>
                <Link to={`/org/${orgName}/project/${projectName}/repository`}>{projectName}</Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section active={true}>{repoName}</Breadcrumb.Section>
        </Breadcrumb>
    );
};

export default RepositoryPage;
