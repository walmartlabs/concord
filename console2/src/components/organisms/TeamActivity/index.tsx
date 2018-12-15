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
import { connect, Dispatch } from 'react-redux';
import { Link, Redirect, Route, Switch } from 'react-router-dom';
import { Divider, Header, Icon, Loader, Menu, Segment } from 'semantic-ui-react';
import { ConcordKey, RequestError } from '../../../api/common';
import { TeamEntry } from '../../../api/org/team';
import { actions, selectors, State } from '../../../state/data/teams';
import { RequestErrorMessage } from '../../molecules';
import { NotFoundPage } from '../../pages';
import { TeamDeleteActivity, TeamMemberListActivity, TeamRenameActivity } from '../index';

export type TabLink = 'members' | 'settings' | null;

interface ExternalProps {
    orgName: ConcordKey;
    teamName: ConcordKey;
    activeTab: TabLink;
}

interface StateProps {
    data?: TeamEntry;
    error: RequestError;
    loading: boolean;
}

interface DispatchProps {
    load: (orgName: ConcordKey, teamName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class TeamActivity extends React.PureComponent<Props> {
    static renderSetting(t: TeamEntry) {
        return (
            <>
                <Divider horizontal={true} content="Danger Zone" />

                <Segment color="red">
                    <Header as="h4">Team name</Header>
                    <TeamRenameActivity orgName={t.orgName} teamId={t.id} teamName={t.name} />

                    <Header as="h4">Delete team</Header>
                    <TeamDeleteActivity orgName={t.orgName} teamName={t.name} />
                </Segment>
            </>
        );
    }

    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName, teamName: newTeamName } = this.props;
        const { orgName: oldOrgName, teamName: oldTeamName } = prevProps;

        if (oldOrgName !== newOrgName || oldTeamName !== newTeamName) {
            this.init();
        }
    }

    init() {
        const { orgName, teamName, load } = this.props;
        load(orgName, teamName);
    }

    render() {
        const { loading, error, data } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !data) {
            return <Loader active={true} />;
        }

        const { orgName, teamName, activeTab } = this.props;
        const baseUrl = `/org/${orgName}/team/${teamName}`;

        return (
            <>
                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'members'}>
                        <Icon name="users" />
                        <Link to={`/org/${orgName}/team/${teamName}/members`}>Members</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'settings'}>
                        <Icon name="setting" />
                        <Link to={`/org/${orgName}/team/${teamName}/settings`}>Settings</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={baseUrl} exact={true}>
                        <Redirect to={`${baseUrl}/members`} />
                    </Route>

                    <Route path={`${baseUrl}/members`} exact={true}>
                        <TeamMemberListActivity orgName={orgName} teamName={teamName} />
                    </Route>
                    <Route path={`${baseUrl}/settings`} exact={true}>
                        <h1>{TeamActivity.renderSetting(data)}</h1>
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

const mapStateToProps = (
    { teams }: { teams: State },
    { orgName, teamName }: ExternalProps
): StateProps => ({
    data: selectors.teamByName(teams, orgName, teamName),
    loading: teams.get.running,
    error: teams.get.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (orgName, teamName) => dispatch(actions.getTeam(orgName, teamName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(TeamActivity);
