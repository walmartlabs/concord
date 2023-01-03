/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import { ConcordId } from '../../../../api/common';

export interface PlayInfoEntry {
    id: ConcordId;
    play: string;
    hostsCount: number;
    tasksCount: number;
    taskStats: TaskStats;
    progress: number;
}

export interface TaskInfoEntry {
    name: string;
    type: string;
    stats: TaskStats;
}

export interface TaskStats {
    failed: number;
    ok: number;
    unreachable: number;
    skipped: number;
    running: number;
}

export const OK_COLOR = 'green';
export const FAILED_COLOR = 'red';
export const UNREACHABLE_COLOR = 'orange';
export const SKIPPED_COLOR = 'grey';
export const RUNNING_COLOR = undefined;

export const OK_COLOR_HEX = '#21ba45';
export const FAILED_COLOR_HEX = '#db2828';
export const UNREACHABLE_COLOR_HEX = '#f2711c';
export const SKIPPED_COLOR_HEX = '#767676';
export const RUNNING_COLOR_HEX = '#e8e8e8';
