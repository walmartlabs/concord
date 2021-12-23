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
import { useState } from 'react';
import fetchLogData from '../../../api/process/log/fetchLogAsBlobURL';
import constate from "constate";

type logDetails = {
    // Blob URL containing log data
    url?: string;
    // Array of checkpoint ids
    anchors?: { [s: string]: string };
};

// Custom hook to provide log functionalities
export const useLog = () => {
    // The process we load the log for
    const [activeProcess, setActiveProcess] = useState('');
    // The Anchor tag we will try to find
    const [activeAnchor, setActiveAnchor] = useState('');
    // Object for storing multiple logs at one time
    const [logsById, setLogsById] = useState<{ [s: string]: logDetails }>({});

    // Add a checkpoint Id to a process's anchors
    const addCheckpointId = (processId: string, checkpointId: string) => {
        // ? Does this keep old state?
        setLogsById({ [processId]: { anchors: { [checkpointId]: checkpointId } } });
    };

    const queueScrollTo = (Id: string) => async () => {
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
                await new Promise((resolve) => setTimeout(resolve, pauseTime));

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
    };
    const fetchLog = (processId: string) => async () => {
        const resp = await fetchLogData(processId);

        setLogsById({
            [processId]: {
                url: resp
            }
        });
    };

    return {
        activeProcess,
        setActiveProcess,
        activeAnchor,
        setActiveAnchor,
        logsById,
        setLogsById,
        addCheckpointId,
        queueScrollTo,
        fetchLog
    };
};

export const [LogProvider, useLogContext] = constate(useLog);

export default LogProvider;