/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import { ConcordId } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { ProcessLogActivity, ProcessStatusActivity } from '../../organisms';
import { NotFoundPage } from '../index';

interface Props {
    instanceId: ConcordId;
}

type TabLink = 'status' | 'log' | 'events' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/status')) {
        return 'status';
    } else if (s.endsWith('/log')) {
        return 'log';
    }

    return null;
};

class ProcessPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { instanceId } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/process`}>Processes</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{instanceId}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'status'}>
                        <Icon name="hourglass half" />
                        <Link to={`${url}/status`}>Status</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'log'}>
                        <Icon name="book" />
                        <Link to={`${url}/log`}>Logs</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/status`} />
                    </Route>
                    <Route path={`${url}/status`}>
                        <ProcessStatusActivity instanceId={instanceId} />
                    </Route>
                    <Route path={`${url}/log`} exact={true}>
                        <ProcessLogActivity instanceId={instanceId} />
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

export default withRouter(ProcessPage);
