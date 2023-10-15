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
import { Icon, Popup, Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { ProcessElementEvent, ProcessEventEntry } from '../../../api/process/event';
import { formatTimestamp } from '../../../utils';
import { HumanizedDuration } from '../../molecules';
import { ProcessRestoreActivity } from '../../organisms';

interface Props {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    events?: ProcessEventEntry<ProcessElementEvent>[];
    definitionLinkBase?: string;
}

const renderDefinitionId = (
    { data: { processDefinitionId, fileName } }: ProcessEventEntry<ProcessElementEvent>,
    idx: number,
    arr: Array<ProcessEventEntry<ProcessElementEvent>>
) => {
    if (idx !== 0 && arr[idx - 1].data.processDefinitionId === processDefinitionId) {
        return;
    }

    if (fileName === undefined) {
        return processDefinitionId;
    }

    return <Popup content={fileName} trigger={<span>{processDefinitionId}</span>} hoverable={true}/>;
};

const renderTimestamp = (
    { eventDate }: ProcessEventEntry<{}>,
    idx: number,
    arr: Array<ProcessEventEntry<{}>>
) => {
    const s = formatTimestamp(eventDate);

    if (idx !== 0 && formatTimestamp(arr[idx - 1].eventDate) === s) {
        return '(same)';
    }

    return s;
};

const getParam = (p: {} | undefined, name: string, index: number) => {
    if (p === undefined) {
        return undefined;
    }
    if (p instanceof Array && p[index]) {
        const { target, resolved } = p[index];
        if (target === name) {
            return resolved;
        }
    }
    return undefined;
};

const renderRestoreCheckpoint = (
    instanceId: ConcordId,
    processStatus: ProcessStatus,
    outParams: {} | undefined
) => {
    const id = getParam(outParams, 'checkpointId', 0);
    if (id === undefined) {
        return;
    }
    const checkpoint = getParam(outParams, 'checkpointName', 1);
    if (checkpoint === undefined) {
        return;
    }

    return (
        <ProcessRestoreActivity
            instanceId={instanceId}
            checkpointId={id}
            checkpoint={checkpoint}
            processStatus={processStatus}
        />
    );
};

const definitionFileName = (data: ProcessElementEvent): string => {
    const { processDefinitionId, fileName } = data;
    if (fileName === undefined) {
        return processDefinitionId;
    }
    return fileName;
};

const definitionLink = (data: ProcessElementEvent, definitionLinkBase?: string) => {
    if (!definitionLinkBase) {
        return data.line;
    }

    const { processDefinitionId, fileName } = data;
    if (!processDefinitionId && !fileName) {
        return data.line;
    }

    return (
        <a
            href={definitionLinkBase + '/' + definitionFileName(data) + '#L' + data.line}
            target="_blank"
            rel="noopener noreferrer">
            {data.line}
        </a>
    );
};

const renderElementRow = (
    instanceId: ConcordId,
    processStatus: ProcessStatus,
    ev: ProcessEventEntry<ProcessElementEvent>,
    idx: number,
    arr: Array<ProcessEventEntry<ProcessElementEvent>>,
    definitionLinkBase?: string
) => {
    return (
        <Table.Row key={idx}>
            <Table.Cell textAlign="right">{renderDefinitionId(ev, idx, arr)}</Table.Cell>
            <Table.Cell
                verticalAlign={'middle'}
                style={{ wordBreak: 'break-all' }}
                className={ev.data.error !== undefined ? 'error' : ''}>
                {ev.data.description}
                {renderRestoreCheckpoint(instanceId, processStatus, ev.data.out)}
            </Table.Cell>
            <Table.Cell singleLine={true}>{renderTimestamp(ev, idx, arr)}</Table.Cell>
            <Table.Cell singleLine={true}>
                <HumanizedDuration value={ev.data.duration} />
            </Table.Cell>
            <Table.Cell>{definitionLink(ev.data, definitionLinkBase)}</Table.Cell>
            <Table.Cell>{ev.data.column}</Table.Cell>
        </Table.Row>
    );
};

const renderElements = (
    instanceId: ConcordId,
    processStatus?: ProcessStatus,
    events?: ProcessEventEntry<ProcessElementEvent>[],
    definitionLinkBase?: string
) => {
    if (!events || !processStatus) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell> </Table.Cell>
                <Table.Cell colSpan={5}>-</Table.Cell>
            </tr>
        );
    }

    if (events.length === 0) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell> </Table.Cell>
                <Table.Cell colSpan={5}>No data available</Table.Cell>
            </tr>
        );
    }

    return events.map((e, idx, arr) =>
        renderElementRow(instanceId, processStatus, e, idx, arr, definitionLinkBase)
    );
};

class ProcessElementList extends React.PureComponent<Props> {
    render() {
        const { instanceId, processStatus, events, definitionLinkBase } = this.props;
        return (
            <Table celled={true} definition={true} className={events ? '' : 'loading'}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell width={1} />
                        <Table.HeaderCell>Step</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true} singleLine={true}>
                            <Icon name="time" />
                            Timestamp
                        </Table.HeaderCell>
                        <Table.HeaderCell collapsing={true} singleLine={true}>
                            Duration
                        </Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Line</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Col</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>

                <Table.Body>
                    {renderElements(instanceId, processStatus, events, definitionLinkBase)}
                </Table.Body>
            </Table>
        );
    }
}

export default ProcessElementList;
