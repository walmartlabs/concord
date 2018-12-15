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
import { default as AnsiUp } from 'ansi_up';

interface HighlighterProps {
    config: Config[];
    value: string;
    caseInsensitive?: boolean;
    global?: boolean;
}

interface Config {
    string: string;
    style: string;
}

const ansiUp = new AnsiUp();
ansiUp.escape_for_html = false;

class Highlighter extends React.PureComponent<HighlighterProps> {
    constructor(props: HighlighterProps) {
        super(props);
    }

    render() {
        const { config, value, caseInsensitive = false, global = true } = this.props;
        const regExpCfg = `${caseInsensitive ? 'i' : ''}
            ${global ? 'g' : ''}`.trim();
        let txt = value;

        for (const cfg of config) {
            txt = txt.replace(
                RegExp(cfg.string, regExpCfg),
                () => `<span style="${cfg.style}"><b>${cfg.string}</b></span>`
            );
        }

        txt = ansiUp.ansi_to_html(txt);

        return <div dangerouslySetInnerHTML={{ __html: txt }} />;
    }
}
export default Highlighter;
