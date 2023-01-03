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
import { format as formatDate, parseISO as parseDate } from 'date-fns';
import { escapeHtml, highlight } from '../../../../utils';
import { LogSegment, LogSegmentType, TagData } from './types';

const TAG = '__logTag:';

export interface LogProcessorOptions {
    separateTasks?: boolean;
    useLocalTime?: boolean;
    showDate?: boolean;
}

export const process = (s: LogSegment, opts: LogProcessorOptions): LogSegment[] => {
    return split(s, opts).map((s) => {
        if (s.type === LogSegmentType.DATA) {
            return { ...s, data: processText(s.data as string, opts) };
        }
        return s;
    });
};

const split = (s: LogSegment, opts: LogProcessorOptions): LogSegment[] => {
    if (s.type !== LogSegmentType.DATA) {
        return [s];
    }

    const result: LogSegment[] = [];

    let data = s.data as string;
    while (true) {
        const tagStart = data.indexOf(TAG);
        if (tagStart < 0) {
            result.push({ type: LogSegmentType.DATA, data });
            break;
        }

        const tagEnd = data.indexOf('\n', tagStart);
        if (tagEnd < 0) {
            break;
        }

        // grab the log segment before the tag
        const prev = data.substring(0, tagStart);
        result.push({ type: LogSegmentType.DATA, data: prev });

        if (opts.separateTasks) {
            // grab the tag's data
            const tag = data.substring(tagStart + TAG.length, tagEnd);
            try {
                result.push({ type: LogSegmentType.TAG, data: JSON.parse(tag) as TagData });
            } catch (e) {
                console.warn('Error while parsing a log tag: ', tag, e);
            }
        }

        // next segment
        data = data.substring(tagEnd + 1);
    }

    return result;
};

export const processText = (s: string, { useLocalTime, showDate }: LogProcessorOptions): string => {
    s = processDate(s, useLocalTime, showDate);
    s = escapeHtml(s);
    s = processLinks(s);
    s = processTags(s);
    s = colorize(s);

    return s;
};

const URL_PATTERN = /(\b(https?):\/\/([-A-Z0-9+@#/%?=~_|!:,.;]|&amp;)*)/;

const processLinks = (value: string): string => {
    return value.replace(
        RegExp(URL_PATTERN, 'ig'),
        (url) => `<a href="${url}" target="_blank">${url}</a>`
    );
};

// html tags (<, >, ..) already escaped
const INSTANCE_ID_TAG = /&lt;concord:instanceId&gt;([^&]*)&lt;\/concord:instanceId&gt;/;

const processTags = (value: string): string => {
    return value.replace(
        RegExp(INSTANCE_ID_TAG, 'ig'),
        (s, instanceId) => `<a href="#/process/${instanceId}/log" target="_blank">${instanceId}</a>`
    );
};

const colorizeProps = {
    config: [
        { string: 'INFO ', style: 'color: #00B5F0' },
        { string: 'WARN ', style: 'color: #ffae42' },
        { string: 'ERROR', style: 'color: #ff0000' },
        { string: 'ANSIBLE:', style: 'color: #808080' },
        { string: 'DOCKER:', style: 'color: #808080' }
    ]
};

const colorize = (value: string): string => {
    return highlight(value, colorizeProps);
};

// we expect the runtime to use "yyyy-MM-dd'T'HH:mm:ss.SSSZ" format for timestamps
// see also /common/src/main/java/com/walmartlabs/concord/common/LogUtils.java
const DATE_PATTERN = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}.\d{4})/;
const DATE_LENGTH = '2019-04-11T19:41:24.839+0000'.length;

const processDate = (value: string, useLocalTime?: boolean, showDate?: boolean): string => {
    if (!useLocalTime) {
        return value;
    }

    const lines = value.split('\n');
    for (let i = 0; i < lines.length; i++) {
        const l = lines[i];
        const dt = l.substring(0, DATE_LENGTH);
        if (DATE_PATTERN.test(dt)) {
            const d = parseDate(dt);
            const dst = formatDate(d, `${showDate ? 'yyyy-MM-dd ' : ''}HH:mm:ss`);
            lines[i] = dst + l.substring(DATE_LENGTH);
        }
    }
    return lines.join('\n');
};
