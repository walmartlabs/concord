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
import { Button, Header, Icon, Menu, Popup, Radio, Sticky, Transition } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { RequestErrorMessage } from '../../molecules';

import './styles.css';

interface State {
    refreshStuck: boolean;
    scrollAnchorRef: boolean;
    useLocalTime: boolean;
    showDate: boolean;
}

interface Props {
    instanceId: ConcordId;

    loading: boolean;
    status: ProcessStatus | null;
    data: string[];
    error: RequestError;
    completed: boolean;

    startPolling: (useLocalTime: boolean, showDate: boolean) => void;
    stopPolling: () => void;
    loadWholeLog: (useLocalTime: boolean, showDate: boolean) => void;
    refresh: () => void;
}

interface LogContainerProps {
    data: string[];
}

class LogContainer extends React.PureComponent<LogContainerProps> {
    render() {
        const { data } = this.props;

        return (
            <>
                {data.map((value, idx) => (
                    <pre className="logEntry" key={idx}>
                        <div dangerouslySetInnerHTML={{ __html: value }} />
                    </pre>
                ))}
            </>
        );
    }
}

class ProcessLogViewer extends React.Component<Props, State> {
    private stickyRef: any;
    private scrollAnchorRef: any;

    constructor(props: Props) {
        super(props);
        this.state = {
            refreshStuck: false,
            scrollAnchorRef: false,
            useLocalTime: true,
            showDate: false
        };
        this.handleScroll = this.handleScroll.bind(this);
        this.scrollToBottom = this.scrollToBottom.bind(this);
        this.renderToolbar = this.renderToolbar.bind(this);
    }

    componentDidMount() {
        this.props.startPolling(true, false);
    }

    componentWillUnmount() {
        this.props.stopPolling();
    }

    componentDidUpdate(prevProps: Props) {
        const { instanceId, startPolling, stopPolling, data } = this.props;
        const { scrollAnchorRef, useLocalTime, showDate } = this.state;

        if (prevProps.data !== data && scrollAnchorRef) {
            this.scrollToBottom();
        }

        if (instanceId !== prevProps.instanceId) {
            stopPolling();
            startPolling(useLocalTime, showDate);
        }
    }

    handleScroll(ev: any, { checked }: any) {
        this.setState({
            scrollAnchorRef: checked!
        });

        if (checked === true) {
            this.scrollToBottom();
        }
    }

    handleUseLocalTimeChange(newValue: boolean) {
        const { startPolling, stopPolling } = this.props;
        const { showDate } = this.state;

        stopPolling();
        startPolling(newValue, showDate);

        this.setState({ useLocalTime: newValue });
    }

    handleShowDate(newValue: boolean) {
        const { startPolling, stopPolling } = this.props;
        const { useLocalTime } = this.state;

        stopPolling();
        startPolling(useLocalTime, newValue);

        this.setState({ showDate: newValue });
    }

    scrollToBottom() {
        this.scrollAnchorRef.scrollIntoView({ behavior: 'instant' });
    }

    renderToolbar() {
        const { loading, refresh, status, completed, loadWholeLog, instanceId } = this.props;
        const { useLocalTime, showDate, scrollAnchorRef } = this.state;

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
                    <Radio
                        label="Auto-Scroll"
                        toggle={true}
                        checked={scrollAnchorRef}
                        onChange={this.handleScroll}
                        style={{ paddingLeft: 20, paddingRight: 20 }}
                    />
                    <Button.Group>
                        <Popup
                            trigger={<Button>Time Format</Button>}
                            flowing={true}
                            hoverable={true}
                            position={'bottom center'}>
                            <div>
                                <Radio
                                    label="Use local time"
                                    toggle={true}
                                    checked={useLocalTime}
                                    onChange={(ev, data) =>
                                        this.handleUseLocalTimeChange(data.checked as boolean)
                                    }
                                />
                            </div>
                            <div style={{ marginTop: 10 }}>
                                <Radio
                                    label="Show date"
                                    toggle={true}
                                    checked={showDate}
                                    onChange={(ev, data) =>
                                        this.handleShowDate(data.checked as boolean)
                                    }
                                />
                            </div>
                        </Popup>
                        {status && !completed && (
                            <Button
                                disabled={loading}
                                onClick={() => loadWholeLog(useLocalTime, showDate)}>
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

                <LogContainer data={data} />

                <div
                    ref={(scroll) => {
                        this.scrollAnchorRef = scroll;
                    }}
                />
                <Transition animation="fade up" duration={550} visible={window.scrollY > 164}>
                    <div className="scrollToTopButton" onClick={() => window.scrollTo({ top: 0 })}>
                        <Icon name="chevron circle up" size="huge" />
                    </div>
                </Transition>
            </div>
        );
    }
}

export default ProcessLogViewer;
