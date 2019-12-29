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
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Input, List, Loader, Menu, Radio } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { OrganizationEntry, OrganizationVisibility } from '../../../api/org';
import { list as getPaginatedOrgList } from '../../../api/org/index';
import { PaginationToolBar, RequestErrorMessage } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';

const OrganizationList = () => {
    const [data, setData] = useState<OrganizationEntry[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<RequestError>();
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset
    } = usePagination();
    const [next, setNext] = useState<boolean>(false);
    const oldFilter = useRef<string>();
    const oldOnlyCurrent = useRef<boolean>(true);

    const [filter, setFilter] = useState<string>();
    const [onlyCurrent, setOnlyCurrent] = useState(true);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);

            if (filter && oldFilter.current !== filter) {
                oldFilter.current = filter;
                resetOffset(0);
            }

            if (oldOnlyCurrent.current !== onlyCurrent) {
                oldOnlyCurrent.current = onlyCurrent;
                resetOffset(0);
            }

            const paginatedOrgList = await getPaginatedOrgList(
                onlyCurrent,
                paginationFilter.offset,
                paginationFilter.limit,
                filter
            );
            setData(paginatedOrgList.items);
            setNext(paginatedOrgList.next);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }, [onlyCurrent, filter, paginationFilter.offset, paginationFilter.limit, resetOffset]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

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

                <Menu.Item position="right">
                    <Radio
                        label="Show only user's organizations"
                        toggle={true}
                        checked={onlyCurrent}
                        onChange={(ev, { checked }) => setOnlyCurrent(checked!)}
                    />
                </Menu.Item>

                <PaginationToolBar
                    filterProps={paginationFilter}
                    handleLimitChange={(limit) => handleLimitChange(limit)}
                    handleNext={handleNext}
                    handlePrev={handlePrev}
                    handleFirst={handleFirst}
                    disablePrevious={paginationFilter.offset === 0}
                    disableNext={!next}
                    disableFirst={paginationFilter.offset === 0}
                />
            </Menu>

            {error && <RequestErrorMessage error={error} />}
            {loading && <Loader active={true} />}
            {data.length === 0 && <h3>No organizations found</h3>}

            <List divided={true} relaxed={true} size="large">
                {data.map((org, idx) => (
                    <List.Item key={idx}>
                        <List.Icon
                            name={
                                org.visibility === OrganizationVisibility.PRIVATE
                                    ? 'lock'
                                    : 'unlock'
                            }
                            color="grey"
                        />
                        <List.Content>
                            <List.Header as={Link} to={`/org/${org.name}`}>
                                {org.name}
                            </List.Header>
                            <List.Description>
                                {org.owner ? `Owner: ${org.owner.username}` : ''}
                            </List.Description>
                        </List.Content>
                    </List.Item>
                ))}
            </List>
        </>
    );
};

export default OrganizationList;
