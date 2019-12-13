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

import { AnsibleHost, AnsibleStatus, SearchFilter } from '../../../../api/process/ansible';
import { HumanizedDuration, PaginationToolBar } from '../../../molecules';
import { ConcordId } from '../../../../api/common';
import { AnsibleTaskListActivity } from '../../../organisms';

interface State {
    hostFilter?: string;
    prevHostFilter?: string;
    hostGroupFilter?: string;
}

interface Props {
    instanceId: ConcordId;
    playbookId?: ConcordId;
    hosts?: AnsibleHost[];
    hostGroups: string[];

    next?: number;
    prev?: number;
    refresh: (filter: SearchFilter) => void;
}

const makeHostGroupOptions = (data: string[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    return data.map((value) => ({ value, text: value }));
};

class AnsibleHostList extends React.Component<Props, State> {
    static renderHostItem(
        instanceId: ConcordId,
        host: string,
        hostGroup: string,
        hostStatus: AnsibleStatus,
        duration: number,
        idx: number,
        playbookId?: ConcordId
    ) {
        return (
            <Modal
                key={idx}
                basic={true}
                size="fullscreen"
                dimmer="inverted"
                trigger={
                    <Table.Row error={hostStatus === AnsibleStatus.FAILED}>
                        <Table.Cell>{host}</Table.Cell>
                        <Table.Cell>{hostGroup}</Table.Cell>
                        <Table.Cell>{hostStatus}</Table.Cell>
                        <Table.Cell>
                            <HumanizedDuration value={duration !== 0 ? duration : undefined} />
                        </Table.Cell>
                    </Table.Row>
                }>
                <Modal.Content scrolling={true}>
                    <AnsibleTaskListActivity
                        instanceId={instanceId}
                        playbookId={playbookId}
                        host={host}
                        hostGroup={hostGroup}
                    />
                </Modal.Content>
            </Modal>
        );
    }

    static renderHosts(instanceId: ConcordId, playbookId?: ConcordId, hosts?: AnsibleHost[]) {
        if (!hosts) {
            return (
                <tr style={{ fontWeight: 'bold' }}>
                    <Table.Cell colSpan={4}>-</Table.Cell>
                </tr>
            );
        }

        if (hosts.length === 0) {
            return (
                <Table.Row style={{ fontWeight: 'bold' }}>
                    <Table.Cell colSpan={4}>No data available</Table.Cell>
                </Table.Row>
            );
        }

        return hosts.map((host, idx) =>
            AnsibleHostList.renderHostItem(
                instanceId,
                host.host,
                host.hostGroup,
                host.status,
                host.duration,
                idx,
                playbookId
            )
        );
    }

    constructor(props: Props) {
        super(props);
        this.state = {};

        this.handleNext = this.handleNext.bind(this);
        this.handlePrev = this.handlePrev.bind(this);
        this.handleFirst = this.handleFirst.bind(this);
        this.handleHostOnBlur = this.handleHostOnBlur.bind(this);
        this.handleHostChange = this.handleHostChange.bind(this);
        this.handleHostGroupChange = this.handleHostGroupChange.bind(this);
    }

    handleNext() {
        this.handleNavigation(this.props.next);
    }

    handlePrev() {
        this.handleNavigation(this.props.prev);
    }

    handleFirst() {
        this.handleNavigation(0);
    }

    handleNavigation(offset?: number) {
        const { hostFilter, hostGroupFilter } = this.state;
        const { refresh } = this.props;

        refresh({
            offset,
            host: hostFilter,
            hostGroup: hostGroupFilter
        });
    }

    handleHostOnBlur() {
        const { hostFilter, prevHostFilter, hostGroupFilter } = this.state;
        if (hostFilter !== prevHostFilter) {
            this.setState({ prevHostFilter: hostFilter });
            this.props.refresh({ host: hostFilter, hostGroup: hostGroupFilter });
        }
    }

    handleHostChange(s?: string) {
        const { hostFilter } = this.state;
        const host = s && s.length > 0 ? s : undefined;

        if (hostFilter !== host) {
            this.setState({ hostFilter: host });
        }
    }

    handleHostGroupChange(s?: string) {
        const { hostFilter, hostGroupFilter } = this.state;
        const hostGroup = s && s.length > 0 ? s : undefined;

        if (hostGroupFilter !== hostGroup) {
            this.setState({ hostGroupFilter: hostGroup });
            this.props.refresh({ host: hostFilter, hostGroup });
        }
    }

    render() {
        const { instanceId, playbookId, hosts, hostGroups, prev, next } = this.props;

        return (
            <>
                <Grid columns={3} style={{ marginBottom: '5px' }}>
                    <Grid.Column>
                        <Input
                            disabled={hosts === undefined}
                            fluid={true}
                            type="text"
                            icon="filter"
                            placeholder="Host"
                            onBlur={this.handleHostOnBlur}
                            onChange={(ev, data) => this.handleHostChange(data.value)}
                        />
                    </Grid.Column>
                    <Grid.Column>
                        <Dropdown
                            disabled={hosts === undefined}
                            clearable={true}
                            fluid={true}
                            placeholder="Host group"
                            search={true}
                            selection={true}
                            options={makeHostGroupOptions(hostGroups)}
                            onChange={(ev, data) =>
                                this.handleHostGroupChange(data.value as string)
                            }
                        />
                    </Grid.Column>
                    <Grid.Column textAlign={'right'}>
                        <PaginationToolBar
                            filterProps={{}}
                            handleNext={this.handleNext}
                            handlePrev={this.handlePrev}
                            handleFirst={this.handleFirst}
                            disablePrevious={prev === undefined}
                            disableNext={next === undefined}
                            disableFirst={prev === undefined}
                        />
                    </Grid.Column>
                </Grid>

                <Table
                    celled={true}
                    attached="bottom"
                    selectable={true}
                    basic={true}
                    compact={true}
                    style={{ cursor: 'pointer' }}
                    className={hosts ? '' : 'loading'}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell singleLine={true}>Host</Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>Host Group</Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>Host Status</Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>Duration</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {AnsibleHostList.renderHosts(instanceId, playbookId, hosts)}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default AnsibleHostList;
