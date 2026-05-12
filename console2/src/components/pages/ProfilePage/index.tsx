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
import { Link, Navigate, Route, Routes, useLocation } from 'react-router';
import { Breadcrumb, Grid, Menu } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';
import { NotFoundPage } from '../index';
import { APITokensListPage, NewAPITokenPage, UserInfoPage } from '../../../components/pages';

type TabLink = 'user-info' | 'token' | null;

const pathToTab = (s: string): TabLink => {
    if (s.includes('/api-token')) {
        return 'token';
    } else if (s.includes('/user-info')) {
        return 'user-info';
    }

    return null;
};

const ProfilePage = () => {
    const location = useLocation();
    const activeTab = pathToTab(location.pathname);

    return (
        <>
            <BreadcrumbSegment>
                <Breadcrumb.Section active={true}>Profile Options</Breadcrumb.Section>
            </BreadcrumbSegment>

            <Grid centered={true}>
                <Grid.Column width={3}>
                    <Menu tabular={true} vertical={true} fluid={true}>
                        <Menu.Item active={activeTab === 'user-info'}>
                            <Link to="/profile/user-info">User</Link>
                        </Menu.Item>
                        <Menu.Item active={activeTab === 'token'}>
                            <Link to="/profile/api-token">API Tokens</Link>
                        </Menu.Item>
                    </Menu>
                </Grid.Column>

                <Grid.Column width={13}>
                    <Routes>
                        <Route index={true} element={<Navigate to="api-token" replace={true} />} />
                        <Route path="api-token/_new" element={<NewAPITokenPage />} />
                        <Route path="api-token" element={<APITokensListPage />} />
                        <Route path="user-info" element={<UserInfoPage />} />
                        <Route path="*" element={<NotFoundPage />} />
                    </Routes>
                </Grid.Column>
            </Grid>
        </>
    );
};

export default ProfilePage;
