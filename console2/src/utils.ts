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

import * as moment from 'moment';

interface HasName {
    name: string;
}

export const comparators = {
    byProperty: <T, P>(getter: (i: T) => P) => (a: T, b: T): number => {
        const x = getter(a);
        const y = getter(b);
        return x > y ? 1 : x < y ? -1 : 0;
    },
    byName: (a: HasName, b: HasName) => (a.name > b.name ? 1 : a.name < b.name ? -1 : 0)
};

export const notEmpty = (x: {}) => {
    if (!x) {
        return false;
    }

    const ks = Object.keys(x);
    if (ks.length === 0) {
        return false;
    }

    for (const k of ks) {
        const v = x[k];
        if (v !== undefined) {
            return true;
        }
    }

    return false;
};

export const formatTimestamp = (t?: moment.MomentInput): string | undefined => {
    if (!t) {
        return;
    }

    return moment(t).format('YYYY-MM-DD HH:mm:ss');
};
