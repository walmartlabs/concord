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
import { Header, Menu, Input } from 'semantic-ui-react';
import { HostnameListItem } from './HostnameListItem';

export class HostnameList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            filteredHosts: props.hosts
        };
    }

    componentWillReceiveProps(nextProps, nextState) {
        this.state = {
            filteredHosts: nextProps.hosts
        };
    }

    filterList(event, hosts) {
        var updatedList = hosts;
        if (event) {
            updatedList = updatedList.filter(function(item) {
                return item.toLowerCase().search(event.target.value.toLowerCase()) !== -1;
            });
        } else {
            updatedList = hosts;
        }
        this.setState({ filteredHosts: updatedList });
    }

    render() {
        const { hosts, status, selectedHost, setSelectedHostFn } = this.props;
        const { filteredHosts } = this.state;

        if (filteredHosts) {
            const HostList = filteredHosts.map((host, index) => {
                if (host !== 'undefined') {
                    return (
                        <HostnameListItem
                            hostname={host}
                            key={index}
                            selectedHost={selectedHost}
                            setSelectedHostFn={setSelectedHostFn}
                        />
                    );
                } else {
                    return null;
                }
            });
            return (
                <div>
                    <Menu attached="top">
                        <Menu.Item>
                            <Header as="h3">Hosts by Status {status ? status : ''}</Header>
                        </Menu.Item>
                        <Menu.Menu position="right">
                            <Menu.Item>
                                <Input
                                    type="text"
                                    icon="filter"
                                    size="small"
                                    placeholder="Search"
                                    onChange={(e) => this.filterList(e, hosts)}
                                />
                            </Menu.Item>
                        </Menu.Menu>
                    </Menu>
                    <Menu attached="bottom" vertical fluid>
                        {HostList}
                    </Menu>
                </div>
            );
        } else {
            return null;
        }
    }
}

export default HostnameList;
