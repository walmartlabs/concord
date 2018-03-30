/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
export const statusFilterKeys = ['ALL', 'FAILURES', 'OK', 'CHANGED', 'SKIPPED', 'UNREACHABLE'];

export const statusColors = {
    OK: '#5DB571', // green
    CHANGED: '#00A4D3', // blue
    FAILURES: '#EC6357', // red
    UNREACHABLE: '#BDB9B9', // gray
    SKIPPED: '#F6BC32', // yellow
    DEFAULT: '#3F3F3D', // black
    ALL: '#3F3F3D'
};

export const semanticStatusColors = {
    OK: 'green',
    CHANGED: 'blue',
    FAILURES: 'red',
    UNREACHABLE: 'gray',
    SKIPPED: 'orange',
    DEFAULT: 'black'
};
