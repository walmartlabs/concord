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

import { ConcordId, RequestError } from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import { memo, useCallback } from 'react';
import { restart as apiRestart } from '../../../api/process';
import { useState } from 'react';

interface ExternalProps {
    instanceId: ConcordId;
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

const RestartProcessPopup = memo((props: ExternalProps) => {
    const [restarting, setRestarting] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [success, setSuccess] = useState(false);

    const instanceId = props.instanceId;

    const cancelProcess = useCallback(async () => {
        setRestarting(true);

        try {
            await apiRestart(instanceId);
            setSuccess(true);
        } catch (e) {
            setError(e);
        } finally {
            setRestarting(false);
        }
    }, [instanceId]);

    const reset = useCallback(() => {
        setRestarting(false);
        setSuccess(false);
        setError(undefined);
    }, []);

    const { trigger, refresh } = props;

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Restart the process?"
            introMsg={<p>Are you sure you want to restart the selected process?</p>}
            running={restarting}
            runningMsg={<p>Restarting...</p>}
            success={success}
            successMsg={<p>The restart command was sent successfully.</p>}
            error={error}
            reset={reset}
            onDone={refresh}
            onConfirm={cancelProcess}
        />
    );
});

export default RestartProcessPopup;
