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
import { Menu, Modal } from 'semantic-ui-react';
import ConnectedAnsibleTaskList from '../AnsibleTaskList';

export class HostnameListItem extends React.Component {
    render() {
        const { hostname, selectedHost, setSelectedHostFn } = this.props;

        return (
            <Modal
                trigger={
                    <Menu.Item
                        name={hostname}
                        active={selectedHost === hostname}
                        onClick={() => setSelectedHostFn(hostname)}>
                        {hostname}
                    </Menu.Item>
                }
                basic
                size="fullscreen">
                <Modal.Content>
                    <ConnectedAnsibleTaskList />
                </Modal.Content>
            </Modal>
        );
    }
}

export default HostnameListItem;
