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
    isFinal,
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

export const getGroupsBetweenTime = (
    start: Date,
    end: Date,
    checkpoints: ProcessCheckpointEntry[],
    status: ProcessStatus
): CustomCheckpoint[] => {
    // * Custom checkpoints extend checkpoints and add start/end times and status
    const resultCheckpoints: CustomCheckpoint[] = [];

    checkpoints
        // * Sort Checkpoints in chronological order
        .sort(comparators.byProperty((i) => i.createdAt))
        // * Filter for checkpoints between a start and end date
        .filter((checkpoint) => {
            const checkTime = parseDate(checkpoint.createdAt);
            if (isAfter(checkTime, start) && isBefore(checkTime, end)) {
                return true;
            }
            return false;
        })
        // * Iterate over results, construct and push new customCheckpoints for return
        .forEach((checkpoint, index, array) => {
            if (index !== array.length - 1) {
                resultCheckpoints.push({
                    ...checkpoint,
                    status: ProcessStatus.FINISHED,
                    startTime: parseDate(checkpoint.createdAt),
                    endTime: parseDate(array[index + 1].createdAt)
                });
            } else {
                resultCheckpoints.push({
                    ...checkpoint,
                    status,
                    startTime: parseDate(checkpoint.createdAt),
                    endTime: parseDate(end)
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
    if (history.length == 0) {
        return [];
    }

    const groups: CheckpointGroup[] = [];
    let currentGroup: CheckpointGroup = {
        name: `#1`,
        start: parseDate(history[0].changeDate),
        checkpoints: []
    };

    history.forEach((event) => {
        // find the next group
        if (
            event.status === ProcessStatus.SUSPENDED &&
            event.payload &&
            event.payload.checkpointId
        ) {
            // close the previous group
            if (currentGroup) {
                groups.push({ ...currentGroup });
            }

            currentGroup = {
                name: `#${groups.length + 1}`,
                start: parseDate(event.changeDate),
                checkpoints: []
            };
        }

        // find an event that might look like an end of the group
        if (event.status === ProcessStatus.SUSPENDED || isFinal(event.status)) {
            if (!currentGroup) {
                return;
            }

            currentGroup.end = parseDate(event.changeDate);

            currentGroup.checkpoints = getGroupsBetweenTime(
                currentGroup.start!,
                currentGroup.end,
                checkpoints,
                event.status
            );
        }
    });

    // close the last group
    if (currentGroup) {
        groups.push({ ...currentGroup });
    }

    return groups;
};
