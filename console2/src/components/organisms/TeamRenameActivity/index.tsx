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

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { isTeamExists } from '../../../api/service/console';
import { actions, State } from '../../../state/data/teams';
import { teamAlreadyExistsError } from '../../../validation';
import { EntityRenameForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    teamId: ConcordId;
    teamName: ConcordKey;
}

interface StateProps {
    renaming: boolean;
    error: RequestError;
}

interface DispatchProps {
    reset: () => void;
    rename: (orgName: ConcordKey, teamId: ConcordId, teamName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class TeamRenameActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.reset();
    }

    render() {
        const { error, renaming, orgName, teamId, teamName, rename } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <EntityRenameForm
                    originalName={teamName}
                    submitting={renaming}
                    onSubmit={(values) => rename(orgName, teamId, values.name)}
                    inputPlaceholder="Team name"
                    confirmationHeader="Rename the team?"
                    confirmationContent="Are you sure you want to rename the team?"
                    isExists={(name) => isTeamExists(orgName, name)}
                    alreadyExistsTemplate={teamAlreadyExistsError}
                    inputTestId="team-rename-input"
                    buttonTestId="team-rename-button"
                />
            </>
        );
    }
}

const mapStateToProps = ({ teams }: { teams: State }): StateProps => ({
    renaming: teams.rename.running,
    error: teams.rename.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    rename: (orgName, teamId, teamName) => dispatch(actions.renameTeam(orgName, teamId, teamName))
});

export default connect(mapStateToProps, mapDispatchToProps)(TeamRenameActivity);
