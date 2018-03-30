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
import { Table } from 'semantic-ui-react';
import { formatTimestamp } from '../../util';
import * as _ from 'lodash';
import ReactJson from 'react-json-view';

export class StatusOverviewTable extends React.Component {
    render() {
        const { eventsByStatus } = this.props;

        const groupByTaskName = _.groupBy(eventsByStatus, (d) => d.data.task);

        if (eventsByStatus) {
            return (
                <div>
                    <Table celled definition={true}>
                        <Table.Header>
                            <Table.Row>
                                <Table.HeaderCell />
                                <Table.HeaderCell>Host</Table.HeaderCell>
                                <Table.HeaderCell>Event Time</Table.HeaderCell>
                                <Table.HeaderCell>Results</Table.HeaderCell>
                                <Table.HeaderCell>Playbook</Table.HeaderCell>
                            </Table.Row>
                        </Table.Header>
                        {Object.keys(groupByTaskName).map((key, index) => (
                            <Table.Body key={index}>
                                {groupByTaskName[key].map((value, index, arr) => {
                                    return (
                                        <Table.Row key={index}>
                                            <Table.Cell>
                                                {index !== 0 &&
                                                arr[index - 1].data.task ===
                                                    value.data.task ? null : (
                                                    <div>{key}</div>
                                                )}
                                            </Table.Cell>

                                            <Table.Cell>{value.data.host}</Table.Cell>
                                            <Table.Cell>
                                                {formatTimestamp(value.eventDate)}
                                            </Table.Cell>
                                            <Table.Cell>
                                                <ReactJson
                                                    src={value.data.result}
                                                    collapsed={true}
                                                    name={null}
                                                    enableClipboard={false}
                                                />
                                            </Table.Cell>
                                            <Table.Cell>{value.data.playbook}</Table.Cell>
                                        </Table.Row>
                                    );
                                })}
                            </Table.Body>
                        ))}
                    </Table>
                </div>
            );
        } else {
            return null;
        }
    }
}

export default StatusOverviewTable;
