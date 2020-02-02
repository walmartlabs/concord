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
import { Redirect, Route, RouteComponentProps, Switch } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { MainToolbar } from '../../molecules';
import { useRef } from 'react';
import { useCallback } from 'react';
import { useState } from 'react';
import { NotFoundPage } from '../index';
import StoreSettings from './StoreSettings';
import StoreTeamAccessActivity from './StoreTeamAccessActivity';
import StoreDataList from './StoreDataList';
import StoreQueryList from './StoreQueryList';
import { LoadingState } from '../../../App';
import { AuditLogActivity } from '../../organisms';

interface RouteProps {
    orgName: ConcordId;
    storeName: ConcordId;
}

type TabLink = 'data' | 'query' | 'access' | 'settings' | 'audit' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/data')) {
        return 'data';
    } else if (s.endsWith('/query')) {
        return 'query';
    } else if (s.endsWith('/access')) {
        return 'access';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    } else if (s.endsWith('/audit')) {
        return 'audit';
    }

    return null;
};

const StoragePage = (props: RouteComponentProps<RouteProps>) => {
    const stickyRef = useRef(null);

    const loading = React.useContext(LoadingState);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const { orgName, storeName } = props.match.params;
    const activeTab = pathToTab(props.location.pathname);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    const baseUrl = `/org/${orgName}/jsonstore/${storeName}`;

    return (
        <div ref={stickyRef}>
            <MainToolbar
                loading={loading}
                refresh={refreshHandler}
                stickyRef={stickyRef}
                breadcrumbs={renderBreadcrumbs(orgName, storeName)}
            />

            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'data'}>
                    <Icon name="file alternate" />
                    <Link to={`${baseUrl}/data`}>Data</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'query'}>
                    <Icon name="search" />
                    <Link to={`${baseUrl}/query`}>Queries</Link>
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
                    <Redirect to={`${baseUrl}/data`} />
                </Route>

                <Route path={`${baseUrl}/data`} exact={true}>
                    <StoreDataList orgName={orgName} storeName={storeName} forceRefresh={refresh} />
                </Route>
                <Route path={`${baseUrl}/query`} exact={true}>
                    <StoreQueryList
                        orgName={orgName}
                        storeName={storeName}
                        forceRefresh={refresh}
                    />
                </Route>
                <Route path={`${baseUrl}/access`} exact={true}>
                    <StoreTeamAccessActivity
                        orgName={orgName}
                        storeName={storeName}
                        forceRefresh={refresh}
                    />
                </Route>
                <Route path={`${baseUrl}/settings`} exact={true}>
                    <StoreSettings orgName={orgName} storeName={storeName} forceRefresh={refresh} />
                </Route>
                <Route path={`${baseUrl}/audit`} exact={true}>
                    <AuditLogActivity
                        forceRefresh={refresh}
                        showRefreshButton={false}
                        filter={{ details: { orgName: orgName, jsonStoreName: storeName } }}
                    />
                </Route>

                <Route component={NotFoundPage} />
            </Switch>
        </div>
    );
};

const renderBreadcrumbs = (orgName: string, storeName: string) => {
    return (
        <Breadcrumb size="big">
            <Breadcrumb.Section>
                <Link to={`/org/${orgName}/jsonstore`}>{orgName}</Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section active={true}>{storeName}</Breadcrumb.Section>
        </Breadcrumb>
    );
};

export default StoragePage;
