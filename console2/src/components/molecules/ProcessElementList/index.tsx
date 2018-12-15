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
import { Icon, Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { ProcessElementEvent, ProcessEventEntry } from '../../../api/process/event';
import { formatTimestamp } from '../../../utils';
import { HumanizedDuration } from '../../molecules';
import { ProcessRestoreActivity } from '../../organisms';

interface Props {
    instanceId: ConcordId;
    processStatus: ProcessStatus;
    events: Array<ProcessEventEntry<ProcessElementEvent>>;
    tooMuchData?: boolean;
}

const renderDefinitionId = (
    { data: { processDefinitionId } }: ProcessEventEntry<ProcessElementEvent>,
    idx: number,
    arr: Array<ProcessEventEntry<ProcessElementEvent>>
) => {
    if (idx !== 0 && arr[idx - 1].data.processDefinitionId === processDefinitionId) {
        return;
    }

    return processDefinitionId;
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

const renderElementRow = (
    instanceId: ConcordId,
    processStatus: ProcessStatus,
    ev: ProcessEventEntry<ProcessElementEvent>,
    idx: number,
    arr: Array<ProcessEventEntry<ProcessElementEvent>>
) => {
    return (
        <Table.Row key={idx}>
            <Table.Cell textAlign="right">{renderDefinitionId(ev, idx, arr)}</Table.Cell>
            <Table.Cell verticalAlign={'middle'}>
                {ev.data.description}
                {renderRestoreCheckpoint(instanceId, processStatus, ev.data.out)}
            </Table.Cell>
            <Table.Cell singleLine={true}>{renderTimestamp(ev, idx, arr)}</Table.Cell>
            <Table.Cell singleLine={true}>
                <HumanizedDuration value={ev.duration} />
            </Table.Cell>
            <Table.Cell>{ev.data.line}</Table.Cell>
            <Table.Cell>{ev.data.column}</Table.Cell>
        </Table.Row>
    );
};

class ProcessElementList extends React.PureComponent<Props> {
    render() {
        const { instanceId, processStatus, events, tooMuchData } = this.props;
        return (
            <Table celled={true} definition={true}>
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
                    {events.map((e, idx, arr) =>
                        renderElementRow(instanceId, processStatus, e, idx, arr)
                    )}
                </Table.Body>

                {tooMuchData && (
                    <Table.Footer fullWidth={true}>
                        <Table.Row>
                            <Table.HeaderCell colSpan={6}>
                                <Icon name="warning sign" /> <strong>Partial data</strong>
                            </Table.HeaderCell>
                        </Table.Row>
                    </Table.Footer>
                )}
            </Table>
        );
    }
}

export default ProcessElementList;
