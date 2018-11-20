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

import { AnsibleHost, AnsibleStatus } from '../../../api/process/event';
import { HumanizedDuration } from '../index';
import { ConcordId } from '../../../api/common';
import { AnsibleTaskListActivity } from '../../organisms';

interface State {
    hostFilter?: string;
    hostGroupFilter?: string;
}

interface Props {
    instanceId: ConcordId;
    hosts: AnsibleHost[];
    hostGroups: string[];
    selectedStatus?: AnsibleStatus;
}

const compareByHost = (a: AnsibleHost, b: AnsibleHost) =>
    a.host > b.host ? 1 : a.host < b.host ? -1 : 0;

const applyFilter = (
    hosts: AnsibleHost[],
    selectedStatus?: AnsibleStatus,
    hostFilter?: string,
    hostGroupFilter?: string
): AnsibleHost[] => {
    let result = [...hosts];

    if (selectedStatus) {
        result = result.filter(({ status }) => status === selectedStatus);
    }

    if (hostFilter) {
        const f = hostFilter.toLowerCase();
        result = result.filter(({ host }) => host.toLowerCase().indexOf(f) >= 0);
    }

    if (hostGroupFilter) {
        result = result.filter(({ hostGroup }) => hostGroup === hostGroupFilter);
    }

    return result.sort(compareByHost);
};

const makeHostGroupOptions = (data: string[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    const opts = data.map((value) => ({ value, text: value }));
    return [{ value: '', text: 'all' }].concat(opts);
};

class AnsibleHostList extends React.Component<Props, State> {
    static renderHostItem(
        instanceId: ConcordId,
        host: string,
        hostGroup: string,
        duration: number,
        idx: number
    ) {
        return (
            <Modal
                key={idx}
                basic={true}
                size="fullscreen"
                dimmer="inverted"
                trigger={
                    <Table.Row>
                        <Table.Cell>{host}</Table.Cell>
                        <Table.Cell>{hostGroup}</Table.Cell>
                        <Table.Cell>
                            <HumanizedDuration value={duration !== 0 ? duration : undefined} />
                        </Table.Cell>
                    </Table.Row>
                }>
                <Modal.Content>
                    <AnsibleTaskListActivity
                        instanceId={instanceId}
                        host={host}
                        hostGroup={hostGroup}
                    />
                </Modal.Content>
            </Modal>
        );
    }

    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    render() {
        const { instanceId, hosts, hostGroups, selectedStatus } = this.props;
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
                            <Table.HeaderCell singleLine={true}>Host Group</Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>Duration</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {applyFilter(hosts, selectedStatus, hostFilter, hostGroupFilter).map(
                            (host, idx) =>
                                AnsibleHostList.renderHostItem(
                                    instanceId,
                                    host.host,
                                    host.hostGroup,
                                    host.duration,
                                    idx
                                )
                        )}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default AnsibleHostList;
