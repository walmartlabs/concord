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
import { ProcessHistoryEntry } from '../../../../api/process/history';
import { ProcessCheckpointEntry, CheckpointGroup, Status, CustomCheckpoint } from '../shared/types';
import { isBefore, isAfter } from 'date-fns';

// * Sort callback to order objects by a specific date property
const sortChrono = (sortByKey: string) => (a: any, b: any) =>
    new Date(a[sortByKey]).getTime() - new Date(b[sortByKey]).getTime();

// * Generate CustomCheckpoint array between time
export const getGroupsBetweenTime = (
    start: Date,
    end: Date,
    checkpoints: ProcessCheckpointEntry[],
    status: Status
): CustomCheckpoint[] => {
    // * Custom checkpoints extend checkpoints and add start/end times and status
    const resultCheckpoints: CustomCheckpoint[] = [];

    checkpoints
        // * Sort Checkpoints in chronological order
        .sort(sortChrono('createdAt'))
        // * Filter for checkpoints between a start and end date
        .filter((checkpoint) => {
            const checkTime = new Date(checkpoint.createdAt);
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
                    status: 'FINISHED',
                    startTime: new Date(checkpoint.createdAt),
                    endTime: new Date(array[index + 1].createdAt)
                });
            } else {
                resultCheckpoints.push({
                    ...checkpoint,
                    status,
                    startTime: new Date(checkpoint.createdAt),
                    endTime: new Date(end)
                });
            }
        });

    return resultCheckpoints;
};

export const generateCheckpointGroups = (
    checkpoints: ProcessCheckpointEntry[],
    historyEntries: ProcessHistoryEntry[]
): CheckpointGroup[] => {
    // * Sort History in chronological order
    const cronoHist = historyEntries.sort(sortChrono('changeDate'));

    const checkpointGroups: CheckpointGroup[] = [];
    let groupCount = 1; //
    const initialWipGroup: CheckpointGroup = { name: '', checkpoints: [] };
    let wipGroup: CheckpointGroup = initialWipGroup;

    cronoHist.forEach((event) => {
        // * Create Group Starting point of Process meets this criteria
        if (
            (event.checkpointId !== undefined && event.status === 'SUSPENDED') ||
            event.status === 'STARTING'
        ) {
            wipGroup.name = `#${groupCount}`;
            wipGroup.Start = new Date(event.changeDate);
        }

        // * If we come across a Finalized state gather the events inbetween start and finish
        if (
            event.status === 'FINISHED' ||
            event.status === 'FAILED' ||
            event.status === 'CANCELLED'
        ) {
            wipGroup.End = new Date(event.changeDate);

            wipGroup.checkpoints = getGroupsBetweenTime(
                wipGroup.Start!,
                wipGroup.End,
                checkpoints,
                event.status
            );

            // * Add group to collection
            checkpointGroups.push({ ...wipGroup });

            // * Reset
            groupCount++;
            wipGroup = initialWipGroup;
        }
    });

    return [...checkpointGroups];
};
