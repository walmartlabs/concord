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
import { Grid, Tab } from 'semantic-ui-react';
import {
    AnsibleHost,
    AnsibleStatsEntry,
    AnsibleStatus,
    SearchFilter
} from '../../../api/process/ansible';

import { AnsibleHostList, AnsibleStatChart } from '../../molecules';
import { makeStats } from './data';
import { ConcordId } from '../../../api/common';
import { AnsibleTaskListActivity } from '../../organisms';

interface State {
    filter: SearchFilter;
}

interface Props {
    stats: AnsibleStatsEntry;
    hosts: AnsibleHost[];
    instanceId: ConcordId;
    next?: number;
    prev?: number;
    refresh: (filter: SearchFilter) => void;
}

class AnsibleStats extends React.PureComponent<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { filter: {} };

        this.handleStatusChange = this.handleStatusChange.bind(this);
        this.handleRefresh = this.handleRefresh.bind(this);
    }

    handleStatusChange(status?: AnsibleStatus) {
        const { refresh } = this.props;
        const { filter } = this.state;
        const newFilter = { ...filter, status, offset: undefined };
        this.setState({ filter: newFilter });
        refresh(newFilter);
    }

    handleRefresh(filter: SearchFilter) {
        const { refresh } = this.props;
        const newFilter = { ...filter, status: this.state.filter.status };
        this.setState({ filter: newFilter });
        refresh(newFilter);
    }

    render() {
        const { instanceId, hosts, stats, prev, next } = this.props;

        return (
            <Tab
                panes={[
                    {
                        menuItem: 'By hosts',
                        render: () => (
                            <Tab.Pane attached={true}>
                                <Grid columns={2}>
                                    <Grid.Column>
                                        <AnsibleStatChart
                                            width={500}
                                            height={300}
                                            data={makeStats(stats)}
                                            uniqueHosts={stats.uniqueHosts}
                                            onClick={this.handleStatusChange}
                                        />
                                    </Grid.Column>
                                    <Grid.Column>
                                        <AnsibleHostList
                                            instanceId={instanceId}
                                            hosts={hosts}
                                            hostGroups={stats.hostGroups}
                                            prev={prev}
                                            next={next}
                                            refresh={this.handleRefresh}
                                        />
                                    </Grid.Column>
                                </Grid>
                            </Tab.Pane>
                        )
                    },
                    {
                        menuItem: 'Failures',
                        render: () => (
                            <Tab.Pane attached={true} style={{ overflow: 'auto' }}>
                                <AnsibleTaskListActivity
                                    showHosts={true}
                                    status={AnsibleStatus.FAILED}
                                    instanceId={instanceId}
                                />
                            </Tab.Pane>
                        )
                    }
                ]}
            />
        );
    }
}

export default AnsibleStats;
