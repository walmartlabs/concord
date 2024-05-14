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

import { default as AnsiUp } from 'ansi_up';
import { format as formatDate, parseISO as parseDate } from 'date-fns';

interface HasName {
    name: string;
}

/**
 * General rules to follow for comparator definitions
 *
 * used with Array.prototype.sort()
 *
 * If compareFunction(a, b) is less than 0, sort a to an index lower than b (i.e. a comes first).
 *
 * If compareFunction(a, b) returns 0, leave a and b unchanged with respect to each other,
 * but sorted with respect to all different elements.
 * Note: the ECMAscript standard does not guarantee this behaviour, and thus not all browsers
 * (e.g. Mozilla versions dating back to at least 2003) respect this.
 *
 * If compareFunction(a, b) is greater than 0, sort b to an index lower than a (i.e. b comes first).
 *
 * compareFunction(a, b) must always return the same value when given a
 * specific pair of elements a and b as its two arguments.
 * If inconsistent results are returned then the sort order is undefined.
 */
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

export const formatTimestamp = (t?: string): string | undefined => {
    if (!t) {
        return;
    }

    return formatDate(parseDate(t), 'yyyy-MM-dd HH:mm:ss');
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

const thresh = 1024;
const units = ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
export const formatFileSize = (bytes: number) => {
    if (Math.abs(bytes) < thresh) {
        return bytes + ' B';
    }

    let u = -1;
    do {
        bytes /= thresh;
        ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + ' ' + units[u];
};

export const escapeHtml = (s: string): string =>
    s
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');

export interface Config {
    string: string;
    style: string;
    divide?: boolean;
}

export interface HighlighterProps {
    config: Config[];
    caseInsensitive?: boolean;
    global?: boolean;
}

const ansiUp = new AnsiUp();
ansiUp.escape_for_html = false;

export const highlight = (value: string, props: HighlighterProps): string => {
    const { config, caseInsensitive = false, global = true } = props;
    const regExpCfg = `${caseInsensitive ? 'i' : ''}
            ${global ? 'g' : ''}`.trim();
    let txt = value;

    for (const cfg of config) {
        if (typeof txt === 'string') {
            txt = txt.replace(
                RegExp(cfg.string, regExpCfg),
                () =>
                    `<span style="${cfg.style}"><b>${cfg.string}</b></span>${
                        cfg.divide ? '<hr/>' : ''
                    }`
            );
        }
    }

    txt = ansiUp.ansi_to_html(txt);

    return txt;
};

export const setQueryParam = (url: string, key: string, value: string): string => {
    const isAbsoluteUrl = url.startsWith('http') || url.startsWith('https');
    const fakeBase = !isAbsoluteUrl ? 'http://fake-base.com' : undefined;
    var modifiedUrl = new URL(url, fakeBase);

    modifiedUrl.searchParams.set(key, value);

    if (isAbsoluteUrl) {
        return modifiedUrl.toString();
    } else {
        return modifiedUrl.toString().replace(fakeBase!, '');
    }
}
