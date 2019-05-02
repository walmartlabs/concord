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
import { Loader } from 'semantic-ui-react';
import { ConcordKey, RequestError } from '../../../api/common';
import { ResourceAccessEntry } from '../../../api/org';

import { actions, State, selectors } from '../../../state/data/secrets';
import { RequestErrorMessage, TeamAccessList } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

interface StateProps {
    error: RequestError;
    updating: boolean;
    loading: boolean;
    entries: ResourceAccessEntry[];
}

interface DispatchProps {
    load: (orgName: ConcordKey, secretName: ConcordKey) => void;
    update: (orgName: ConcordKey, secretName: ConcordKey, entries: ResourceAccessEntry[]) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class SecretTeamAccessActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.init();
    }
    init() {
        const { orgName, secretName, load } = this.props;
        load(orgName, secretName);
    }
    render() {
        const { error, entries, update, orgName, secretName, updating, loading } = this.props;

        if (loading || updating) {
            return <Loader active={true} />;
        }

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        return (
            <div>
                <TeamAccessList
                    data={entries}
                    submitting={updating}
                    orgName={orgName}
                    submit={(data) => update(orgName, secretName, data)}
                />
            </div>
        );
    }
}

const mapStateToProps = (
    { secrets }: { secrets: State },
    { orgName, secretName }: ExternalProps
): StateProps => ({
    entries: selectors.secretAccesTeams(secrets, orgName, secretName),
    error: secrets.secretTeamAccess.error,
    updating: secrets.updateSecretTeamAccess.running,
    loading: secrets.secretTeamAccess.running
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (orgName, secretName) => {
        dispatch(actions.SecretTeamAccess(orgName, secretName));
    },
    update: (orgName, secretName, data) => {
        dispatch(actions.updateSecretTeamAccess(orgName, secretName, data));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SecretTeamAccessActivity);
