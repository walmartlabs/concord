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
import { NewTeamEntry } from '../../../api/org/team';
import { actions, State } from '../../../state/data/teams';
import { NewTeamForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
}

interface StateProps {
    error: RequestError;
    submitting: boolean;
}

interface DispatchProps {
    reset: () => void;
    submit: (orgName: ConcordKey, entry: NewTeamEntry) => void;
}

type Props = StateProps & ExternalProps & DispatchProps;

class NewTeamActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.reset();
    }

    render() {
        const { error, submitting, orgName, submit } = this.props;
        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <NewTeamForm
                    orgName={orgName}
                    submitting={submitting}
                    onSubmit={(values) => submit(orgName, values)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ teams }: { teams: State }): StateProps => ({
    error: teams.create.error,
    submitting: teams.create.running
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    submit: (orgName: ConcordKey, entry: NewTeamEntry) =>
        dispatch(actions.createTeam(orgName, entry))
});

export default connect(mapStateToProps, mapDispatchToProps)(NewTeamActivity);
