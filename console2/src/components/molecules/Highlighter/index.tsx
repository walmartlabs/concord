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
import { highlight } from '../../../utils';

interface HighlighterProps {
    config: Config[];
    value: string;
    caseInsensitive?: boolean;
    global?: boolean;
}

interface Config {
    string: string;
    style: string;
    divide?: boolean;
}

class Highlighter extends React.PureComponent<HighlighterProps> {
    render() {
        const { value } = this.props;

        const txt = highlight(value, this.props);

        return <div dangerouslySetInnerHTML={{ __html: txt }} />;
    }
}
export default Highlighter;
