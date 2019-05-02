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

import { ConcordId, RequestError } from '../../../api/common';
import { NewTeamLdapGroupEntry, TeamLdapGroupEntry } from '../../../api/org/team';
import { actions, State } from '../../../state/data/teams';
import { comparators } from '../../../utils';
import { RequestErrorMessage, TeamLdapGroupList } from '../../molecules';

interface ExternalProps {
    orgName: ConcordId;
    teamName: ConcordId;
}

interface StateProps {
    data: TeamLdapGroupEntry[];
    loading: boolean;
    loadError: RequestError;
    updating: boolean;
    updateError: RequestError;
}

interface DispatchProps {
    reset: () => void;
    load: (orgName: ConcordId, teamName: ConcordId) => void;
    update: (orgName: ConcordId, teamName: ConcordId, users: NewTeamLdapGroupEntry[]) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class TeamLdapGroupListActivity extends React.PureComponent<Props> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prepProps: Props) {
        if (
            this.props.orgName !== prepProps.orgName ||
            this.props.teamName !== prepProps.teamName
        ) {
            this.init();
        }
    }

    init() {
        const { reset, load, orgName, teamName } = this.props;
        reset();
        load(orgName, teamName);
    }

    render() {
        const {
            loading,
            loadError,
            data,
            updateError,
            orgName,
            teamName,
            update,
            updating
        } = this.props;

        if (loadError) {
            return <RequestErrorMessage error={loadError} />;
        }

        if (loading) {
            return <Loader active={true} />;
        }

        return (
            <>
                {updateError && <RequestErrorMessage error={updateError} />}

                <TeamLdapGroupList
                    data={data}
                    submitting={updating}
                    submit={(users) => update(orgName, teamName, users)}
                />
            </>
        );
    }
}

const getLdapGroups = ({ listLdapGroups }: State): TeamLdapGroupEntry[] => {
    if (!listLdapGroups.response) {
        return [];
    }

    if (!listLdapGroups.response.items) {
        return [];
    }

    const items = listLdapGroups.response.items;
    return items
        .sort(comparators.byProperty((i) => i.role))
        .sort(comparators.byProperty((i) => i.group));
};

const mapStateToProps = ({ teams }: { teams: State }): StateProps => ({
    data: getLdapGroups(teams),
    loading: teams.listLdapGroups.running,
    loadError: teams.listLdapGroups.error,
    updating: teams.replaceLdapGroups.running,
    updateError: teams.replaceLdapGroups.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    load: (orgName, teamName) => dispatch(actions.listTeamLdapGroups(orgName, teamName)),
    update: (orgName, teamName, users) =>
        dispatch(actions.replaceLdapGroups(orgName, teamName, users))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(TeamLdapGroupListActivity);
