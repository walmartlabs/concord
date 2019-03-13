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
import * as React from 'react';

interface Props {
    text: string;
    allowedCharCount?: number;
    startSubstringCount?: number;
    endSubstringCount?: number;
}

// TODO: Handle non existant values
// * Returns a smaller string than what was originally supplied
export const Truncate: React.SFC<Props> = ({
    text,
    allowedCharCount = 15,
    startSubstringCount = 6,
    endSubstringCount = 6
}) => {
    const separator: string = '...';
    const start = text.substring(0, startSubstringCount);
    const end = text.substring(text.length - endSubstringCount, text.length);

    if (text.length > allowedCharCount) {
        return <span>{`${start + separator + end}`}</span>;
    } else {
        return <span>{text}</span>;
    }
};

export default Truncate;
