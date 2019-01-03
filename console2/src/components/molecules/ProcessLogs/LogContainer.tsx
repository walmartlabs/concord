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
import { Container, ActionMap, SelectorMap, EffectMap, ComposableContainer } from 'constate';

import fetchLog from '../../../api/process/log/fetchLogAsBlobURL';

type logDetails = {
    // Blob URL containing log data
    url?: string;
    // Array of checkpoint ids
    anchors?: { [s: string]: string };
};

export interface State {
    // The process we load the log for
    activeProcess: string;
    // The Anchor tag we will try to find
    activeAnchor: string;
    // Object for storing multiple logs at one time
    logsByProcessId: { [s: string]: logDetails };
}

// No state variables have initial values
export const initialState: State = {
    activeProcess: '',
    activeAnchor: '',
    logsByProcessId: {}
};

export interface Actions {
    // To set which process log we should look at
    setActiveProcess: (processId: string) => void;
    // To set the anchor to scroll to
    setActiveAnchor: (Id: string) => void;
    // Add a checkpoint Id to a process's anchors
    addCheckpointId: (processId: string, checkpointId: string) => void;
}

export interface Selectors {
    // Find out what the current active process is
    getActiveProcess: () => string;
    // Find out what the current active anchor is
    getActiveAnchor: () => string;
}

export interface Effects {
    // Use HTML File API and fetch to pull log data into a blob URL
    fetchLog: (processId: string) => void;
    // Multiple try to find a DOM element and scroll to it
    queueScrollTo: (Id: string) => void;
}

// Actions are non-async changes to state
export const actions: ActionMap<State, Actions> = {
    setActiveProcess: (processId) => (state) => {
        return { ...state, activeProcess: processId };
    },
    setActiveAnchor: (Id) => (state) => {
        return { ...state, activeAnchor: Id };
    },
    addCheckpointId: (processId, checkpointId) => (state) => {
        return {
            ...state,
            logsByProcessId: { [processId]: { anchors: { [checkpointId]: checkpointId } } }
        };
    }
};

// Selectors derive values from values in state
export const selectors: SelectorMap<State, Selectors> = {
    getActiveProcess: () => (state) => state.activeProcess,
    getActiveAnchor: () => (state) => state.activeAnchor
};

// Effects are for async actions and side effects
export const effects: EffectMap<State, Effects> = {
    fetchLog: (processId) => async ({ setState }) => {
        const resp = await fetchLog(processId);

        setState((state) => ({
            ...state,
            logsByProcessId: {
                [processId]: {
                    url: resp
                }
            }
        }));
    },
    queueScrollTo: (Id) => async () => {
        const pauseTime = 500; // # of ms before trying again
        const tryLimit = 30; // # of retries

        // State Vars
        let tryCount = 0;
        let queuedToScroll = true;

        // Loop till we find the dom element
        while (queuedToScroll) {
            const element = document.getElementById(Id);

            // If element is found, scroll it into view
            if (element) {
                element.scrollIntoView({ behavior: 'auto', block: 'start', inline: 'nearest' });
                queuedToScroll = false;
            } else {
                // Await on an Async timeout to resolve before trying again
                await new Promise((resolve) =>
                    setTimeout(() => {
                        resolve();
                    }, pauseTime)
                );

                // Prevent infinite loops and such.
                tryCount++;
                if (tryCount >= tryLimit) {
                    queuedToScroll = false;
                    console.warn(
                        `Log either is taking a long time to load or the ID of ${Id} does not exist.`
                    );
                }
            }
        }
    }
};

// Exporting Composable container
// Provides function as children render props as a means to interact with the container
export const LogContainer: ComposableContainer<State, Actions, Selectors, Effects> = (
    props: any
) => {
    return (
        <Container
            initialState={initialState}
            actions={actions}
            selectors={selectors}
            effects={effects}
            context="logs"
            {...props}
        />
    );
};

export default LogContainer;
