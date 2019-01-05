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
import { ProjectList, RedirectButton } from '../../organisms';
import { connect, DispatchProp } from 'react-redux';
import { State as SessionState } from '../../../state/session';
import { Organizations } from '../../../state/data/orgs/types';

interface State {
    filter?: string;
}

interface ExternalProps {
    orgName: ConcordKey;
}

interface UserProps {
    orgs: Organizations;
}

type Props = ExternalProps & UserProps & DispatchProp<any>;

class ProjectListActivity extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    isUserOrgMember = (orgName: string) => {
        const userOrgs = this.props.orgs;

        return (
            Object.keys(userOrgs)
                .map((k) => userOrgs[k])
                .filter((org) => org.name === orgName).length > 0
        );
    };

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
                        <RedirectButton
                            disabled={!this.isUserOrgMember(orgName)}
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="New project"
                            location={`/org/${orgName}/project/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <ProjectList orgName={orgName} filter={this.state.filter} />
            </>
        );
    }
}
const mapStateToProps = ({ session }: { session: SessionState }): UserProps => ({
    orgs: session.user.orgs ? session.user.orgs : {}
});
export default connect(mapStateToProps)(ProjectListActivity);
