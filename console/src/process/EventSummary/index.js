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

import React from 'react';
import { connect } from 'react-redux';
import { selectors } from '../reducers';
import * as actions from '../actions';

import { EventSummary } from './EventSummary';

export class Connected_EventSummary extends React.Component {
    componentDidMount() {
        const { instanceId, startPollingDataFn } = this.props;
        startPollingDataFn(instanceId);
    }

    componentWillUnmount() {
        const { stopPollingDataFn } = this.props;
        stopPollingDataFn();
    }

    render() {
        return <EventSummary {...this.props} />;
    }
}

const mapStateToProps = ({ process }) => ({
    eventsByType: selectors.getEventsByType(process),
    eventsByStatus: selectors.getEventsByStatus(process)
});

const mapDispatchToProps = (dispatch) => ({
    stopPollingDataFn: () => dispatch(actions.stopPolling()),
    startPollingDataFn: () => dispatch(actions.startPolling())
});

export default connect(mapStateToProps, mapDispatchToProps)(Connected_EventSummary);
