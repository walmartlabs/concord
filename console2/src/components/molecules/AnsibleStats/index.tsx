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
import { AnsibleHost, AnsibleStatus } from '../../../api/process/event';

import { AnsibleHostList, AnsibleStatChart } from '../../molecules';
import { AnsibleStatChartEntry } from '../AnsibleStatChart';
import { countUniqueHosts, makeHostGroups, makeStats } from './data';
import { ConcordId } from '../../../api/common';
import { AnsibleTaskListActivity } from '../../organisms';

interface State {
    selectedStatus?: AnsibleStatus;
}

interface Props {
    hosts: AnsibleHost[];
    instanceId: ConcordId;
}

class AnsibleStats extends React.Component<Props, State> {
    private hostGroups: string[];
    private stats: AnsibleStatChartEntry[];

    constructor(props: Props) {
        super(props);
        this.state = {};
        this.update();
    }

    componentDidUpdate(prevProps: Props) {
        this.update();
    }

    update() {
        const { hosts } = this.props;
        this.hostGroups = makeHostGroups(hosts);
        this.stats = makeStats(hosts);
    }

    render() {
        const { instanceId, hosts } = this.props;

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
                                            data={this.stats}
                                            uniqueHosts={countUniqueHosts(hosts)}
                                            onClick={(s) => this.setState({ selectedStatus: s })}
                                        />
                                    </Grid.Column>
                                    <Grid.Column>
                                        <AnsibleHostList
                                            instanceId={instanceId}
                                            hosts={hosts}
                                            hostGroups={this.hostGroups}
                                            selectedStatus={this.state.selectedStatus}
                                        />
                                    </Grid.Column>
                                </Grid>
                            </Tab.Pane>
                        )
                    },
                    {
                        menuItem: 'Failures',
                        render: () => (
                            <Tab.Pane attached={true}>
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
