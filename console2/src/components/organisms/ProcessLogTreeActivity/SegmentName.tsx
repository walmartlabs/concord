/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import * as React from 'react';
import './SegmentName.css';

// Match balanced expressions, including collection literals and braces inside strings.
// This only marks text; it never evaluates the expression.
export const nameParts = (name: string): { text: string; expression: boolean }[] => {
    const parts: { text: string; expression: boolean }[] = [];
    let offset = 0;
    let search = 0;
    while (search < name.length) {
        const start = name.indexOf('${', search);
        if (start < 0) break;
        let depth = 1;
        let quote = '';
        let end = start + 2;
        for (; end < name.length && depth > 0; end++) {
            const ch = name[end];
            if (quote) {
                if (ch === '\\') end++;
                else if (ch === quote) quote = '';
            } else if (ch === "'" || ch === '"') quote = ch;
            else if (ch === '{') depth++;
            else if (ch === '}') depth--;
        }
        if (depth !== 0) break;
        if (start > offset) parts.push({ text: name.slice(offset, start), expression: false });
        parts.push({ text: name.slice(start, end), expression: true });
        offset = end;
        search = end;
    }
    if (offset < name.length) parts.push({ text: name.slice(offset), expression: false });
    return parts;
};

const SegmentName = ({ name }: { name: string }) => {
    return (
        <>
            {nameParts(name).map((part, i) =>
                part.expression ? (
                    <span key={i} className="NameExpression">
                        {part.text}
                    </span>
                ) : (
                    <React.Fragment key={i}>{part.text}</React.Fragment>
                )
            )}
        </>
    );
};

export default SegmentName;
