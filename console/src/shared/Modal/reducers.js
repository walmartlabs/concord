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
import types from './actions';

const INITIAL = { kind: null, opts: {} };

export default (state: any = INITIAL, { type, kind, opts }: any) => {
    switch (type) {
        case types.MODAL_OPEN:
            return { kind, opts };
        case types.MODAL_CLOSE:
            return INITIAL;
        default:
            return state;
    }
};
