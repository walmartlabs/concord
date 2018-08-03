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
import { Link } from 'react-router-dom';
import { Dropdown, Image, Menu } from 'semantic-ui-react';

export type GlobalNavTab = 'activity' | 'process' | 'org' | null;

interface Props {
    activeTab: GlobalNavTab;
    userDisplayName?: string;
    openDocumentation: () => void;
    openAbout: () => void;
    openProfile: () => void;
    logOut: () => void;
}

class GlobalNavMenu extends React.PureComponent<Props> {
    render() {
        const {
            activeTab,
            userDisplayName,
            openDocumentation,
            openAbout,
            openProfile,
            logOut
        } = this.props;

        return (
            <Menu fluid={true} inverted={true} size="small" secondary={true}>
                <Menu.Item>
                    <Image src="/images/logo.svg" size="small" />
                </Menu.Item>
                <Menu.Item active={activeTab === 'activity'}>
                    <Link to="/activity">Activity</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'process'}>
                    <Link to="/process">Processes</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'org'}>
                    <Link to="/org">Organizations</Link>
                </Menu.Item>
                <Menu.Item position="right" fitted="vertically">
                    <Menu inverted={true} size="small" secondary={true}>
                        <Menu.Item as={Dropdown} text="System">
                            <Dropdown.Menu>
                                <Dropdown.Item
                                    icon="help"
                                    text="Documentation"
                                    onClick={() => openDocumentation()}
                                />
                                <Dropdown.Item
                                    icon="info"
                                    text="About"
                                    onClick={() => openAbout()}
                                />
                            </Dropdown.Menu>
                        </Menu.Item>
                        <Menu.Item as={Dropdown} text={userDisplayName}>
                            {/* TODO can't add an icon here */}
                            <Dropdown.Menu>
                                <Dropdown.Item
                                    icon="setting"
                                    text="Profile"
                                    onClick={() => openProfile()}
                                />
                                <Dropdown.Item
                                    icon="log out"
                                    text="Log out"
                                    onClick={() => logOut()}
                                />
                            </Dropdown.Menu>
                        </Menu.Item>
                    </Menu>
                </Menu.Item>
            </Menu>
        );
    }
}

export default GlobalNavMenu;
