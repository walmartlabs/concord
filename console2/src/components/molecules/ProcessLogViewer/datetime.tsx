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

export const formatDateTime = (useLocalTime: boolean, showDate: boolean, s: string): string => {
    if (!useLocalTime) {
        return s;
    }

    // we expect the runtime to use "YYYY-MM-dd'T'HH:mm:ss.SSSZ" format for timestamps
    // see also /common/src/main/java/com/walmartlabs/concord/common/LogUtils.java
    const re = /.*(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}.\d{4}).*/gm;

    const timestamps = [];
    while (true) {
        const m = re.exec(s);
        if (!m) {
            break;
        }
        timestamps.push(m[1]);
    }

    if (timestamps.length === 0) {
        return s;
    }

    timestamps.forEach((src) => {
        const d = parseDate(src);
        const dst = formatDate(src, `${showDate ? 'YYYY-MM-DD ' : ''}HH:mm:ss`);
        s = s.replace(src, dst);
    });

    return s;
};
