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

import { isAfter, isBefore, isEqual, parseISO as parseDate } from 'date-fns';

import {
    CheckpointRestoreHistoryEntry,
    ProcessCheckpointEntry,
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
    end?: Date
): CustomCheckpoint[] => {
    // * Custom checkpoints extend checkpoints and add start/end times and status
    const resultCheckpoints: CustomCheckpoint[] = [];

    checkpoints
        // * Filter for checkpoints between a start and end date
        .filter((checkpoint) => {
            const checkTime = parseDate(checkpoint.createdAt);
            return (
                (isEqual(checkTime, start) || isAfter(checkTime, start)) &&
                (end === undefined || isBefore(checkTime, end))
            );
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
 * @param processStatus status of the process
 * @param checkpoints Original Process Checkpoint Array
 * @param checkpointRestoreHistory
 */
export const generateCheckpointGroups = (
    processStatus: ProcessStatus,
    checkpoints: ProcessCheckpointEntry[],
    checkpointRestoreHistory?: CheckpointRestoreHistoryEntry[]
): CheckpointGroup[] => {
    const points = checkpoints.sort(comparators.byProperty((i) => parseDate(i.createdAt)));
    if (points.length === 0) {
        return [];
    }

    const history = (checkpointRestoreHistory || []).sort(
        comparators.byProperty((i) => parseDate(i.changeDate))
    );

    const groups: CheckpointGroup[] = [];
    let currentGroup: CheckpointGroup = {
        name: `#1`,
        status: ProcessStatus.FINISHED,
        checkpoints: []
    };
    let currentGroupStart = parseDate(points[0].createdAt);
    history.forEach((h) => {
        let currentGroupEnd = parseDate(h.changeDate);

        currentGroup.status = h.processStatus;
        currentGroup.checkpoints = geCheckpointsBetweenTime(
            points,
            h.processStatus,
            currentGroupStart,
            currentGroupEnd
        );
        groups.push(currentGroup);

        currentGroup = {
            name: `#${groups.length + 1}`,
            status: processStatus,
            checkpoints: []
        };
        currentGroupStart = currentGroupEnd;
    });

    currentGroup.checkpoints = geCheckpointsBetweenTime(
        checkpoints,
        processStatus,
        currentGroupStart,
        undefined
    );
    groups.push(currentGroup);

    return groups;
};
