/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
// @flow

export const booleanTrigger = (requestActionType: string, resultActionType: string) => (
    state: boolean = false,
    action: any
) => {
    switch (action.type) {
        case requestActionType:
            return true;
        case resultActionType:
            return false;
        default:
            return state;
    }
};

export const error = (type: string) => (state: ?string = null, action: any) => {
    switch (action.type) {
        case type:
            if (action.error) {
                return action.message;
            }
            return null;
        default:
            return state;
    }
};
