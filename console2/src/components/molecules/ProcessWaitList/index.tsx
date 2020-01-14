/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
    ProcessLockPayload,
    ProcessSleepPayload,
    ProcessWaitHistoryEntry,
    ProcessWaitPayload,
    WaitPayload,
    WaitType
} from '../../../api/process';
import { Accordion, Icon, Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';
import { Link } from 'react-router-dom';
import { LocalTimestamp } from '../index';

interface ExternalProps {
    data?: ProcessWaitHistoryEntry[];
}

const ProcessWaitList = ({ data }: ExternalProps) => {
    return (
        <Table celled={true} className={data ? '' : 'loading'}>
            <Table.Header>{renderTableHeader()}</Table.Header>
            <Table.Body>{renderElements(data)}</Table.Body>
        </Table>
    );
};

const renderTableHeader = () => {
    return (
        <Table.Row>
            <Table.HeaderCell collapsing={true}>Date</Table.HeaderCell>
            <Table.HeaderCell collapsing={true}>Condition</Table.HeaderCell>
            <Table.HeaderCell>Dependencies</Table.HeaderCell>
        </Table.Row>
    );
};

const renderElements = (data?: ProcessWaitHistoryEntry[]) => {
    if (!data) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={3}>&nbsp;</Table.Cell>
            </tr>
        );
    }

    if (data.length === 0) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={3}>No data available</Table.Cell>
            </tr>
        );
    }

    return data.map((p) => renderTableRow(p));
};

const renderProcessLink = (id: ConcordId) => {
    return (
        <p>
            <Link to={`/process/${id}`} key={id}>
                {id}
            </Link>
        </p>
    );
};

const renderCondition = ({ type, reason, payload }: ProcessWaitHistoryEntry) => {
    switch (type) {
        case WaitType.NONE: {
            return (
                <>
                    <Icon name="check" /> No wait conditions
                </>
            );
        }
        case WaitType.PROCESS_COMPLETION: {
            return (
                <>
                    <Icon name="hourglass half" />
                    Waiting for the process to complete
                    {reason && ` (${reason})`}
                </>
            );
        }
        case WaitType.PROCESS_LOCK: {
            const lockPayload = payload as ProcessLockPayload;
            return (
                <>
                    <Icon name="hourglass half" />
                    Waiting for the lock ({lockPayload.name})
                </>
            );
        }
        case WaitType.PROCESS_SLEEP: {
            const sleepPayload = payload as ProcessSleepPayload;
            return (
                <>
                    <Icon name="hourglass half" />
                    Waiting until ({<LocalTimestamp value={sleepPayload.until} />})
                </>
            );
        }
        default:
            return type;
    }
};

const renderProcessWaitDetails = (payload: ProcessWaitPayload) => {
    if (payload.processes.length === 0) {
        return <></>;
    } else if (payload.processes.length === 1) {
        return renderProcessLink(payload.processes[0]);
    }

    const panels = [
        {
            key: 'k1',
            title: {
                content: (
                    <Link to={`/process/${payload.processes[0]}`} key={payload.processes[0]}>
                        {payload.processes[0]}
                    </Link>
                ),
                style: { padding: 0 }
            },
            content: [payload.processes.slice(1).map((id) => renderProcessLink(id))]
        }
    ];
    return <Accordion panels={panels} />;
};

const renderProcessLockDetails = (payload: ProcessLockPayload) => {
    return renderProcessLink(payload.instanceId);
};

const renderDependencies = (type: WaitType, payload: WaitPayload) => {
    switch (type) {
        case WaitType.PROCESS_COMPLETION: {
            return renderProcessWaitDetails(payload as ProcessWaitPayload);
        }
        case WaitType.PROCESS_LOCK: {
            return renderProcessLockDetails(payload as ProcessLockPayload);
        }
        default:
            return '';
    }
};

const renderTableRow = (row: ProcessWaitHistoryEntry) => {
    return (
        <Table.Row key={row.id} verticalAlign="top">
            <Table.Cell singleLine={true}>
                <LocalTimestamp value={row.eventDate} />
            </Table.Cell>
            <Table.Cell collapsing={true}>{renderCondition(row)}</Table.Cell>
            <Table.Cell>{row.payload && renderDependencies(row.type, row.payload)}</Table.Cell>
        </Table.Row>
    );
};

export default ProcessWaitList;
