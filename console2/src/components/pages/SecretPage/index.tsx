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

import { ConcordKey } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { SecretInfo } from '../../organisms';
import { NotFoundPage } from '../index';

interface RouteProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

type TabLink = 'info' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/info')) {
        return 'info';
    }

    return null;
};

class SecretPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, secretName } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}/secret`}>{orgName}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{secretName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Route path={`${url}/^(repository)`}>
                    <Menu tabular={true}>
                        <Menu.Item active={activeTab === 'info'}>
                            <Icon name="file" />
                            <Link to={`/org/${orgName}/secret/${secretName}/info`}>Info</Link>
                        </Menu.Item>
                    </Menu>
                </Route>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/info`} />
                    </Route>

                    <Route path={`${url}/info`} exact={true}>
                        <SecretInfo orgName={orgName} secretName={secretName} />
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

export default withRouter(SecretPage);
