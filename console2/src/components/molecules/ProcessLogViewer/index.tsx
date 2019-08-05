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
import { Button, Divider, Icon, Loader, Popup, Radio, Transition } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessEntry } from '../../../api/process';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import { LogSegment, LogSegmentType, TagData } from '../../../state/data/processes/logs/types';
import { RequestErrorMessage } from '../../molecules';
import { TaskCallDetails } from '../../organisms';

import './styles.css';
import ProcessToolbar from '../ProcessToolbar';

interface State {
    refreshStuck: boolean;
    scrollAnchorRef: boolean;
    opts: LogProcessorOptions;
    expandedItems: ConcordId[];
}

interface Props {
    instanceId: ConcordId;

    loading: boolean;
    process: ProcessEntry | null;
    data: LogSegment[];
    error: RequestError;
    completed: boolean;

    startPolling: (opts: LogProcessorOptions) => void;
    stopPolling: () => void;
    loadWholeLog: (opts: LogProcessorOptions) => void;
    refresh: () => void;
}

interface LogContainerProps {
    instanceId: ConcordId;
    data: LogSegment[];
    onClick: (correlationId: ConcordId) => void;
    expandedItems: ConcordId[];
}

const renderTagHeader = (
    taskName: string,
    idx: number,
    expanded?: boolean,
    onClick?: () => void
) => (
    <Divider
        horizontal={true}
        key={idx}
        className={onClick ? 'clickableTagHeader' : undefined}
        onClick={onClick}>
        {taskName}
        {onClick && <Icon name={expanded ? 'chevron up' : 'chevron down'} />}
    </Divider>
);

const renderTag = (
    instanceId: ConcordId,
    tag: TagData,
    onClick: () => void,
    expanded: boolean,
    idx: number
) => {
    if (tag.phase === 'post') {
        return <Divider key={idx} />;
    }

    if (!tag.correlationId) {
        return renderTagHeader(tag.taskName, idx);
    }

    return (
        <div key={idx} className="logTagDetails">
            {renderTagHeader(tag.taskName, idx, expanded, onClick)}
            {expanded && (
                <TaskCallDetails instanceId={instanceId} correlationId={tag.correlationId} />
            )}
        </div>
    );
};

const LogContainer = ({ instanceId, data, onClick, expandedItems }: LogContainerProps) => (
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
                    const tag = data as TagData;
                    const expanded = !!expandedItems.find((i) => i === tag.correlationId);
                    return renderTag(
                        instanceId,
                        tag,
                        () => onClick(tag.correlationId),
                        expanded,
                        idx
                    );
                }
                default: {
                    return `Unknown log segment type: ${type}`;
                }
            }
        })}
    </>
);

const DEFAULT_OPTS: LogProcessorOptions = {
    useLocalTime: true,
    showDate: false,
    separateTasks: true
};

const getStoredOpts = (): LogProcessorOptions => {
    const data = localStorage.getItem('logViewerOpts');
    if (!data) {
        return DEFAULT_OPTS;
    }

    return JSON.parse(data);
};

const storeOpts = (opts: LogProcessorOptions) => {
    const data = JSON.stringify(opts);
    localStorage.setItem('logViewerOpts', data);
};

class ProcessLogViewer extends React.Component<Props, State> {
    private stickyRef: any;
    private scrollAnchorRef: any;

    constructor(props: Props) {
        super(props);

        this.state = {
            refreshStuck: false,
            scrollAnchorRef: false,
            opts: getStoredOpts(),
            expandedItems: []
        };

        this.handleScroll = this.handleScroll.bind(this);
        this.scrollToBottom = this.scrollToBottom.bind(this);
        this.handleTagClick = this.handleTagClick.bind(this);

        this.stickyRef = React.createRef();
    }

    componentDidMount() {
        this.props.startPolling(getStoredOpts());
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
        storeOpts(newOpts);
    }

    handleTagClick(correlationId: ConcordId) {
        let { expandedItems } = this.state;

        const i = expandedItems.findIndex((i) => i === correlationId);
        if (i < 0) {
            expandedItems.push(correlationId);
        } else {
            expandedItems.splice(i, 1);
        }

        this.setState({ expandedItems });
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

    createLogToolbarActions() {
        const { loading, completed, loadWholeLog, instanceId, process } = this.props;
        const { opts, scrollAnchorRef } = this.state;

        return (
            <>
                <Radio
                    label="Auto-Scroll"
                    toggle={true}
                    checked={scrollAnchorRef}
                    onChange={this.handleScroll}
                    style={{ paddingRight: 20 }}
                />

                {this.renderSettingsMenu(opts)}

                <Button.Group>
                    {process!.status && !completed && (
                        <Button disabled={loading} onClick={() => loadWholeLog(opts)}>
                            Show the whole log
                        </Button>
                    )}
                    <Button
                        onClick={() => window.open(`/api/v1/process/${instanceId}/log`, '_blank')}>
                        Raw
                    </Button>
                </Button.Group>
            </>
        );
    }

    render() {
        const { error, instanceId, data, loading, refresh, process } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (!process) {
            return <Loader active={loading} />;
        }

        const { expandedItems } = this.state;

        return (
            <div ref={this.stickyRef}>
                <ProcessToolbar
                    stickyRef={this.stickyRef}
                    loading={loading}
                    refresh={refresh}
                    process={process}
                    additionalActions={this.createLogToolbarActions()}
                />

                <LogContainer
                    instanceId={instanceId}
                    data={data}
                    expandedItems={expandedItems}
                    onClick={this.handleTagClick}
                />

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
