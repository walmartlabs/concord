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
import { Link } from 'react-router-dom';
import { Breadcrumb, Grid, Menu } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';
import { Redirect, Route, RouteComponentProps, Switch, withRouter } from 'react-router';
import { NotFoundPage } from '../index';
import { APITokensListPage, NewAPITokenPage, UserInfoPage } from '../../../components/pages';

interface RouteProps {
    orgName: string;
}

type TabLink = 'user-info' |'token' | null;

const pathToTab = (s: string): TabLink => {
    if (s.includes('/api-token')) {
        return 'token';
    } else if (s.includes('/user-info')) {
        return 'user-info'
    }

    return null;
};

class ProfilePage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section active={true}>Profile Options</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Grid centered={true}>
                    <Grid.Column width={3}>
                        <Menu tabular={true} vertical={true} fluid={true}>
                            <Menu.Item active={activeTab === 'user-info'}>
                                <Link to={`${url}/user-info`}>User</Link>
                            </Menu.Item>
                            <Menu.Item active={activeTab === 'token'}>
                                <Link to={`${url}/api-token`}>API Tokens</Link>
                            </Menu.Item>
                        </Menu>
                    </Grid.Column>

                    <Grid.Column width={13}>
                        <Switch>
                            <Route path={url} exact={true}>
                                <Redirect to={`${url}/api-token`} />
                            </Route>
                            <Route path={`${url}/api-token/_new`}>
                                <NewAPITokenPage />
                            </Route>
                            <Route path={`${url}/api-token`}>
                                <APITokensListPage />
                            </Route>
                            <Route path={`${url}/user-info`}>
                                <UserInfoPage />
                            </Route>

                            <Route component={NotFoundPage} />
                        </Switch>
                    </Grid.Column>
                </Grid>
            </>
        );
    }
}

export default withRouter(ProfilePage);
