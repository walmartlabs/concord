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
import { AnsibleEvent, AnsibleStatus, ProcessEventEntry } from '../../../api/process/event';

import { AnsibleHostList, AnsibleStatChart, AnsibleTaskList } from '../../molecules';
import { AnsibleHostListEntry } from '../AnsibleHostList';
import { AnsibleStatChartEntry } from '../AnsibleStatChart';
import { countUniqueHosts, getFailures, makeHostList, makeHostGroups, makeStats } from './data';

interface State {
    selectedStatus?: AnsibleStatus;
}

interface Props {
    events: Array<ProcessEventEntry<AnsibleEvent>>;
}

class AnsibleStats extends React.Component<Props, State> {
    private hosts: AnsibleHostListEntry[];
    private hostGroups: string[];
    private stats: AnsibleStatChartEntry[];
    private failures: Array<ProcessEventEntry<AnsibleEvent>>;

    constructor(props: Props) {
        super(props);
        this.state = {};
        this.update();
    }

    componentDidUpdate(prevProps: Props) {
        this.update();
    }

    update() {
        const { events } = this.props;
        this.hosts = makeHostList(events);
        this.hostGroups = makeHostGroups(events);
        this.stats = makeStats(events);
        this.failures = getFailures(events);
    }

    render() {
        const { events } = this.props;

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
                                            uniqueHosts={countUniqueHosts(this.hosts)}
                                            onClick={(s) => this.setState({ selectedStatus: s })}
                                        />
                                    </Grid.Column>
                                    <Grid.Column>
                                        <AnsibleHostList
                                            events={events}
                                            hosts={this.hosts}
                                            hostGroups={this.hostGroups}
                                            hostEventsFn={(evs, host, hostGroup?) =>
                                                evs.filter(
                                                    (e) =>
                                                        e.data.host === host &&
                                                        (!hostGroup ||
                                                            e.data.hostGroup === hostGroup)
                                                )
                                            }
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
                                {this.failures.length > 0 && (
                                    <AnsibleTaskList
                                        showHosts={true}
                                        events={this.failures}
                                        filter={(evs) => evs}
                                    />
                                )}
                            </Tab.Pane>
                        )
                    }
                ]}
            />
        );
    }
}

export default AnsibleStats;
