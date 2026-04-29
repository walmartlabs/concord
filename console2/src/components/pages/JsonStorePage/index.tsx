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
import { useCallback, useState } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { Link } from 'react-router';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { RouteComponentProps, withRouter } from '@/router';
import { ConcordId } from '../../../api/common';
import { NotFoundPage } from '../index';
import StoreSettings from './StoreSettings';
import StoreTeamAccessActivity from './StoreTeamAccessActivity';
import StoreDataList from './StoreDataList';
import StoreQueryList from './StoreQueryList';
import { LoadingState } from '../../../App';
import { AuditLogActivity, BreadcrumbsToolbar } from '../../organisms';

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
    const loading = React.useContext(LoadingState);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const { orgName, storeName } = props.match.params;
    const activeTab = pathToTab(props.location.pathname);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    const baseUrl = `/org/${orgName}/jsonstore/${storeName}`;

    return (
        <>
            <BreadcrumbsToolbar loading={loading} refreshHandler={refreshHandler}>
                <Breadcrumb.Section>
                    <Link to={`/org/${orgName}/jsonstore`}>{orgName}</Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section active={true}>{storeName}</Breadcrumb.Section>
            </BreadcrumbsToolbar>

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

            <Routes>
                <Route index={true} element={<Navigate to="data" replace={true} />} />
                <Route
                    path="data"
                    element={
                        <StoreDataList
                            orgName={orgName}
                            storeName={storeName}
                            forceRefresh={refresh}
                        />
                    }
                />
                <Route
                    path="query"
                    element={
                        <StoreQueryList
                            orgName={orgName}
                            storeName={storeName}
                            forceRefresh={refresh}
                        />
                    }
                />
                <Route
                    path="access"
                    element={
                        <StoreTeamAccessActivity
                            orgName={orgName}
                            storeName={storeName}
                            forceRefresh={refresh}
                        />
                    }
                />
                <Route
                    path="settings"
                    element={
                        <StoreSettings
                            orgName={orgName}
                            storeName={storeName}
                            forceRefresh={refresh}
                        />
                    }
                />
                <Route
                    path="audit"
                    element={
                        <AuditLogActivity
                            forceRefresh={refresh}
                            showRefreshButton={false}
                            filter={{ details: { orgName: orgName, jsonStoreName: storeName } }}
                        />
                    }
                />
                <Route path="*" element={<NotFoundPage />} />
            </Routes>
        </>
    );
};

export default withRouter(StoragePage);
