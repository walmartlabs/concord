/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { Table } from 'semantic-ui-react';

import { isFinal, ProcessHistoryEntry } from '../../../api/process';
import { formatDuration } from '../../../utils';
import { LocalTimestamp } from '../index';

interface ExternalProps {
    data?: ProcessHistoryEntry[];
}

const renderTableHeader = () => {
    return (
        <Table.Row>
            <Table.HeaderCell collapsing={true}>Status</Table.HeaderCell>
            <Table.HeaderCell collapsing={true}>Change Time </Table.HeaderCell>
            <Table.HeaderCell collapsing={true}>Elapsed Time </Table.HeaderCell>
        </Table.Row>
    );
};

const renderTableRow = (data: ProcessHistoryEntry[], row: ProcessHistoryEntry, idx: number) => {
    let elapsedTime: string | undefined;

    if (idx === 0 && !isFinal(row.status)) {
        const startTime: Date = new Date(data[idx].changeDate);
        const currentTime: Date = new Date();
        const duration = currentTime.getTime() - startTime.getTime();
        elapsedTime = formatDuration(duration) + ' (so far)';
    } else if (idx > 0) {
        const endTime: Date = new Date(row.changeDate);
        const startTime: Date = new Date(data[idx - 1].changeDate);
        const duration = startTime.getTime() - endTime.getTime();
        elapsedTime = formatDuration(duration);
    }

    return (
        <Table.Row key={idx}>
            <Table.Cell>{row.status}</Table.Cell>
            <Table.Cell>
                <LocalTimestamp value={row.changeDate} />
            </Table.Cell>
            <Table.Cell>{elapsedTime}</Table.Cell>
        </Table.Row>
    );
};

const renderElements = (data?: ProcessHistoryEntry[]) => {
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

    return data.map((p, idx) => renderTableRow(data, p, idx));
};

const ProcessHistoryList = ({ data }: ExternalProps) => {
    return (
        <Table celled={true} className={data ? '' : 'loading'}>
            <Table.Header>{renderTableHeader()}</Table.Header>
            <Table.Body>{renderElements(data)}</Table.Body>
        </Table>
    );
};

export default ProcessHistoryList;
