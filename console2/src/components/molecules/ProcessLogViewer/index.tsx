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
import {
    Button,
    Divider,
    Header,
    Icon,
    Menu,
    Popup,
    Radio,
    Sticky,
    Transition
} from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import { LogSegment, LogSegmentType, TagData } from '../../../state/data/processes/logs/types';
import { RequestErrorMessage } from '../../molecules';

import './styles.css';

interface State {
    refreshStuck: boolean;
    scrollAnchorRef: boolean;
    opts: LogProcessorOptions;
}

interface Props {
    instanceId: ConcordId;

    loading: boolean;
    status: ProcessStatus | null;
    data: LogSegment[];
    error: RequestError;
    completed: boolean;

    startPolling: (opts: LogProcessorOptions) => void;
    stopPolling: () => void;
    loadWholeLog: (opts: LogProcessorOptions) => void;
    refresh: () => void;
}

interface LogContainerProps {
    data: LogSegment[];
}

const renderTag = (tag: TagData, idx: number) => {
    if (tag.phase === 'post') {
        return <Divider key={idx} />;
    }

    return (
        <Divider horizontal={true} key={idx}>
            {tag.taskName}
        </Divider>
    );
};

class LogContainer extends React.PureComponent<LogContainerProps> {
    render() {
        const { data } = this.props;

        return (
            <>
                {data.map(({ data, type }, idx) => {
                    switch (type) {
                        case LogSegmentType.DATA: {
                            return (
                                <pre className="logEntry" key={idx}>
                                    <div dangerouslySetInnerHTML={{ __html: data as string }} />
                                </pre>
                            );
                        }
                        case LogSegmentType.TAG: {
                            return renderTag(data as TagData, idx);
                        }
                        default: {
                            return `Unknown log segment type: ${type}`;
                        }
                    }
                })}
            </>
        );
    }
}

const DEFAULT_OPTS: LogProcessorOptions = {
    useLocalTime: true,
    showDate: false,
    separateTasks: true
};

class ProcessLogViewer extends React.Component<Props, State> {
    private stickyRef: any;
    private scrollAnchorRef: any;

    constructor(props: Props) {
        super(props);
        this.state = {
            refreshStuck: false,
            scrollAnchorRef: false,
            opts: { ...DEFAULT_OPTS }
        };

        this.handleScroll = this.handleScroll.bind(this);
        this.scrollToBottom = this.scrollToBottom.bind(this);
        this.renderToolbar = this.renderToolbar.bind(this);
    }

    componentDidMount() {
        this.props.startPolling({ ...DEFAULT_OPTS });
    }

    componentWillUnmount() {
        this.props.stopPolling();
    }

    componentDidUpdate(prevProps: Props) {
        const { instanceId, startPolling, stopPolling, data } = this.props;
        const { scrollAnchorRef, opts } = this.state;

        if (prevProps.data !== data && scrollAnchorRef) {
            this.scrollToBottom();
        }

        if (instanceId !== prevProps.instanceId) {
            stopPolling();
            startPolling(opts);
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

    handleOptionsChange(k: keyof LogProcessorOptions, v: boolean) {
        const { startPolling, stopPolling } = this.props;
        const { opts } = this.state;

        const newOpts = { ...opts, [k]: v };

        stopPolling();
        startPolling(newOpts);

        this.setState({ opts: newOpts });
    }

    scrollToBottom() {
        this.scrollAnchorRef.scrollIntoView({ behavior: 'instant' });
    }

    renderSettingsMenu(opts: LogProcessorOptions) {
        return (
            <Popup
                size="huge"
                position="bottom left"
                trigger={<Button basic={true} icon="setting" style={{ marginRight: 20 }} />}
                on="click">
                <div>
                    <Radio
                        label="Separate tasks"
                        toggle={true}
                        checked={opts.separateTasks}
                        onChange={(ev, data) =>
                            this.handleOptionsChange('separateTasks', data.checked as boolean)
                        }
                    />
                </div>

                <Divider horizontal={true}>Timestamps</Divider>

                <div>
                    <Radio
                        label="Use local time"
                        toggle={true}
                        checked={opts.useLocalTime}
                        onChange={(ev, data) =>
                            this.handleOptionsChange('useLocalTime', data.checked as boolean)
                        }
                    />
                </div>

                <div>
                    <Radio
                        label="Show date"
                        toggle={true}
                        checked={opts.showDate}
                        onChange={(ev, data) =>
                            this.handleOptionsChange('showDate', data.checked as boolean)
                        }
                    />
                </div>
            </Popup>
        );
    }

    renderToolbar() {
        const { loading, refresh, status, completed, loadWholeLog, instanceId } = this.props;
        const { opts, scrollAnchorRef } = this.state;

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
                        style={{ paddingRight: 20 }}
                    />

                    {this.renderSettingsMenu(opts)}

                    <Button.Group>
                        {status && !completed && (
                            <Button disabled={loading} onClick={() => loadWholeLog(opts)}>
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
