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
import { Icon } from 'semantic-ui-react';

interface Props {
    url: string;
    path?: string;
    commitId?: string;
    text?: string;
}

const gitUrlParse = (s: string): string | undefined => {
    if (s.startsWith('git')) {
        // git@gecgithub01.walmart.com:devtools/concord.git
        const regex = /git@(.*):(.*)\.git/;
        const match = regex.exec(s);
        if (!match || match.length !== 3) {
            return;
        }
        return `https://${match[1]}/${match[2]}`;
    } else if (s.startsWith('http')) {
        // https://gecgithub01.walmart.com/devtools/concord.git
        const regex = /http[s]?:\/\/(.*).git/;
        const match = regex.exec(s);
        if (!match || match.length !== 2) {
            return;
        }
        return `https://${match[1]}`;
    }

    return;
};

const normalizePath = (s: string): string => {
    if (s.startsWith('/')) {
        return s.substring(1);
    }
    return s;
};

class GitHubLink extends React.PureComponent<Props> {
    render() {
        const { url, commitId, path, text } = this.props;

        let s = gitUrlParse(url);
        if (!s) {
            return url;
        }

        if (commitId && !path) {
            s += `/commit/${commitId}`;
        } else if (commitId && path) {
            s += `/tree/${commitId}/${normalizePath(path)}`;
        } else if (!commitId && path) {
            s += `/tree/master/${normalizePath(path)}`;
        }

        return (
            <a href={s} target="_blank">
                {text ? text : s} <Icon name="external" />
            </a>
        );
    }
}

export default GitHubLink;
