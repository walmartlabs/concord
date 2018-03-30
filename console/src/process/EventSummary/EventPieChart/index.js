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
import { connect } from 'react-redux';
import { EventPieChart } from './EventPieChart';
import { ansible_event_summary as actions } from '../../actions';
import { selectors } from '../../reducers';

export class Connected_EventPieChart extends React.Component {
    render() {
        const { ansibleStats, setHostListFn } = this.props;

        return (
            <EventPieChart
                width={500}
                height={300}
                ansibleStats={ansibleStats}
                setHostListFn={(hosts) => {
                    setHostListFn(hosts);
                }}
            />
        );
    }
}

const mapStateToProps = ({ process }) => ({
    ansibleStats: selectors.ansibleStatsSelector(process),
    events: selectors.getEvents(process),
    eventsByType: selectors.getEventsByHostName
});

const mapDispatchToProps = (dispatch) => ({
    setHostListFn: (hosts) => dispatch(actions.set_host_list(hosts))
});

export default connect(mapStateToProps, mapDispatchToProps)(Connected_EventPieChart);
