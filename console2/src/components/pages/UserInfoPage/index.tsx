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
import {
    Menu
} from 'semantic-ui-react';

import {UserInfo} from '../../organisms/index';

class UserInfoPage extends React.Component {
    render() {
        return (
            <>
                <Menu secondary={true} pointing={true} style={{height: '48px'}}>
                    <Menu.Item position={'left'}>
                        <h4>User Info</h4>
                    </Menu.Item>
                </Menu>

                <UserInfo/>
            </>
        );
    }
}

export default UserInfoPage;
