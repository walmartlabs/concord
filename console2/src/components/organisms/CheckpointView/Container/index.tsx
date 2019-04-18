/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import React, { useEffect, useState } from 'react';
import createContainer from 'constate';
import {
    ProcessEntry,
    PaginatedProcessEntries,
    list as apiList,
    ProcessListQuery
} from '../../../../api/process';
import { CheckpointGroup } from '../shared/types';
import { generateCheckpointGroups } from './checkpointUtils';
import { ProjectEntry } from '../../../../api/org/project';
import useQueryParams from './useQueryParams';
import { ColumnDefinition } from '../../../../api/org';

/**
 * Interface for the inital props passed to this container.
 */
export interface InitialProps {
    project: ProjectEntry;
    refreshInterval?: number;
}

/**
 * Custom React hook to isolate state and features specific to the Checkpoint page.
 * We are exporting this as a [constate](https://github.com/diegohaz/constate) container
 * Use with the useContext react hook
 *
 * TODO: This hook is probably too big at this point, think about refactoring into more composable hooks
 * TODO: Add Error handling and error states
 *
 * @param initial: InitialProps values you can pass in via a Context or Provider
 */
export const useCheckpoint = (initial: InitialProps) => {
    // currentPage for pagination
    const [currentPage, setPage] = useState(1);

    // limitPerPage is the limit of items we will show on a page at one time
    const [limitPerPage, setLimitPerPage] = useState(10);

    // loadingData boolean to tell us if data is loading or not
    const [loadingData, setLoadingData] = useState(false);

    // orgId comes from initial, represents a concord org Id
    const [orgId] = useState(initial.project!.orgId);

    // projectId comes from iniital, represents a concord project Id
    const [projectId] = useState(initial.project!.id);

    // project comes from iniital, represents a concord project
    const [project] = useState(initial.project!);

    // processes an array of ProcessEntries which we can map over to render components
    const [processes, setProcesses] = useState<ProcessEntry[]>([]);

    // checkpointGroups are a custom object correlating a processes checkpoints to timestamps
    const [checkpointGroups, setCheckpointGroups] = useState<{
        [key: string]: CheckpointGroup[];
    }>({});

    // the current query parameters existing
    const { queryParams, replaceQueryParams, getCurrentParams } = useQueryParams();

    /**
     * an active filter is represented by the source name of a ui config object
     * @see getConfigBySourceName to pull specific filter data
     * */
    const [activeFilters, setActiveFilters] = useState<{ [source: string]: string }>({
        ...queryParams
    });

    /**
     * Selector for the project meta configs
     * @return An array of configs or return empty object if no data is found
     */
    const getProjectUIConfigs = (): ColumnDefinition[] => {
        const project = initial.project;
        if (project.meta && project.meta.ui && project.meta.ui.processList) {
            return project.meta.ui.processList;
        }
        // No data to return
        return [];
    };

    /**
     * Selector for metadata configs
     * @return An array of metadata configs or return empty object if no data is found
     */
    const getMetaDataConfigs = () => {
        // Filter for meta properties and return the results
        return getProjectUIConfigs().filter((value) => {
            if (value.source && value.source.includes('meta')) {
                // Found some meta
                return true;
            }
            // This object is not meta
            return false;
        });
    };

    /**
     * Select a specific UI config for details by it's source name
     * @return a single metadata config
     */
    const getConfigBySourceName = (sourceName: string) => {
        return getProjectUIConfigs().find((value) => {
            if (value.source === sourceName) {
                // Found an exact match
                return true;
            }
            // Nothing found
            return false;
        });
    };

    /**
     * Add Active filter
     * @param source is the unique key these configs are known by
     * @param value the filter input for the source field
     */
    const addActiveFilter = (sourceName: string, value: string) => {
        // Try to get the new filter
        const newFilter = getConfigBySourceName(sourceName);

        // Did we find the config item?
        if (newFilter) {
            // Add the source name to the active filters
            setActiveFilters({ ...activeFilters, [newFilter.source]: value });
        }
    };

    /**
     * Remove a specific filter from the list
     * Updates the url query params to new values
     * @param source the unique key of the meta filter to remove
     */
    const removeFilter = (sourceName: string) => {
        // find out if the property exist on the object
        const exists = Object.keys(activeFilters).includes(sourceName);

        if (exists === false) {
            return; // does not exist, do_nothing()
        }

        // It exists, so lets delete it
        let newFilters = activeFilters;
        delete newFilters[sourceName];

        setActiveFilters(newFilters);
        replaceQueryParams(newFilters);
    };

    /**
     * Remove all active filters
     * This just wipes the array clean
     */
    const removeAllFilters = () => {
        setActiveFilters({});
        replaceQueryParams({});
    };

    /**
     * Set current page to the provided page
     * @param newPage to be set
     */
    const setCurrentPage = (newPage: number) => setPage(newPage);

    /**
     * Set page limit to the new limit
     * @param newLimit to be set
     */
    const setPageLimit = (newLimit: number) => setLimitPerPage(newLimit);

    /**
     * Refresh the process data and generate checkpoint data
     * Function is async for the nice await api.
     * This is not exposed through the container api and should be called internally.
     *
     * @param args Arguments for the fetch @see ProcessListQuery type for args
     */
    const refreshProcessData = async (args: ProcessListQuery) => {
        if (loadingData === true) {
            return;
        }

        setLoadingData(true);

        const { items: processes }: PaginatedProcessEntries = await apiList({
            ...args,
            include: ['checkpoints', 'history']
        });

        const checkpointGroups = {};
        processes.forEach((p) => {
            if (p.checkpoints && p.statusHistory) {
                checkpointGroups[p.instanceId] = generateCheckpointGroups(
                    p.checkpoints,
                    p.statusHistory
                );
            }
        });

        setCheckpointGroups(checkpointGroups);
        setProcesses(processes);
        setLoadingData(false);
    };

    /**
     * Get the total processes length of a process
     *
     * @return number of process length
     */
    const getProcessCount = (): number => (processes ? processes.length : 0);

    /**
     * Get a humanized display of what page they are on.
     *
     * @return humanized string if the data exists otherwise return an empty string
     */
    const getPaginationAsString = (): string => {
        if (processes) {
            const upperLimit = currentPage * limitPerPage;
            const lowerLimit = (currentPage - 1) * limitPerPage + 1;

            return `Showing ${lowerLimit} - ${upperLimit}`; // e.g. "Showing 1 - 10"
        } else {
            return '';
        }
    };

    /**
     * Reload function calls refreshProcessData function with parameters to refresh
     * the current page.
     *
     * @param filters - additional filter values, currently used to grab initial query params on page load
     *
     * @returns nothing
     */
    const reloadData = (filters?: { [source: string]: string }): void => {
        refreshProcessData({
            orgId,
            projectId,
            limit: limitPerPage,
            offset: (currentPage - 1) * limitPerPage,
            ...activeFilters,
            ...filters
        });
    };

    /**
     * Similar to reload data, calls refreshProcessData, but allows you to customize
     * the args passed to the reload function.
     *
     * TODO This function has the potential to throw warning if unmounted whilst fetch is in progress
     * ! Warning: Can't perform a React state update on an unmounted component.
     *
     * @param args Arguments for the fetch @see ProcessListQuery type for args
     */
    const loadData = (args: ProcessListQuery): void => {
        refreshProcessData({ ...args, ...activeFilters });
    };

    if (initial.refreshInterval !== undefined) {
        useEffect(
            () => {
                // Load initial dataset
                reloadData(getCurrentParams());

                // Continue to request data updates on
                const onPollInterval = setInterval(() => {
                    reloadData();
                }, initial.refreshInterval);

                return () => {
                    clearInterval(onPollInterval);
                };
            },
            [activeFilters, currentPage]
        );
    }

    // If activefilters change
    useEffect(
        () => {
            // Reset to page 1
            setPage(1);
        },
        [activeFilters]
    );

    /**
     * Update active filter when query params change
     */
    useEffect(
        () => {
            setActiveFilters({ ...queryParams });
        },
        [queryParams]
    );

    /**
     * Selector to see if we are currently on the first page.
     *
     * @return true if first page, otherwise false
     */
    const isFirstPage = (): boolean => currentPage === 1;

    /**
     * Selector to determine if filtering is possible based on current state
     * @return true if filtering is allowed, false if it is not
     */
    const canFilter = (): boolean => {
        if (getMetaDataConfigs().length > 0) {
            return true;
        }

        // Nothing true, so false
        return false;
    };

    return {
        checkpointGroups,
        currentPage,
        limitPerPage,
        setCurrentPage,
        getProcessCount,
        getPaginationAsString,
        isFirstPage,
        loadData,
        reloadData,
        loadingData,
        orgId,
        projectId,
        project,
        setPageLimit,
        processes,
        queryParams,
        replaceQueryParams,
        getProjectUIConfigs,
        getMetaDataConfigs,
        getConfigBySourceName,
        addActiveFilter,
        removeFilter,
        removeAllFilters,
        canFilter,
        activeFilters,
        setActiveFilters
    };
};
export const CheckpointContainer = createContainer(useCheckpoint);

export default CheckpointContainer;
