/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { Menu, Message } from 'semantic-ui-react';

import { APITokenList, RedirectButton } from '../../organisms/index';

class APITokensListPage extends React.Component {
    render() {
        return (
            <>
                <Menu secondary={true} pointing={true}>
                    <Menu.Item position={'left'}>
                        <h4>API Tokens</h4>
                    </Menu.Item>

                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="New token"
                            location={`/profile/api-token/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <Message warning={true}>
                    Tokens are not stored and cannot be restored - only recreated.
                </Message>

                <APITokenList />
            </>
        );
    }
}

export default APITokensListPage;
