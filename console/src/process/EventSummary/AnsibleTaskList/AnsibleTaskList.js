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
import { Table, Header } from 'semantic-ui-react';
import ReactJson from 'react-json-view';
import { formatTimestamp } from '../../util';

export class AnsibleTaskList extends React.Component {
    render() {
        const { title, tasks } = this.props;

        return (
            <div>
                <Header as="h3" attached="top">
                    {title}
                </Header>
                <Table celled attached="bottom">
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>Ansible Task</Table.HeaderCell>
                            <Table.HeaderCell>Status</Table.HeaderCell>
                            <Table.HeaderCell>Event Time</Table.HeaderCell>
                            <Table.HeaderCell>Results</Table.HeaderCell>
                            <Table.HeaderCell>Playbook</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {tasks &&
                            tasks.map((value, index) => {
                                return (
                                    <Table.Row
                                        key={index}
                                        error={value.data.status === 'FAILED'}
                                        positive={value.data.status === 'OK'}
                                        warning={value.data.status === 'UNREACHABLE'}>
                                        <Table.Cell>{value.data.task}</Table.Cell>
                                        <Table.Cell>{value.data.status}</Table.Cell>
                                        <Table.Cell>{formatTimestamp(value.eventDate)}</Table.Cell>
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
                </Table>
            </div>
        );
    }
}

export default AnsibleTaskList;
