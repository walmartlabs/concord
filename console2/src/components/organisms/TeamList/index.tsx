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
import { Link } from 'react-router-dom';
import { List, Loader } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { TeamEntry } from '../../../api/org/team';
import { actions, State } from '../../../state/data/teams';
import { Teams } from '../../../state/data/teams/types';
import { comparators } from '../../../utils';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
}

interface StateProps {
    data: TeamEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (orgName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class TeamList extends React.PureComponent<Props> {
    constructor(props: Props) {
        super(props);
        this.state = { onlyCurrent: true };
    }

    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prepProps: Props) {
        if (this.props.orgName !== prepProps.orgName) {
            this.update();
        }
    }

    update() {
        const { load, orgName } = this.props;
        load(orgName);
    }

    render() {
        const { loading, data, error } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        return (
            <>
                {loading && <Loader active={true} />}

                <List divided={true} relaxed={true} size="large">
                    {data.map((team: TeamEntry, index: number) => (
                        <List.Item key={index}>
                            <List.Content>
                                <List.Header>
                                    <Link to={`/org/${team.orgName}/team/${team.name}`}>
                                        {team.name}
                                    </Link>
                                </List.Header>
                                {team.description && (
                                    <List.Description>{team.description}</List.Description>
                                )}
                            </List.Content>
                        </List.Item>
                    ))}
                </List>
            </>
        );
    }
}

// TODO refactor as a selector?
const makeTeamList = (data: Teams): TeamEntry[] => {
    if (!data) {
        return [];
    }

    return Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName);
};

const mapStateToProps = ({ teams }: { teams: State }): StateProps => ({
    data: makeTeamList(teams.teamById),
    loading: teams.list.running,
    error: teams.list.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (orgName: ConcordKey) => dispatch(actions.listTeams(orgName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(TeamList);
