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

import { isAfter, isBefore, parse as parseDate } from 'date-fns';

import {
    ProcessCheckpointEntry,
    ProcessHistoryEntry,
    ProcessStatus
} from '../../../../api/process';
import { comparators } from '../../../../utils';
import { CheckpointGroup, CustomCheckpoint } from '../shared/types';

/**
 * Generate CustomCheckpoint array between time
 *
 * @param start Start Date Object
 * @param end End Date Object
 * @param checkpoints Concord Process Checkpoint array
 * @param status Process Status type for this run
 * */

export const geCheckpointsBetweenTime = (
    checkpoints: ProcessCheckpointEntry[],
    status: ProcessStatus,
    start: Date,
    end: Date
): CustomCheckpoint[] => {
    // * Custom checkpoints extend checkpoints and add start/end times and status
    const resultCheckpoints: CustomCheckpoint[] = [];

    checkpoints
        .sort(comparators.byProperty((i) => i.createdAt))
        // * Filter for checkpoints between a start and end date
        .filter((checkpoint) => {
            const checkTime = parseDate(checkpoint.createdAt);
            return isAfter(checkTime, start) && isBefore(checkTime, end);
        })
        .forEach((checkpoint, index, array) => {
            if (index !== array.length - 1) {
                resultCheckpoints.push({
                    ...checkpoint,
                    status: ProcessStatus.FINISHED,
                    startTime: parseDate(checkpoint.createdAt)
                });
            } else {
                resultCheckpoints.push({
                    ...checkpoint,
                    status,
                    startTime: parseDate(checkpoint.createdAt)
                });
            }
        });

    return resultCheckpoints;
};

/**
 * Generates a custom object array of type CheckpointGroup
 * Correlates checkpoint data with history data to generate said object.
 *
 * @param checkpoints Original Process Checkpoint Array
 * @param historyEntries Original Process History Array
 */
export const generateCheckpointGroups = (
    checkpoints: ProcessCheckpointEntry[],
    historyEntries: ProcessHistoryEntry[]
): CheckpointGroup[] => {
    // make sure that the history is sorted
    const history = historyEntries.sort(comparators.byProperty((i) => i.changeDate));
    if (history.length === 0) {
        return [];
    }

    const groups: CheckpointGroup[] = [];
    let currentGroup: CheckpointGroup = {
        name: `#1`,
        checkpoints: []
    };

    history.forEach((event) => {
        if (event.status === ProcessStatus.RUNNING) {
            const eventDate = parseDate(event.changeDate);
            currentGroup.status = ProcessStatus.RUNNING;
            if (currentGroup.start === undefined) {
                currentGroup.start = eventDate;
            }
            currentGroup.end = new Date();
        } else if (
            event.status === ProcessStatus.SUSPENDED &&
            event.payload &&
            event.payload.checkpointId
        ) {
            // close the previous group
            groups.push({ ...currentGroup });

            currentGroup = {
                name: `#${groups.length + 1}`,
                checkpoints: []
            };
        } else {
            currentGroup.status = event.status;
            currentGroup.end = parseDate(event.changeDate);
        }
    });

    // close the last group
    groups.push({ ...currentGroup });

    // add checkpoints into groups
    groups
        .filter((g) => g.status !== undefined)
        .filter((g) => g.start !== undefined)
        .filter((g) => g.end !== undefined)
        .forEach((g) => {
            g.checkpoints = geCheckpointsBetweenTime(checkpoints, g.status!, g.start!, g.end!);
        });

    return groups;
};
