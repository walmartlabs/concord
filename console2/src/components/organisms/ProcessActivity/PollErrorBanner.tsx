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
import { Message } from 'semantic-ui-react';

import { RequestErrorData } from '../../../api/common';

interface Props {
    processError?: RequestErrorData;
    tabError?: RequestErrorData;
    /** Label of the tab that reported `tabError`, e.g. "Events". */
    tabLabel: string;
    retryInterval: number;
    onRetry: () => void;
}

const formatInterval = (ms: number): string => {
    const seconds = Math.round(ms / 1000);
    if (seconds < 60) {
        return seconds === 1 ? 'second' : `${seconds} seconds`;
    }
    const minutes = Math.round(seconds / 60);
    return minutes === 1 ? 'minute' : `${minutes} minutes`;
};

const describe = (processError: boolean, tabError: boolean, tabLabel: string) => {
    if (processError && tabError) {
        return ['Unable to refresh the process', 'The data on this page may be out of date.'];
    }
    if (processError) {
        return [
            'Unable to refresh the process status',
            'The status shown above may be out of date.',
        ];
    }
    return [`Unable to refresh the ${tabLabel} tab`, 'The data below may be out of date.'];
};

/**
 * Shown only while polling retries on its own, so the text says which data is stale and that no
 * action is required. Error details are omitted: a proxy body can be arbitrarily long.
 */
const PollErrorBanner = ({ processError, tabError, tabLabel, retryInterval, onRetry }: Props) => {
    const error = processError ?? tabError;
    if (!error) {
        return null;
    }

    const [header, stale] = describe(!!processError, !!tabError, tabLabel);
    return (
        <Message warning={true} className="ProcessPollError">
            <Message.Header>{header}</Message.Header>
            <p>
                {stale} Retrying automatically every {formatInterval(retryInterval)}, or{' '}
                {/* A button styled as a link: it triggers an action rather than navigating. */}
                <button type="button" className="ProcessPollErrorRetry" onClick={onRetry}>
                    retry now
                </button>
                .
            </p>
            {error.message && <p className="ProcessPollErrorCause">Last error: {error.message}</p>}
        </Message>
    );
};

export default PollErrorBanner;
