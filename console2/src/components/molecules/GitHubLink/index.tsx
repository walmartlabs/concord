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
