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
import { Dropdown, DropdownItemProps, Grid, Input, Modal, Table } from 'semantic-ui-react';

import { AnsibleEvent, AnsibleStatus, ProcessEventEntry } from '../../../api/process/event';
import { AnsibleTaskList, HumanizedDuration } from '../index';

export interface AnsibleHostListEntry {
    host: string;
    hostGroups: string[];
    status: AnsibleStatus;
    duration: number;
}

interface State {
    hostFilter?: string;
    hostGroupFilter?: string;
}

interface Props {
    hosts: AnsibleHostListEntry[];
    hostGroups: string[];
    hostEventsFn: (host: string, hostGroup?: string) => Array<ProcessEventEntry<AnsibleEvent>>;
    selectedStatus?: AnsibleStatus;
}

const compareByHost = (a: AnsibleHostListEntry, b: AnsibleHostListEntry) =>
    a.host > b.host ? 1 : a.host < b.host ? -1 : 0;

const applyFilter = (
    hosts: AnsibleHostListEntry[],
    selectedStatus?: AnsibleStatus,
    hostFilter?: string,
    hostGroupFilter?: string
): AnsibleHostListEntry[] => {
    let result = [...hosts];

    if (selectedStatus) {
        result = result.filter(({ status }) => status === selectedStatus);
    }

    if (hostFilter) {
        const f = hostFilter.toLowerCase();
        result = result.filter(({ host }) => host.toLowerCase().indexOf(f) >= 0);
    }

    if (hostGroupFilter) {
        result = result.filter(({ hostGroups }) => hostGroups.indexOf(hostGroupFilter) > -1);
    }

    return result.sort(compareByHost);
};

const makeHostGroupOptions = (data: string[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    const opts = data.map((value) => ({ value, text: value }));
    return [{ text: 'all' }].concat(opts);
};

class AnsibleHostList extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    renderHostItem(host: string, duration: number, idx: number) {
        const { hostEventsFn } = this.props;
        const { hostGroupFilter } = this.state;
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
                    <AnsibleTaskList title={host} events={hostEventsFn(host, hostGroupFilter)} />
                </Modal.Content>
            </Modal>
        );
    }

    render() {
        const { hosts, hostGroups, selectedStatus } = this.props;
        const { hostFilter, hostGroupFilter } = this.state;

        return (
            <>
                <Grid columns={2} style={{ marginBottom: '5px' }}>
                    <Grid.Column>
                        <Input
                            fluid={true}
                            type="text"
                            icon="filter"
                            placeholder="Host"
                            onChange={(e, { value }) => this.setState({ hostFilter: value })}
                        />
                    </Grid.Column>
                    <Grid.Column>
                        <Dropdown
                            fluid={true}
                            placeholder="Host group"
                            search={true}
                            selection={true}
                            options={makeHostGroupOptions(hostGroups)}
                            onChange={(e, { value }) =>
                                this.setState({ hostGroupFilter: value as string })
                            }
                        />
                    </Grid.Column>
                </Grid>

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
                        {applyFilter(hosts, selectedStatus, hostFilter, hostGroupFilter).map(
                            (host, idx) => this.renderHostItem(host.host, host.duration, idx)
                        )}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default AnsibleHostList;
