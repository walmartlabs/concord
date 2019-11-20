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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Link, Redirect, Route, Switch } from 'react-router-dom';
import { Icon, Loader, Menu, Breadcrumb } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessEntry } from '../../../api/process';
import { actions, State } from '../../../state/data/processes';
import { BreadcrumbSegment, RequestErrorMessage, WithCopyToClipboard } from '../../molecules';
import { NotFoundPage } from '../../pages';
import {
    ProcessLogActivity,
    ProcessStatusActivity,
    ProcessHistoryActivity,
    ProcessChildrenActivity,
    ProcessAttachmentsActivity,
    ProcessEventsActivity,
    ProcessWaitActivity,
    ProcessAnsibleActivitySwitcher
} from '../index';

export type TabLink =
    | 'status'
    | 'ansible'
    | 'log'
    | 'events'
    | 'history'
    | 'wait'
    | 'children'
    | 'attachments'
    | null;

interface ExternalProps {
    instanceId: ConcordId;
    activeTab: TabLink;
}

interface StateProps {
    data?: ProcessEntry;
    error: RequestError;
    loading: boolean;
}

interface DispatchProps {
    load: (instanceId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prevProps: Props) {
        if (this.props.instanceId !== prevProps.instanceId) {
            this.init();
        }
    }

    init() {
        const { instanceId, load } = this.props;
        load(instanceId);
    }

    renderBreadcrumbs() {
        const { data } = this.props;

        if (!data) {
            return;
        }

        if (!data.orgName) {
            return (
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/process`}>Processes</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>
                        <WithCopyToClipboard value={data.instanceId}>
                            {data.instanceId}
                        </WithCopyToClipboard>
                    </Breadcrumb.Section>
                </BreadcrumbSegment>
            );
        }

        return (
            <BreadcrumbSegment>
                <Breadcrumb.Section>
                    <Link to={`/org/${data.orgName}`}>{data.orgName}</Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section>
                    <Link to={`/org/${data.orgName}/project/${data.projectName}`}>
                        {data.projectName}
                    </Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section active={true}>
                    <WithCopyToClipboard value={data.instanceId}>
                        {data.instanceId}
                    </WithCopyToClipboard>
                </Breadcrumb.Section>
            </BreadcrumbSegment>
        );
    }

    render() {
        const { loading, error, data } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !data) {
            return <Loader active={true} />;
        }

        const { instanceId, activeTab } = this.props;

        const baseUrl = `/process/${instanceId}`;

        return (
            <>
                {this.renderBreadcrumbs()}

                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'status'}>
                        <Icon name="hourglass half" />
                        <Link to={`${baseUrl}/status`}>Status</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'ansible'}>
                        <Icon name="chart area" />
                        <Link to={`${baseUrl}/ansible`}>Ansible</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'events'}>
                        <Icon name="content" />
                        <Link to={`${baseUrl}/events`}>Events</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'log'}>
                        <Icon name="book" />
                        <Link to={`${baseUrl}/log`}>Logs</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'history'}>
                        <Icon name="history" />
                        <Link to={`${baseUrl}/history`}>History</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'wait'}>
                        <Icon name="wait" />
                        <Link to={`${baseUrl}/wait`}>Wait Conditions</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'children'}>
                        <Icon name="chain" />
                        <Link to={`${baseUrl}/children`}>Child Processes</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'attachments'}>
                        <Icon name="paperclip" />
                        <Link to={`${baseUrl}/attachments`}>Attachments</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={baseUrl} exact={true}>
                        <Redirect to={`${baseUrl}/status`} />
                    </Route>
                    <Route path={`${baseUrl}/status`}>
                        <ProcessStatusActivity process={data} />
                    </Route>
                    <Route path={`${baseUrl}/ansible`}>
                        <ProcessAnsibleActivitySwitcher process={data} />
                    </Route>
                    <Route path={`${baseUrl}/events`}>
                        <ProcessEventsActivity process={data} />
                    </Route>
                    <Route path={`${baseUrl}/log`} exact={true}>
                        <ProcessLogActivity instanceId={instanceId} />
                    </Route>
                    <Route path={`${baseUrl}/history`} exact={true}>
                        <ProcessHistoryActivity process={data} />
                    </Route>
                    <Route path={`${baseUrl}/wait`} exact={true}>
                        <ProcessWaitActivity process={data} />
                    </Route>
                    <Route path={`${baseUrl}/children`} exact={true}>
                        <ProcessChildrenActivity instanceId={instanceId} />
                    </Route>
                    <Route path={`${baseUrl}/attachments`} exact={true}>
                        <ProcessAttachmentsActivity process={data} />
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

const mapStateToProps = (
    { processes }: { processes: State },
    { instanceId }: ExternalProps
): StateProps => ({
    data: processes.processesById[instanceId],
    loading: processes.loading,
    error: processes.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (instanceId) => dispatch(actions.getProcess(instanceId, ['history', 'checkpoints']))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessActivity);
