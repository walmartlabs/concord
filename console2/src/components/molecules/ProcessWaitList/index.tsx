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
import { Icon, Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';
import { Link } from 'react-router-dom';
import { LocalTimestamp } from '../index';

interface Props {
    data: ProcessWaitHistoryEntry[];
}

class ProcessWaitList extends React.PureComponent<Props> {
    static renderTableHeader() {
        return (
            <Table.Row>
                <Table.HeaderCell collapsing={true}>Date</Table.HeaderCell>
                <Table.HeaderCell collapsing={true}>Condition</Table.HeaderCell>
                <Table.HeaderCell>Dependencies</Table.HeaderCell>
            </Table.Row>
        );
    }

    static renderProcessLink = (id: ConcordId) => {
        return (
            <p>
                <Link to={`/process/${id}`}>{id}</Link>
            </p>
        );
    };

    static renderCondition({ type, reason, payload }: ProcessWaitHistoryEntry) {
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
    }

    renderProcessWaitDetails = (payload: ProcessWaitPayload) => {
        return payload.processes.map((p) => ProcessWaitList.renderProcessLink(p));
    };

    renderProcessLockDetails = (payload: ProcessLockPayload) => {
        return ProcessWaitList.renderProcessLink(payload.instanceId);
    };

    renderDependencies = (type: WaitType, payload: WaitPayload) => {
        switch (type) {
            case WaitType.PROCESS_COMPLETION: {
                return this.renderProcessWaitDetails(payload as ProcessWaitPayload);
            }
            case WaitType.PROCESS_LOCK: {
                return this.renderProcessLockDetails(payload as ProcessLockPayload);
            }
            default:
                return '';
        }
    };

    renderTableRow = (row: ProcessWaitHistoryEntry) => {
        return (
            <Table.Row key={row.id} verticalAlign="top">
                <Table.Cell singleLine={true}>
                    <LocalTimestamp value={row.eventDate} />
                </Table.Cell>
                <Table.Cell collapsing={true}>{ProcessWaitList.renderCondition(row)}</Table.Cell>
                <Table.Cell>
                    {row.payload && this.renderDependencies(row.type, row.payload)}
                </Table.Cell>
            </Table.Row>
        );
    };

    render() {
        const { data } = this.props;

        return (
            <Table celled={true}>
                <Table.Header>{ProcessWaitList.renderTableHeader()}</Table.Header>
                <Table.Body>
                    {data && data.length > 0 && data.map((p) => this.renderTableRow(p))}
                    {(!data || (data && data.length === 0)) && (
                        <tr style={{ fontWeight: 'bold' }}>
                            <Table.Cell colSpan={3}>No data available</Table.Cell>
                        </tr>
                    )}
                </Table.Body>
            </Table>
        );
    }
}

export default ProcessWaitList;
