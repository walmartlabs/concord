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
import { isFinal, ProcessHistoryEntry, ProcessStatus } from '../../../api/process';
import { Table } from 'semantic-ui-react';
import { formatDuration } from '../../../utils';
import { LocalTimestamp } from '../index';

interface Props {
    data: ProcessHistoryEntry[];
}

class ProcessHistoryList extends React.PureComponent<Props> {
    renderTableHeader = () => {
        return (
            <Table.Row>
                <Table.HeaderCell collapsing={true}>Status</Table.HeaderCell>
                <Table.HeaderCell collapsing={true}>Change Time </Table.HeaderCell>
                <Table.HeaderCell collapsing={true}>Elapsed Time </Table.HeaderCell>
            </Table.Row>
        );
    };

    renderTableRow = (row: ProcessHistoryEntry, idx: number) => {
        const { data } = this.props;
        if (!data) {
            return;
        }

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

    render() {
        const { data } = this.props;

        return (
            <Table celled={true}>
                <Table.Header>{this.renderTableHeader()}</Table.Header>
                <Table.Body>
                    {data.length > 0 && data.map((p, idx) => this.renderTableRow(p, idx))}
                    {data.length === 0 && (
                        <tr style={{ fontWeight: 'bold' }}>
                            <Table.Cell colSpan={3}>No data available</Table.Cell>
                        </tr>
                    )}
                </Table.Body>
            </Table>
        );
    }
}

export default ProcessHistoryList;
