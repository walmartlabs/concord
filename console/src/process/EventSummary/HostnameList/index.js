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
import HostnameList from './HostnameList';
import { selectors } from '../../reducers';
import { ansible_event_summary as actions } from '../../actions';

export class Connected_HostnameList extends React.Component {
    render() {
        const { hosts, selectedHost, setSelectedHostFn } = this.props;

        if (hosts.length > 0) {
            return (
                <HostnameList
                    hosts={hosts}
                    selectedHost={selectedHost}
                    setSelectedHostFn={setSelectedHostFn}
                />
            );
        } else {
            return null;
        }
    }
}

const mapStateToProps = ({ process }) => ({
    hosts: selectors.ansibleEventSummary.hosts(process),
    selectedHost: selectors.ansibleEventSummary.selectedHost(process)
});

const mapDispatchToProps = (dispatch) => ({
    setSelectedHostFn: (hostname) => dispatch(actions.set_selected_host(hostname))
});

export default connect(mapStateToProps, mapDispatchToProps)(Connected_HostnameList);
