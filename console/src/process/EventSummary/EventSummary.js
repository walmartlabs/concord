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
// @ts-check

import React, { Component } from 'react';
import { Header, Divider, Grid, Tab, Menu } from 'semantic-ui-react';
import ConnectedConcordEventList from './ConcordEventList';
import ConnectedEventPieChart from './EventPieChart';
import ConnectedHostnameList from './HostnameList';
import ConnectedAnsibleTaskList from './AnsibleTaskList';
import StatusOverview from './StatusOverview';

export class EventSummary extends Component {
    render() {
        const { eventsByType } = this.props;

        return (
            <div>
                <ConnectedConcordEventList />

                {eventsByType.ANSIBLE && (
                    <div>
                        <Divider horizontal section>
                            Ansible Stats
                        </Divider>
                        <Tab
                            panes={[
                                {
                                    menuItem: 'By hosts',
                                    render: () => (
                                        <Tab.Pane attached>
                                            <Header as="h2">
                                                <Header.Subheader>
                                                    Click the chart to reveal hosts filtered by
                                                    Ansible status, then select a hostname to see
                                                    it's aggregate results
                                                </Header.Subheader>
                                            </Header>
                                            <Grid stackable columns={2} centered>
                                                <Grid.Column>
                                                    <ConnectedEventPieChart />
                                                </Grid.Column>
                                                <Grid.Column>
                                                    <ConnectedHostnameList />
                                                </Grid.Column>
                                            </Grid>
                                            <Divider horizontal />
                                            <ConnectedAnsibleTaskList />
                                        </Tab.Pane>
                                    )
                                },
                                {
                                    menuItem: <Menu.Item key="messages">Failures</Menu.Item>,
                                    render: () => (
                                        <Tab.Pane attached>
                                            <StatusOverview />
                                        </Tab.Pane>
                                    )
                                }
                            ]}
                        />{' '}
                        {/* TODO: Failures should not show unless there are failures */}
                    </div>
                )}
            </div>
        );
    }
}
