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
import { AnsibleTaskList } from './AnsibleTaskList';
import { selectors } from '../../reducers';

export class Connected_AnsibleTaskList extends React.Component {
    render() {
        const { selectedHost, eventsByHost } = this.props;
        return selectedHost ? (
            <AnsibleTaskList title={selectedHost} tasks={eventsByHost[selectedHost]} />
        ) : null;
    }
}

const mapStateToProps = ({ process }) => ({
    selectedHost: selectors.ansibleEventSummary.selectedHost(process),
    eventsByHost: selectors.getEventsByHostName(process)
});

const mapDispatchToProps = (dispatch) => ({});

export default connect(mapStateToProps, mapDispatchToProps)(Connected_AnsibleTaskList);
