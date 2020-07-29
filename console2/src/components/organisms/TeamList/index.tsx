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
import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { List } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { list as apiList, TeamEntry } from '../../../api/org/team';
import { comparators } from '../../../utils';
import { useApi } from '../../../hooks/useApi';
import { LoadingDispatch } from '../../../App';
import { RequestErrorActivity } from '../index';

interface Props {
    orgName: ConcordKey;
    forceRefresh: any;
    filter?: string;
}

const makeTeamList = (data: TeamEntry[], filter?: string): TeamEntry[] =>
    data
        .filter((e) => (filter ? e.name.toLowerCase().indexOf(filter.toLowerCase()) >= 0 : true))
        .sort(comparators.byName);

export default ({ orgName, forceRefresh, filter }: Props) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiList(orgName);
    }, [orgName]);

    const { data, error } = useApi<TeamEntry[]>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <List divided={true} relaxed={true} size="large">
                {data?.length === 0 && <h3>No teams found</h3>}
                {data &&
                    makeTeamList(data, filter).map((team: TeamEntry, index: number) => (
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
};
