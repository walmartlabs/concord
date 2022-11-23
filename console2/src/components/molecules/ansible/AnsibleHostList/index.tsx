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

import { AnsibleHost, AnsibleStatus, SearchFilter, SortField, SortOrder } from '../../../../api/process/ansible';
import { HumanizedDuration, PaginationToolBar } from '../../../molecules';
import { ConcordId } from '../../../../api/common';
import { AnsibleTaskListActivity } from '../../../organisms';
import ColumnSort from "../../../atoms/ColumnSort";

interface State {
    hostFilter?: string;
    prevHostFilter?: string;
    hostGroupFilter?: string;
    hostStatusFilter?: AnsibleStatus;
    hostSortFieldFilter?: SortField;
    hostSortByFilter?: SortOrder;
}

interface Props {
    instanceId: ConcordId;
    playbookId?: ConcordId;
    hosts?: AnsibleHost[];
    hostGroups: string[];
    showStatusFilter?: boolean;

    next?: number;
    prev?: number;
    refresh: (filter: SearchFilter) => void;
}

const hostStatusesOptions = [
    { text: AnsibleStatus.RUNNING, value: AnsibleStatus.RUNNING },
    { text: AnsibleStatus.CHANGED, value: AnsibleStatus.CHANGED },
    { text: AnsibleStatus.FAILED, value: AnsibleStatus.FAILED },
    { text: AnsibleStatus.OK, value: AnsibleStatus.OK },
    { text: AnsibleStatus.SKIPPED, value: AnsibleStatus.SKIPPED },
    { text: AnsibleStatus.UNREACHABLE, value: AnsibleStatus.UNREACHABLE }
];

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
        this.handleHostStatusChange = this.handleHostStatusChange.bind(this);
        this.handleOrderByChange = this.handleOrderByChange.bind(this);
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
        const { hostFilter, hostGroupFilter, hostStatusFilter, hostSortFieldFilter, hostSortByFilter } = this.state;
        const { refresh } = this.props;

        refresh({
            offset,
            host: hostFilter,
            hostGroup: hostGroupFilter,
            status: hostStatusFilter,
            sortField: hostSortFieldFilter,
            sortBy: hostSortByFilter
        });
    }

    handleHostOnBlur() {
        const { hostFilter, prevHostFilter, hostGroupFilter, hostStatusFilter, hostSortFieldFilter, hostSortByFilter } = this.state;
        if (hostFilter !== prevHostFilter) {
            this.setState({ prevHostFilter: hostFilter });
            this.props.refresh({
                host: hostFilter,
                hostGroup: hostGroupFilter,
                status: hostStatusFilter,
                sortField: hostSortFieldFilter,
                sortBy: hostSortByFilter
            });
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
        const { hostFilter, hostGroupFilter, hostStatusFilter, hostSortFieldFilter, hostSortByFilter } = this.state;
        const hostGroup = s && s.length > 0 ? s : undefined;

        if (hostGroupFilter !== hostGroup) {
            this.setState({ hostGroupFilter: hostGroup });
            this.props.refresh({ host: hostFilter, hostGroup, status: hostStatusFilter, sortField: hostSortFieldFilter, sortBy: hostSortByFilter });
        }
    }

    handleHostStatusChange(s?: AnsibleStatus) {
        const { hostFilter, hostGroupFilter, hostStatusFilter, hostSortFieldFilter, hostSortByFilter } = this.state;
        const status = s && s.length > 0 ? s : undefined;

        if (status !== hostStatusFilter) {
            this.setState({ hostStatusFilter: status });
            this.props.refresh({ host: hostFilter, hostGroup: hostGroupFilter, status, sortField: hostSortFieldFilter, sortBy: hostSortByFilter });
        }
    }
    
    handleOrderByChange(sf: SortField, sb: SortOrder) {
        const { hostFilter, hostGroupFilter, hostStatusFilter, hostSortFieldFilter, hostSortByFilter } = this.state;
        const sortField = sf && sf.length > 0 ? sf : undefined;
        const sortBy = sb && sb.length > 0 ? sb : undefined;
        
        if (hostSortFieldFilter !== sortField || hostSortByFilter !== sortBy) {
            this.setState({ hostSortFieldFilter: sortField });
            this.setState({hostSortByFilter: sortBy});
            this.props.refresh({ host: hostFilter, hostGroup: hostGroupFilter, status: hostStatusFilter, sortField, sortBy });
        }
    }
    
    render() {
        const {
            instanceId,
            playbookId,
            hosts,
            hostGroups,
            prev,
            next,
            showStatusFilter
        } = this.props;

        return (
            <>
                <Grid columns={showStatusFilter ? 4 : 3} style={{ marginBottom: '5px' }}>
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
                    {showStatusFilter && (
                        <Grid.Column>
                            <Dropdown
                                disabled={hosts === undefined}
                                clearable={true}
                                fluid={true}
                                placeholder="Host status"
                                search={true}
                                selection={true}
                                options={hostStatusesOptions}
                                onChange={(ev, data) =>
                                    this.handleHostStatusChange(data.value as AnsibleStatus)
                                }
                            />
                        </Grid.Column>
                    )}
                    <Grid.Column textAlign={'right'}>
                        <PaginationToolBar
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
                            <Table.HeaderCell singleLine={true}>
                                <div style={{ height:'0px', lineHeight:'36px' }}>Host</div>
                                <ColumnSort 
                                    ascSort={() => this.handleOrderByChange(SortField.HOST, SortOrder.ASC)} 
                                    descSort={() => this.handleOrderByChange(SortField.HOST, SortOrder.DESC)} 
                                />
                            </Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>
                                <div style={{ height:'0px', lineHeight:'36px' }}>Host Group</div>
                                <ColumnSort
                                    ascSort={() => this.handleOrderByChange(SortField.HOST_GROUP, SortOrder.ASC)}
                                    descSort={() => this.handleOrderByChange(SortField.HOST_GROUP, SortOrder.DESC)}
                                />
                            </Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>
                                <div style={{ height:'0px', lineHeight:'36px' }}>Host Status</div>
                                <ColumnSort
                                    ascSort={() => this.handleOrderByChange(SortField.STATUS, SortOrder.ASC)}
                                    descSort={() => this.handleOrderByChange(SortField.STATUS, SortOrder.DESC)}
                                />
                            </Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>
                                <div style={{ height:'0px', lineHeight:'36px' }}>Duration</div>
                                <ColumnSort
                                    ascSort={() => this.handleOrderByChange(SortField.DURATION, SortOrder.ASC)}
                                    descSort={() => this.handleOrderByChange(SortField.DURATION, SortOrder.DESC)}
                                />
                            </Table.HeaderCell>
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
