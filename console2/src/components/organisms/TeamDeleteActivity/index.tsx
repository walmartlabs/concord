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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/teams';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    teamName: ConcordKey;
}

interface StateProps {
    deleting: boolean;
    error: RequestError;
}

interface DispatchProps {
    reset: () => void;
    deleteTeam: (orgName: ConcordKey, teamName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class TeamDeleteActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.reset();
    }

    render() {
        const { error, deleting, orgName, teamName, deleteTeam } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <ButtonWithConfirmation
                    primary={true}
                    negative={true}
                    content="Delete"
                    loading={deleting}
                    confirmationHeader="Delete the team?"
                    confirmationContent="Are you sure you want to delete the team?"
                    onConfirm={() => deleteTeam(orgName, teamName)}
                    data-testid="team-delete-button"
                />
            </>
        );
    }
}

const mapStateToProps = ({ teams }: { teams: State }): StateProps => ({
    deleting: teams.deleteTeam.running,
    error: teams.deleteTeam.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    deleteTeam: (orgName, teamName) => dispatch(actions.deleteTeam(orgName, teamName))
});

export default connect(mapStateToProps, mapDispatchToProps)(TeamDeleteActivity);
