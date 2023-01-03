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
import { useCallback, useContext, useState } from 'react';
import { Input, List, Menu } from 'semantic-ui-react';
import { Link } from 'react-router-dom';

import { ConcordKey } from '../../../api/common';
import { CreateNewEntityButton, PaginationToolBar } from '../../molecules';
import { Organizations } from '../../../state/data/orgs/types';
import {
    list as apiList,
    PaginatedStorageEntries,
    StorageVisibility
} from '../../../api/org/jsonstore';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { UserSessionContext } from '../../../session';
import { LoadingDispatch } from '../../../App';

interface Props {
    orgName: ConcordKey;
    forceRefresh: any;
}

export default ({ orgName, forceRefresh }: Props) => {
    const dispatch = React.useContext(LoadingDispatch);

    const [filter, setFilter] = useState<string>();
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst
    } = usePagination();

    const fetchData = useCallback(() => {
        return apiList(orgName, paginationFilter.offset, paginationFilter.limit, filter);
    }, [orgName, paginationFilter.offset, paginationFilter.limit, filter]);

    const { data, error } = useApi<PaginatedStorageEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const { userInfo } = useContext(UserSessionContext);

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item>
                    <Input
                        icon="search"
                        placeholder="Filter..."
                        onChange={(ev, data) => setFilter(data.value)}
                    />
                </Menu.Item>

                <Menu.Menu position={'right'}>
                    <Menu.Item>
                        <CreateNewEntityButton
                            entity="jsonstore"
                            title="New store"
                            orgName={orgName}
                            userInOrg={isUserOrgMember(orgName, userInfo!.orgs)}
                            enabledInPolicy={true}
                        />
                    </Menu.Item>
                    <Menu.Item style={{ padding: 0 }}>
                        <PaginationToolBar
                            limit={paginationFilter.limit}
                            handleLimitChange={handleLimitChange}
                            handleNext={handleNext}
                            handlePrev={handlePrev}
                            handleFirst={handleFirst}
                            disablePrevious={paginationFilter.offset <= 0}
                            disableNext={!data?.next}
                            disableFirst={paginationFilter.offset <= 0}
                        />
                    </Menu.Item>
                </Menu.Menu>
            </Menu>

            {error && <RequestErrorActivity error={error} />}
            {data?.items.length === 0 && <h3>No JSON stores defined</h3>}

            <List divided={true} relaxed={true} size="large">
                {data?.items.map((s, idx) => (
                    <List.Item key={idx}>
                        <List.Icon
                            name={s.visibility === StorageVisibility.PRIVATE ? 'lock' : 'unlock'}
                            color="grey"
                        />
                        <List.Content>
                            <List.Header as={Link} to={`/org/${orgName}/jsonstore/${s.name}`}>
                                {s.name}
                            </List.Header>
                            <List.Description>
                                {s.owner ? `Owner: ${s.owner.username}` : ''}
                            </List.Description>
                        </List.Content>
                    </List.Item>
                ))}
            </List>
        </>
    );
};

const isUserOrgMember = (orgName: string, userOrgs: Organizations) => {
    return (
        Object.keys(userOrgs)
            .map((k) => userOrgs[k])
            .filter((org) => org.name === orgName).length > 0
    );
};
