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
import { Input, Menu, Modal, Table } from 'semantic-ui-react';

import { AnsibleEvent, AnsibleStatus, ProcessEventEntry } from '../../../api/process/event';
import { AnsibleTaskList, HumanizedDuration } from '../index';

export interface AnsibleHostListEntry {
    host: string;
    status: AnsibleStatus;
    duration: number;
}

interface State {
    filter?: string;
}

interface Props {
    hosts: AnsibleHostListEntry[];
    hostEventsFn: (host: string) => Array<ProcessEventEntry<AnsibleEvent>>;
    selectedStatus?: AnsibleStatus;
}

const compareByHost = (a: AnsibleHostListEntry, b: AnsibleHostListEntry) =>
    a.host > b.host ? 1 : a.host < b.host ? -1 : 0;

const applyFilter = (
    hosts: AnsibleHostListEntry[],
    selectedStatus?: AnsibleStatus,
    filter?: string
): AnsibleHostListEntry[] => {
    let result = [...hosts];

    if (selectedStatus) {
        result = result.filter(({ status }) => status === selectedStatus);
    }

    if (filter) {
        const f = filter.toLowerCase();
        result = result.filter(({ host }) => host.toLowerCase().indexOf(f) >= 0);
    }

    return result.sort(compareByHost);
};

class AnsibleHostList extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    renderHostItem(host: string, duration: number, idx: number) {
        const { hostEventsFn } = this.props;
        return (
            <Modal
                key={idx}
                basic={true}
                size="fullscreen"
                dimmer="inverted"
                trigger={
                    <Table.Row>
                        <Table.Cell>{host}</Table.Cell>
                        <Table.Cell>
                            <HumanizedDuration value={duration} />
                        </Table.Cell>
                    </Table.Row>
                }>
                <Modal.Content>
                    <AnsibleTaskList title={host} events={hostEventsFn(host)} />
                </Modal.Content>
            </Modal>
        );
    }

    render() {
        const { hosts, selectedStatus } = this.props;
        const { filter } = this.state;

        return (
            <>
                <Menu attached="top" borderless={true} tabular={true}>
                    <Menu.Item position={'right'}>
                        <Input
                            type="text"
                            icon="filter"
                            size="small"
                            placeholder="Host"
                            onChange={(e, { value }) => this.setState({ filter: value })}
                        />
                    </Menu.Item>
                </Menu>

                <Table celled={true} attached="bottom" selectable={true}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell singleLine={true}>
                                Hosts by Status {selectedStatus}
                            </Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>Duration</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {applyFilter(hosts, selectedStatus, filter).map((host, idx) =>
                            this.renderHostItem(host.host, host.duration, idx)
                        )}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default AnsibleHostList;
