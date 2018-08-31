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
import { Input, Menu } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { RedirectButton, TeamList } from '../../organisms';

interface State {
    filter?: string;
}

interface Props {
    orgName: ConcordKey;
}

class TeamListActivity extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    render() {
        const { orgName } = this.props;

        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item>
                        <Input
                            icon="search"
                            placeholder="Filter..."
                            onChange={(ev, data) => this.setState({ filter: data.value })}
                        />
                    </Menu.Item>

                    <Menu.Item position={'right'}>
                        <Menu.Item position={'right'}>
                            <RedirectButton
                                icon="plus"
                                positive={true}
                                labelPosition="left"
                                content="New team"
                                location={`/org/${orgName}/team/_new`}
                            />
                        </Menu.Item>
                    </Menu.Item>
                </Menu>

                <TeamList orgName={orgName} filter={this.state.filter} />
            </>
        );
    }
}

export default TeamListActivity;
