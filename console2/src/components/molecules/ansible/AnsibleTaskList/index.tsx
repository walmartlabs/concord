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
import ReactJson from 'react-json-view';
import { Header, Table } from 'semantic-ui-react';

import { AnsibleEvent, AnsibleStatus } from '../../../../api/process/ansible';
import { ProcessEventEntry } from '../../../../api/process/event';
import { HumanizedDuration, LocalTimestamp } from '../../../molecules';

interface Props {
    title?: string;
    showHosts?: boolean;
    hideStatus?: boolean;
    hidePlaybook?: boolean;
    tasks?: Array<ProcessEventEntry<AnsibleEvent>>;
}

class AnsibleTaskList extends React.Component<Props> {
    renderTaskList = () => {
        const { tasks, showHosts, hideStatus, hidePlaybook } = this.props;

        return (
            <Table
                celled={true}
                attached="bottom"
                basic={true}
                compact={true}
                style={{ width: '100%', margin: 0 }}>
                <Table.Header>
                    <Table.Row>
                        {showHosts && <Table.HeaderCell collapsing={true}>Host</Table.HeaderCell>}
                        <Table.HeaderCell collapsing={true}>Ansible Task</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Action</Table.HeaderCell>
                        {!hideStatus && (
                            <Table.HeaderCell collapsing={true}>Status</Table.HeaderCell>
                        )}
                        <Table.HeaderCell collapsing={true}>Event Time</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Duration</Table.HeaderCell>
                        <Table.HeaderCell>Results</Table.HeaderCell>
                        {!hidePlaybook && (
                            <Table.HeaderCell collapsing={true}>Playbook</Table.HeaderCell>
                        )}
                    </Table.Row>
                </Table.Header>

                <Table.Body>
                    {tasks &&
                        tasks.map((value, index) => {
                            const { status, ignore_errors } = value.data;

                            const error = status === AnsibleStatus.FAILED && !ignore_errors;
                            const positive = status === AnsibleStatus.OK;
                            const warning = value.data.status === AnsibleStatus.UNREACHABLE;

                            const statusString =
                                status + (ignore_errors ? ' (errors ignored)' : '');

                            return (
                                <Table.Row
                                    key={index}
                                    error={error}
                                    positive={positive}
                                    warning={warning}>
                                    {showHosts && (
                                        <Table.Cell singleLine={true}>{value.data.host}</Table.Cell>
                                    )}
                                    <Table.Cell singleLine={true}>{value.data.task}</Table.Cell>
                                    <Table.Cell singleLine={true}>
                                        {value.data.action ? value.data.action : '-'}
                                    </Table.Cell>
                                    {!hideStatus && <Table.Cell>{statusString}</Table.Cell>}
                                    <Table.Cell singleLine={true}>
                                        <LocalTimestamp value={value.eventDate} />
                                    </Table.Cell>
                                    <Table.Cell singleLine={true}>
                                        <HumanizedDuration value={value.data.duration} />
                                    </Table.Cell>
                                    <Table.Cell>
                                        <ReactJson
                                            src={value.data.result as object}
                                            collapsed={true}
                                            name={null}
                                            enableClipboard={false}
                                            displayObjectSize={false}
                                            displayDataTypes={false}
                                        />
                                    </Table.Cell>
                                    {!hidePlaybook && (
                                        <Table.Cell singleLine={true}>
                                            {value.data.playbook}
                                        </Table.Cell>
                                    )}
                                </Table.Row>
                            );
                        })}
                    {!tasks && (
                        <tr style={{ fontWeight: 'bold' }}>
                            <Table.Cell colSpan={8}>-</Table.Cell>
                        </tr>
                    )}
                    {tasks && tasks.length === 0 && (
                        <tr style={{ fontWeight: 'bold' }}>
                            <Table.Cell colSpan={8}>No data available</Table.Cell>
                        </tr>
                    )}
                </Table.Body>
            </Table>
        );
    };

    render() {
        const { title } = this.props;

        if (!title) {
            return this.renderTaskList();
        }

        return (
            <>
                {/*TODO: replace this table in table with two line header */}
                <Table
                    style={{ border: 'none', padding: '0' }}
                    className={this.props.tasks ? '' : 'loading'}>
                    <Table.Body>
                        <Table.Row>
                            <Table.Cell style={{ padding: '0', border: 'none' }}>
                                <Header as="h3" attached="top">
                                    {title}
                                </Header>
                            </Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell style={{ padding: '0', border: 'none' }}>
                                {this.renderTaskList()}
                            </Table.Cell>
                        </Table.Row>
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default AnsibleTaskList;
