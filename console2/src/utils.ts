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

const second2Ms = 1000;
const minute2Ms = 60 * second2Ms;
const hour2Ms = 60 * minute2Ms;
const day2Ms = 24 * hour2Ms;

export const formatDuration = (ms?: number): string | undefined => {
    if (ms === 0) {
        return '0ms';
    }

    if (!ms) {
        return;
    }

    let t = ms;

    if (t < second2Ms) {
        return `${t}ms`;
    }

    if (t < minute2Ms) {
        return `~ ${Math.ceil(t / second2Ms)}s`;
    }

    if (t < hour2Ms) {
        return `~ ${Math.ceil(t / minute2Ms)}m`;
    }

    let s = '~';

    const days = Math.floor(t / day2Ms);
    if (days > 0) {
        s += ` ${days}d`;
        t -= days * day2Ms;
    }

    const hours = Math.floor(t / hour2Ms);
    if (hours > 0) {
        s += ` ${hours}h`;
        t -= hours * hour2Ms;
    }

    const mins = Math.floor(t / minute2Ms);
    if (mins > 0) {
        s += ` ${mins}m`;
    }

    return s;
};

export const timestampDiffMs = (
    t1?: moment.MomentInput,
    t2?: moment.MomentInput
): number | undefined => {
    if (!t1 || !t2) {
        return;
    }

    return moment(t1).diff(moment(t2));
};

export const escapeHtml = (s: string): string =>
    s
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
