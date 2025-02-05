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
import { REPOSITORY_SSH_URL_PATTERN } from '../../../validation';

interface Props {
    url: string;
    link?: string;
    path?: string;
    commitId?: string;
    text?: string;
    branch?: string;
}

export const gitUrlParse = (s: string): string | undefined => {
    const url = s.endsWith('.git') ? s : s + '.git';

    const match = REPOSITORY_SSH_URL_PATTERN.exec(url);

    if (match && match.length === 6) {
        const path = match[4] !== undefined ? `/${match[4]}` : '';
        return `https://${match[3]}${path}`;
    } else if (url.startsWith('http')) {
        // https://github.example.com/devtools/concord.git
        const regex = /http[s]?:\/\/(.*)/;
        const match = regex.exec(url);
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
        const { url, link, commitId, path, text, branch } = this.props;

        let s = !link ? gitUrlParse(url) : link;
        if (!s) {
            return url;
        }

        if (s.endsWith('.git')) {
            s = s.substr(0, s.length - 4);
        }

        if (commitId && !path) {
            s += `/commit/${commitId}`;
        } else if (commitId && path) {
            s += `/tree/${commitId}/${normalizePath(path)}`;
        } else if (!commitId && branch && !path) {
            s += `/tree/${branch}`;
        } else if (!commitId && path && branch) {
            s += `/tree/${branch}/${normalizePath(path)}`;
        }

        return (
            <a href={s} target="_blank" rel="noopener noreferrer">
                {text ? text : s} <Icon name="external" />
            </a>
        );
    }
}

export default GitHubLink;
