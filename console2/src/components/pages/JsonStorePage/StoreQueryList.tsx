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
import { useCallback, useState } from 'react';
import { Button, Input, List, Menu, InputOnChangeData } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { PaginationToolBar } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import {
    listStorageQuery as apiGet,
    PaginatedStorageQueryEntries
} from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import StoreQueryDeleteActivity from './StoreQueryDeleteActivity';
import RedirectButton from '../../organisms/RedirectButton';
import { LoadingDispatch } from '../../../App';

import './styles.css';

export interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    forceRefresh: any;
}

const StoreQueryList = ({ orgName, storeName, forceRefresh }: ExternalProps) => {
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
        return apiGet(orgName, storeName, paginationFilter.offset, paginationFilter.limit, filter);
    }, [orgName, storeName, paginationFilter, filter]);

    const { data, error, clearState, fetch } = useApi<PaginatedStorageQueryEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const filterChangeHandler = useCallback(
        (event: React.ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => {
            setFilter(data.value);
        },
        []
    );

    const storageQueryDeleteTrigger = useCallback((onClick: any) => {
        return (
            <Button
                size={'mini'}
                primary={true}
                negative={true}
                onClick={(event) => {
                    event.stopPropagation();
                    event.preventDefault();
                    onClick(event);
                }}>
                Delete
            </Button>
        );
    }, []);

    const deleteHandler = useCallback(() => {
        //TODO: reset pagination?
        clearState();
        fetch();
    }, [clearState, fetch]);

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item>
                    <Input
                        icon="search"
                        placeholder="Filter..."
                        onChange={filterChangeHandler}
                        disabled={data === undefined}
                    />
                </Menu.Item>

                <Menu.Menu position={'right'}>
                    <Menu.Item>
                        <RedirectButton
                            disabled={false}
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content={`New query`}
                            location={`/org/${orgName}/jsonstore/${storeName}/query/_new`}
                        />
                    </Menu.Item>

                    <Menu.Item style={{ padding: 0 }}>
                        <PaginationToolBar
                            filterProps={paginationFilter}
                            handleLimitChange={(limit) => handleLimitChange(limit)}
                            handleNext={handleNext}
                            handlePrev={handlePrev}
                            handleFirst={handleFirst}
                            disablePrevious={paginationFilter.offset === 0}
                            disableNext={data === undefined ? true : !data.next}
                            disableFirst={paginationFilter.offset === 0}
                            disabled={data === undefined}
                        />
                    </Menu.Item>
                </Menu.Menu>
            </Menu>

            {error && <RequestErrorActivity error={error} />}
            {data && data.items.length === 0 && <h3>No storage query items found</h3>}

            <List
                divided={true}
                relaxed={true}
                size="large"
                verticalAlign={'middle'}
                selection={true}>
                {data &&
                    data.items.map((d, idx) => (
                        <List.Item
                            key={idx}
                            as="a"
                            href={`#/org/${orgName}/jsonstore/${storeName}/query/${d.name}/edit`}
                            style={{ backgroundColor: idx % 2 === 0 ? '#F5F5F5' : '#FFFFFF' }}>
                            <List.Content floated="right">
                                <StoreQueryDeleteActivity
                                    orgName={orgName}
                                    storeName={storeName}
                                    storageQueryName={d.name}
                                    trigger={storageQueryDeleteTrigger}
                                    onDone={deleteHandler}
                                />
                            </List.Content>

                            <List.Content verticalAlign={'middle'} className="itemLink">
                                {d.name}
                            </List.Content>
                        </List.Item>
                    ))}
            </List>
        </>
    );
};

export default StoreQueryList;
