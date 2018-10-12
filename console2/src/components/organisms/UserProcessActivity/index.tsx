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

import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/userActivity';
import { RequestErrorMessage, UserProcessStats, UserProcessByOrgCards } from '../../molecules';
import { ProjectProcesses, UserActivity } from '../../../api/service/console/user';
import { Header, Loader } from 'semantic-ui-react';
import { MAX_CARD_ITEMS, OrgProjects } from '../../molecules/UserProcessByOrgCards';
import { ProcessList } from '../../molecules/index';
import { StatusCount } from '../../molecules/UserProcessStats';
import { ProcessStatus } from '../../../api/process';
import { comparators } from '../../../utils';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    REPO_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    UPDATED_AT_COLUMN
} from '../../molecules/ProcessList';

const MAX_OWN_PROCESSES = 10;

interface SessionProps {
    userName?: string;
}

interface StateProps {
    userActivity?: UserActivity;
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (orgName?: string, projectName?: string) => void;
}

const sort = (orgProcess: ProjectProcesses[]): ProjectProcesses[] =>
    orgProcess.sort(comparators.byProperty((i) => i.projectName));

const makeProcessByOrgList = (orgProcesses?: {
    orgName: ConcordKey;
    processes: ProjectProcesses[];
}): OrgProjects[] => {
    if (orgProcesses) {
        return Object.keys(orgProcesses)
            .map((k) => ({ orgName: k, projects: sort(orgProcesses[k]) }))
            .sort((a, b) => b.projects.length - a.projects.length);
    } else {
        return [];
    }
};

const statusOrder = [
    ProcessStatus.RUNNING,
    ProcessStatus.SUSPENDED,
    ProcessStatus.FINISHED,
    ProcessStatus.FAILED
];

const makeProcessStatsList = (processStats: {
    status: ProcessStatus;
    count: number;
}): StatusCount[] =>
    Object.keys(processStats)
        .map((k) => ({ status: k as ProcessStatus, count: processStats[k] }))
        .sort((a, b) => {
            const i1 = statusOrder.indexOf(a.status);
            const i2 = statusOrder.indexOf(b.status);
            return i1 - i2;
        });

class UserProcesses extends React.PureComponent<SessionProps & StateProps & DispatchProps> {
    componentDidMount() {
        this.props.load();
    }

    render() {
        const { userActivity, loading, error, load } = this.props;
        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !userActivity) {
            return <Loader active={true} />;
        }

        const { processStats, orgProcesses, processes } = userActivity;

        return (
            <>
                <Header dividing={true} as="h3">
                    Your processes:
                </Header>
                <UserProcessStats items={makeProcessStatsList(processStats)} />

                <Header dividing={true} as="h3">
                    Running projects:
                </Header>
                <UserProcessByOrgCards items={makeProcessByOrgList(orgProcesses)} />

                <Header dividing={true} as="h3">
                    Your last {MAX_OWN_PROCESSES} processes:
                </Header>
                <ProcessList
                    data={processes}
                    refresh={load}
                    columns={[
                        INSTANCE_ID_COLUMN,
                        PROJECT_COLUMN,
                        REPO_COLUMN,
                        INITIATOR_COLUMN,
                        CREATED_AT_COLUMN,
                        UPDATED_AT_COLUMN
                    ]}
                />
            </>
        );
    }
}

const mapStateToProps = ({ userActivity }: { userActivity: State }): StateProps => ({
    loading: userActivity.loading,
    error: userActivity.error,
    userActivity: userActivity.getUserActivity.response
        ? userActivity.getUserActivity.response.activity
            ? userActivity.getUserActivity.response.activity
            : undefined
        : undefined
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: () => dispatch(actions.getUserActivity(MAX_CARD_ITEMS + 1, MAX_OWN_PROCESSES))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(UserProcesses);
