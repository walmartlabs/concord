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
import { Container, ComposableContainer, EffectMap, ActionMap, SelectorMap } from 'constate';
import { ProcessEntryEx } from '../../../../api/service/console';

import { FetchProcessArgs, loadData } from './loadData';
import { CheckpointGroup, ConcordId } from '../shared/types';

export interface State {
    orgId: ConcordId; // Concord org UUID
    projectId: ConcordId; // Concord project UUID
    processes: ProcessEntryEx[]; // Array of processes
    checkpointGroups: { [id: string]: CheckpointGroup[] };
    currentPage: number; // Current data page
    limitPerPage: number; // Processes per page
    loadingData: boolean; // Are we loading process data?
}

// No state variables have initial values
export const initialState: State = {
    orgId: '',
    projectId: '',
    processes: [],
    checkpointGroups: {},
    currentPage: 1,
    limitPerPage: 10,
    loadingData: false
};

export interface Actions {
    setPageLimit: (newLimit: number) => void;
    setCurrentPage: (newPage: number) => void;
}

// Actions are non-async changes to state
export const actions: ActionMap<State, Actions> = {
    setPageLimit: (newLimit: number) => () => {
        return { limitPerPage: newLimit };
    },
    setCurrentPage: (newPage: number) => (state) => ({ currentPage: newPage })
};
export interface Selectors {
    getCurrentPageProcesses: () => any[]; // TODO: Process Types
    getProcessCount: () => number;
    getPaginationAsString: () => string;
    isFirstPage: () => boolean;
}

// Selectors derive values from values in state
export const selectors: SelectorMap<State, Selectors> = {
    getCurrentPageProcesses: () => ({ currentPage, limitPerPage, processes }) =>
        processes
            ? processes.slice((currentPage - 1) * limitPerPage, currentPage * limitPerPage)
            : [],
    getProcessCount: () => ({ processes }) => (processes ? processes.length : 0),
    getPaginationAsString: () => ({ currentPage, limitPerPage, processes }) => {
        // TODO: Find way to get total number of processes
        // * With Offset Pagination we have the issue of not knowing the total number of processes there are
        // const totalItems = processes ? (processes.length > 0 ? `of ${processes.length}` : '') : '';

        if (processes) {
            const upperLimit = currentPage * limitPerPage;
            const lowerLimit = (currentPage - 1) * limitPerPage + 1;

            return `Showing ${lowerLimit} - ${upperLimit}`; // e.g. "Showing 1 - 10"
        } else {
            return '';
        }
    },
    isFirstPage: () => (state) => state.currentPage === 1
};

export interface Effects {
    refreshProcessData: (args: FetchProcessArgs) => void;
}

// Effects are for async actions and side effects
export const effects: EffectMap<State, Effects> = {
    refreshProcessData: loadData
};

// Exporting Composable container
// Provides function as children render props as a means to interact with the container
export const CheckpointViewContainer: ComposableContainer<State, Actions, Selectors, Effects> = (
    props
) => {
    return (
        <Container
            actions={actions}
            selectors={selectors}
            effects={effects}
            pure={true}
            context="CheckpointView"
            {...props}
        />
    );
};

export default CheckpointViewContainer;
