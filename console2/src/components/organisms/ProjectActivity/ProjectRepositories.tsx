/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import { RedirectButton, RequestErrorActivity } from '../index';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import {useCallback, useMemo, useRef, useState} from 'react';
import {
    list as apiRepositoryList, listTriggersV2 as apiListTriggers,
    PaginatedRepositoryEntries, TriggerEntry,
} from '../../../api/org/project/repository';
import { Input, Menu } from 'semantic-ui-react';
import { PaginationToolBar, RepositoryList, RequestErrorMessage } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    forceRefresh: any;
}

const ProjectRepositories = ({ orgName, projectName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset,
    } = usePagination();
    const oldFilter = useRef<string>();

    const [filter, setFilter] = useState<string>();

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    const fetchData = useCallback(() => {
        if (filter && oldFilter.current !== filter) {
            oldFilter.current = filter;
            resetOffset(0);
        }

        return apiRepositoryList(
            orgName,
            projectName,
            paginationFilter.offset,
            paginationFilter.limit,
            filter
        );
    }, [
        orgName,
        projectName,
        filter,
        paginationFilter.offset,
        paginationFilter.limit,
        resetOffset,
    ]);

    const fetchTriggers = useCallback(() => {
        return apiListTriggers({
            type: 'manual',
            orgName: orgName,
            projectName: projectName
        });

    }, [
        orgName,
        projectName
    ]);

    const { data, isLoading, error } = useApi<PaginatedRepositoryEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: (forceRefresh ? 1 : 0) + (refresh ? 10 : 0),
        debounceTime: 1000,
        dispatch,
    });

    const triggerInfo = useApi<TriggerEntry[]>(fetchTriggers, {
        fetchOnMount: true,
        forceRequest: (forceRefresh ? 1 : 0 ) + (refresh ? 10 : 0),
        dispatch
    });

    const repoTriggerMap = useMemo(()=>{
        const mapData : {[id: string] : TriggerEntry[]} = {};
        if (Array.isArray(triggerInfo.data)) {
            for (let triggerData of triggerInfo.data) {
                const triggerKey = triggerData.repositoryId;
                if(!Array.isArray(mapData[triggerKey])) {
                    mapData[triggerKey] = [ triggerData ];
                } else {
                    mapData[triggerKey ].push(triggerData);
                }
            }
        }
        return mapData;
    }, [triggerInfo.data]);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    if (triggerInfo.error) {
        return <RequestErrorMessage error={triggerInfo.error} />;
    }

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
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="Add repository"
                            location={`/org/${orgName}/project/${projectName}/repository/_new`}
                        />
                    </Menu.Item>

                    <Menu.Item style={{ padding: 0 }}>
                        <PaginationToolBar
                            limit={paginationFilter.limit}
                            handleLimitChange={(limit) => handleLimitChange(limit)}
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

            <RepositoryList
                orgName={orgName}
                projectName={projectName}
                data={data?.items}
                triggerMap={repoTriggerMap}
                loading={isLoading}
                refresh={refreshHandler}
            />
        </>
    );
};

export default ProjectRepositories;
