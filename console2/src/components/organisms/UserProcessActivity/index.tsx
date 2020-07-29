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

import { ConcordKey } from '../../../api/common';
import { UserProcessStats, UserProcessByOrgCards } from '../../molecules';
import {
    getActivity as apiGetActivity,
    ProjectProcesses,
    UserActivity
} from '../../../api/service/console/user';
import { Header } from 'semantic-ui-react';
import { MAX_CARD_ITEMS, OrgProjects } from '../../molecules/UserProcessByOrgCards';
import { ProcessList } from '../../molecules/index';
import { StatusCount } from '../../molecules/UserProcessStats';
import { ProcessEntry, ProcessStatus } from '../../../api/process';
import { comparators } from '../../../utils';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    REPO_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    STATUS_COLUMN
} from '../../molecules/ProcessList';
import { useCallback, useEffect, useState } from 'react';
import { useApi } from '../../../hooks/useApi';
import { LoadingDispatch } from '../../../App';
import RequestErrorActivity from '../RequestErrorActivity';

export interface ExternalProps {
    forceRefresh: any;
}

const MAX_OWN_PROCESSES = 10;

const DEFAULT_STATS: StatusCount[] = [
    { status: ProcessStatus.ENQUEUED, count: -1 },
    { status: ProcessStatus.RUNNING, count: -1 },
    { status: ProcessStatus.SUSPENDED, count: -1 },
    { status: ProcessStatus.FINISHED, count: -1 },
    { status: ProcessStatus.FAILED, count: -1 }
];

const statusOrder = [
    ProcessStatus.RUNNING,
    ProcessStatus.SUSPENDED,
    ProcessStatus.FINISHED,
    ProcessStatus.FAILED
];

const UserProcesses = ({ forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const [processStats, setProcessStats] = useState<StatusCount[]>(DEFAULT_STATS);
    const [processByOrg, setProcessByOrg] = useState<OrgProjects[]>();
    const [processes, setProcesses] = useState<ProcessEntry[]>();

    const fetchData = useCallback(() => {
        return apiGetActivity(MAX_CARD_ITEMS + 1, MAX_OWN_PROCESSES);
    }, []);

    const { data, error } = useApi<UserActivity>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    useEffect(() => {
        if (!data) {
            return;
        }

        setProcessStats(makeProcessStatsList(data?.processStats));
        setProcessByOrg(makeProcessByOrgList(data?.orgProcesses));
        setProcesses(data?.processes);
    }, [data]);

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <Header dividing={true} as="h3" style={{ marginTop: 0 }}>
                Your processes:
            </Header>
            <UserProcessStats items={processStats} />

            <Header dividing={true} as="h3">
                Running projects:
            </Header>
            <UserProcessByOrgCards items={processByOrg} />

            <Header dividing={true} as="h3">
                Your last {MAX_OWN_PROCESSES} processes:
            </Header>
            <ProcessList
                data={processes}
                columns={[
                    STATUS_COLUMN,
                    INSTANCE_ID_COLUMN,
                    PROJECT_COLUMN,
                    REPO_COLUMN,
                    INITIATOR_COLUMN,
                    CREATED_AT_COLUMN
                ]}
            />
        </>
    );
};

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

const makeProcessStatsList = (processStats?: {
    status: ProcessStatus;
    count: number;
}): StatusCount[] => {
    if (processStats === undefined) {
        return DEFAULT_STATS;
    }

    return Object.keys(processStats)
        .map((k) => ({ status: k as ProcessStatus, count: processStats[k] }))
        .sort((a, b) => {
            const i1 = statusOrder.indexOf(a.status);
            const i2 = statusOrder.indexOf(b.status);
            return i1 - i2;
        });
};

export default UserProcesses;
//
// const mapStateToProps = ({ userActivity }: { userActivity: State }): StateProps => ({
//     loading: userActivity.loading,
//     error: userActivity.error,
//     userActivity: userActivity.getUserActivity.response
//         ? userActivity.getUserActivity.response.activity
//             ? userActivity.getUserActivity.response.activity
//             : undefined
//         : undefined
// });
//
// const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
//     load: () => dispatch(actions.getUserActivity(MAX_CARD_ITEMS + 1, MAX_OWN_PROCESSES))
// });
