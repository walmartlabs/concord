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
