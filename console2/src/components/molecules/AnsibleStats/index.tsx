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
import { Grid, Tab } from 'semantic-ui-react';
import { AnsibleEvent, AnsibleStatus, ProcessEventEntry } from '../../../api/process/event';

import { AnsibleHostList, AnsibleStatChart, AnsibleTaskList } from '../../molecules';
import { countUniqueHosts, getFailures, makeHostList, makeStats } from './data';

interface State {
    selectedStatus?: AnsibleStatus;
}

interface Props {
    events: Array<ProcessEventEntry<AnsibleEvent>>;
}

class AnsibleStats extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    render() {
        const { events } = this.props;

        // TODO calculate the data on update
        const hosts = makeHostList(events);
        const stats = makeStats(events);
        const failures = getFailures(events);

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
                                            data={stats}
                                            uniqueHosts={countUniqueHosts(hosts)}
                                            onClick={(s) => this.setState({ selectedStatus: s })}
                                        />
                                    </Grid.Column>
                                    <Grid.Column>
                                        <AnsibleHostList
                                            hosts={hosts}
                                            hostEventsFn={(host) =>
                                                events.filter((e) => e.data.host === host)
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
                                {failures.length > 0 && (
                                    <AnsibleTaskList showHosts={true} events={failures} />
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
