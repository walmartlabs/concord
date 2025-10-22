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
import { Button, Divider, Icon, Popup, Radio, Transition } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import { LogSegment, LogSegmentType, TagData } from '../../../state/data/processes/logs/types';
import { ProcessToolbar } from '../../molecules';
import { TaskCallDetails } from '../../organisms';

import './styles.css';

interface State {
    scrollAnchorRef: boolean;
    anchorTagRefScrolled: boolean;
    opts: LogProcessorOptions;
    expandedItems: ConcordId[];
}

interface Props {
    instanceId: ConcordId;

    processStatus?: ProcessStatus;
    data: LogSegment[];
    completed: boolean;
    opts: LogProcessorOptions;
    selectedCorrelationId?: string;

    optsHandler: (opts: LogProcessorOptions) => void;

    loadWholeLog: (opts: LogProcessorOptions) => void;
}

interface LogContainerProps {
    instanceId: ConcordId;
    data: LogSegment[];
    onClick: (correlationId: ConcordId) => void;
    expandedItems: ConcordId[];
    tagRefs: any[];
}

const renderTagHeader = (
    taskName: string,
    tagRefs: any[],
    idx: number,
    expanded?: boolean,
    onClick?: () => void,
    correlationId?: ConcordId
) => (
    <>
        <div
            ref={
                correlationId
                    ? (element) => {
                          tagRefs[correlationId] = element;
                      }
                    : undefined
            }
        />

        <Divider
            horizontal={true}
            key={idx}
            className={onClick ? 'clickableTagHeader' : undefined}
            onClick={onClick}>
            {taskName}
            {onClick && <Icon name={expanded ? 'chevron up' : 'chevron down'} />}
        </Divider>
    </>
);

const renderTag = (
    instanceId: ConcordId,
    tag: TagData,
    tagRefs: any[],
    onClick: () => void,
    expanded: boolean,
    idx: number
) => {
    if (tag.phase === 'post') {
        return <Divider key={idx} />;
    }

    if (!tag.correlationId) {
        return renderTagHeader(tag.taskName, tagRefs, idx);
    }

    return (
        <div key={idx} className="logTagDetails">
            {renderTagHeader(tag.taskName, tagRefs, idx, expanded, onClick, tag.correlationId)}
            {expanded && (
                <TaskCallDetails instanceId={instanceId} correlationId={tag.correlationId} />
            )}
        </div>
    );
};

const LogContainer = ({ instanceId, data, tagRefs, onClick, expandedItems }: LogContainerProps) => (
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
                        tagRefs,
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

class ProcessLogViewer extends React.Component<Props, State> {
    private scrollAnchorRef: any;
    private tagRefs: any[];

    constructor(props: Props) {
        super(props);

        this.tagRefs = [];

        this.state = {
            scrollAnchorRef: false,
            anchorTagRefScrolled: false,
            opts: props.opts,
            expandedItems: []
        };

        this.handleScroll = this.handleScroll.bind(this);
        this.scrollToBottom = this.scrollToBottom.bind(this);
        this.handleTagClick = this.handleTagClick.bind(this);
        this.scrollToTag = this.scrollToTag.bind(this);
    }

    componentDidUpdate(prevProps: Props) {
        const { data, selectedCorrelationId } = this.props;
        const { scrollAnchorRef } = this.state;

        if (prevProps.data !== data) {
            if (scrollAnchorRef) {
                this.scrollToBottom();
            }

            if (prevProps.selectedCorrelationId !== selectedCorrelationId) {
                this.setState({ anchorTagRefScrolled: selectedCorrelationId === '' });
            }

            this.scrollToTag();
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
        const { optsHandler } = this.props;
        const { opts } = this.state;

        const newOpts = { ...opts, [k]: v };

        optsHandler(newOpts);

        this.setState({ opts: newOpts });
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

    scrollToTag() {
        const { selectedCorrelationId } = this.props;
        const { anchorTagRefScrolled } = this.state;

        if (!anchorTagRefScrolled && selectedCorrelationId && this.tagRefs[selectedCorrelationId]) {
            this.tagRefs[selectedCorrelationId].scrollIntoView({ behavior: 'instant' });
            this.setState({ anchorTagRefScrolled: true });
        }
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
        const { completed, loadWholeLog, instanceId, processStatus } = this.props;
        const { opts, scrollAnchorRef } = this.state;

        return (
            <>
                <Radio
                    label="Auto-Scroll"
                    toggle={true}
                    checked={scrollAnchorRef}
                    disabled={processStatus === undefined}
                    onChange={this.handleScroll}
                    style={{ paddingRight: 20 }}
                />

                {this.renderSettingsMenu(opts)}

                <Button.Group>
                    {processStatus && !completed && (
                        <Button onClick={() => loadWholeLog(opts)}>Show the whole log</Button>
                    )}
                    <Button
                        disabled={!instanceId}
                        onClick={() => window.open(`/api/v1/process/${instanceId}/log`, '_blank')}>
                        Raw
                    </Button>
                </Button.Group>
            </>
        );
    }

    render() {
        const { instanceId, data } = this.props;

        const { expandedItems } = this.state;

        return (
            <>
                <ProcessToolbar>{this.createLogToolbarActions()}</ProcessToolbar>

                <LogContainer
                    instanceId={instanceId}
                    data={data}
                    expandedItems={expandedItems}
                    tagRefs={this.tagRefs}
                    onClick={this.handleTagClick}
                />

                <div
                    ref={(scroll) => {
                        this.scrollAnchorRef = scroll;
                    }}
                />
                <Transition animation="fade up" duration={550}>
                    <div
                        className="scrollToTopButton"
                        onClick={() => {
                            window.scrollTo({ top: 0 });
                            this.setState({
                                scrollAnchorRef: false
                            });
                        }}>
                        <Icon name="chevron circle up" size="huge" />
                    </div>
                </Transition>
            </>
        );
    }
}

export default ProcessLogViewer;
