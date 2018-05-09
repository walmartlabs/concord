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
import { Button, Header, Icon, Menu, Sticky } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { RequestErrorMessage } from '../../molecules';

import './styles.css';

interface State {
    refreshStuck: boolean;
}

interface Props {
    instanceId: ConcordId;

    loading: boolean;
    status: ProcessStatus | null;
    data: string[];
    error: RequestError;
    completed: boolean;

    startPolling: () => void;
    stopPolling: () => void;
    loadWholeLog: () => void;
    refresh: () => void;
}

class ProcessLogViewer extends React.Component<Props, State> {
    private stickyRef: any;

    constructor(props: Props) {
        super(props);
        this.state = { refreshStuck: false };
    }

    componentDidMount() {
        this.props.startPolling();
    }

    componentWillUnmount() {
        this.props.stopPolling();
    }

    componentDidUpdate(prevProps: Props) {
        const { instanceId, startPolling, stopPolling } = this.props;
        if (instanceId !== prevProps.instanceId) {
            stopPolling();
            startPolling();
        }
    }

    renderToolbar() {
        const { loading, refresh, status, completed, loadWholeLog, instanceId } = this.props;

        return (
            <Menu borderless={true} secondary={!this.state.refreshStuck}>
                <Menu.Item>
                    <Header as="h3">
                        <Icon
                            disabled={loading}
                            name="refresh"
                            loading={loading}
                            onClick={() => refresh()}
                        />
                        {status}
                    </Header>
                </Menu.Item>
                <Menu.Item position="right">
                    <Button.Group>
                        {status &&
                            !completed && (
                                <Button disabled={loading} onClick={() => loadWholeLog()}>
                                    Show the whole log
                                </Button>
                            )}
                        <Button
                            onClick={() =>
                                window.open(`/api/v1/process/${instanceId}/log`, '_blank')
                            }>
                            Raw
                        </Button>
                    </Button.Group>
                </Menu.Item>
            </Menu>
        );
    }

    render() {
        const { error, data } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        return (
            <div ref={(r) => (this.stickyRef = r)}>
                <Sticky
                    context={this.stickyRef}
                    offset={10}
                    onStick={() => this.setState({ refreshStuck: true })}
                    onUnstick={() => this.setState({ refreshStuck: false })}>
                    {this.renderToolbar()}
                </Sticky>

                {data.map((value, idx) => (
                    <pre key={idx} className="logEntry">
                        {value}
                    </pre>
                ))}
            </div>
        );
    }
}

export default ProcessLogViewer;
