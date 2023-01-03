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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Loader } from 'semantic-ui-react';
import { ConcordKey, RequestError } from '../../../api/common';
import { ResourceAccessEntry } from '../../../api/org';
import { actions, State, selectors } from '../../../state/data/projects';
import { RequestErrorMessage, TeamAccessList } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

interface StateProps {
    error: RequestError;
    updating: boolean;
    loading: boolean;
    entries: ResourceAccessEntry[];
}

interface DispatchProps {
    load: (orgName: ConcordKey, projectName: ConcordKey) => void;
    update: (orgName: ConcordKey, projectName: ConcordKey, entries: ResourceAccessEntry[]) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectTeamAccessActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.init();
    }
    init() {
        const { orgName, projectName, load } = this.props;
        load(orgName, projectName);
    }
    render() {
        const { error, entries, update, orgName, projectName, updating, loading } = this.props;

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
                    submit={(accessEntries) => update(orgName, projectName, accessEntries)}
                />
            </div>
        );
    }
}

const mapStateToProps = (
    { projects }: { projects: State },
    { orgName, projectName }: ExternalProps
): StateProps => ({
    entries: selectors.projectAccesTeams(projects, orgName, projectName),
    error: projects.updateProjectTeamAccess.error,
    updating: projects.updateProjectTeamAccess.running,
    loading: projects.projectTeamAccess.running
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (orgName, projectName) => {
        dispatch(actions.getTeamAccess(orgName, projectName));
    },
    update: (orgName, projectName, data) => {
        dispatch(actions.updateTeamAccess(orgName, projectName, data));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectTeamAccessActivity);
