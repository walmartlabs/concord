/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { Link } from 'react-router';
import { Table } from 'semantic-ui-react';
import { formatTimestamp } from '../util';
import * as constants from '../constants';

import ProcessStatusIcon from '../ProcessStatusIcon';

export class ProcessDetailTable extends React.Component {
    render() {
        const { data, renderActionButtons, attached } = this.props;

        return (
            <Table
                textAlign="center"
                color={constants.statusColors[data.status]}
                attached={attached}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell>Status</Table.HeaderCell>
                        <Table.HeaderCell>Parent ID</Table.HeaderCell>
                        <Table.HeaderCell>Started by</Table.HeaderCell>
                        <Table.HeaderCell>Created at</Table.HeaderCell>
                        <Table.HeaderCell>Last Update</Table.HeaderCell>
                        <Table.HeaderCell>Actions</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    <Table.Row>
                        <Table.Cell>
                            <ProcessStatusIcon status={data.status} />
                        </Table.Cell>
                        <Table.Cell>
                            {data.parentInstanceId ? (
                                <Link to={`/process/${data.parentInstanceId}`}>
                                    {data.parentInstanceId}
                                </Link>
                            ) : (
                                ' - '
                            )}
                        </Table.Cell>
                        <Table.Cell>{data.initiator}</Table.Cell>
                        <Table.Cell>{formatTimestamp(data.createdAt)}</Table.Cell>
                        <Table.Cell>{formatTimestamp(data.lastUpdatedAt)}</Table.Cell>
                        <Table.Cell>{renderActionButtons()}</Table.Cell>
                    </Table.Row>
                </Table.Body>
            </Table>
        );
    }
}

export default ProcessDetailTable;
