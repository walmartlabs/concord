/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { format as formatDate, parse as parseDate } from 'date-fns';
import { escapeHtml } from '../../../../utils';
import { highlight } from '../../../../utils';

export const process = (value: string, useLocalTime: boolean, showDate: boolean): string => {
    console.log('process: ' + useLocalTime + ', ' + showDate);

    return processLinks(colorize(escapeHtml(processDate(value, useLocalTime, showDate))));
};

const URL_PATTERN = /(\b(https?|http):\/\/\S+)/;

const processLinks = (value: string): string => {
    return value.replace(
        RegExp(URL_PATTERN, 'ig'),
        (url) => `<a href="${url}" target="_blank">${url}</a>`
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

// we expect the runtime to use "YYYY-MM-dd'T'HH:mm:ss.SSSZ" format for timestamps
// see also /common/src/main/java/com/walmartlabs/concord/common/LogUtils.java
const DATE_PATTERN = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}.\d{4})/;
const DATE_LENGTH = '2019-04-11T19:41:24.839+0000'.length;

const processDate = (value: string, useLocalTime: boolean, showDate: boolean): string => {
    if (!useLocalTime) {
        return value;
    }

    const lines = value.split('\n');
    for (let i = 0; i < lines.length; i++) {
        const l = lines[i];
        const dt = l.substring(0, DATE_LENGTH);
        if (DATE_PATTERN.test(dt)) {
            const d = parseDate(dt);
            const dst = formatDate(d, `${showDate ? 'YYYY-MM-DD ' : ''}HH:mm:ss`);
            lines[i] = dst + l.substring(DATE_LENGTH);
        }
    }
    return lines.join('\n');
};
