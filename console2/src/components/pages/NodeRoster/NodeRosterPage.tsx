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
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { MainToolbar } from '../../molecules';
import { useRef } from 'react';
import { useCallback } from 'react';
import { useState } from 'react';
import { LoadingState } from '../../../App';
import { Link } from 'react-router-dom';
import { Redirect, Route, RouteComponentProps, Switch } from 'react-router';
import { NotFoundPage } from '../index';
import NodeRosterHostsList from './NodeRosterHostsList';
import NodeRosterArtifactsList from './NodeRosterArtifactsList';

type TabLink = 'hosts' | 'artifacts' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/hosts')) {
        return 'hosts';
    } else if (s.endsWith('/artifacts')) {
        return 'artifacts';
    }

    return null;
};

const NodeRosterPage = (props: RouteComponentProps) => {
    const activeTab = pathToTab(props.location.pathname);

    const stickyRef = useRef(null);

    const loading = React.useContext(LoadingState);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    const baseUrl = `/noderoster`;

    return (
        <div ref={stickyRef}>
            <MainToolbar
                loading={loading}
                refresh={refreshHandler}
                stickyRef={stickyRef}
                breadcrumbs={renderBreadcrumbs()}
            />

            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'hosts'}>
                    <Icon name="server" />
                    <Link to={`${baseUrl}/hosts`}>Hosts</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'artifacts'}>
                    <Icon name="cubes" />
                    <Link to={`${baseUrl}/artifacts`}>Artifacts</Link>
                </Menu.Item>
            </Menu>

            <Switch>
                <Route path={baseUrl} exact={true}>
                    <Redirect to={`${baseUrl}/hosts`} />
                </Route>

                <Route path={`${baseUrl}/hosts`} exact={true}>
                    <NodeRosterHostsList forceRefresh={refresh} />
                </Route>
                <Route path={`${baseUrl}/artifacts`} exact={true}>
                    <NodeRosterArtifactsList forceRefresh={refresh} />
                </Route>

                <Route component={NotFoundPage} />
            </Switch>
        </div>
    );
};

const renderBreadcrumbs = () => {
    return (
        <Breadcrumb size="big">
            <Breadcrumb.Section active={true}>Node Roster</Breadcrumb.Section>
        </Breadcrumb>
    );
};

export default NodeRosterPage;
