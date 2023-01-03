/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { kill as apiKill } from '../../../api/process';
import { useState } from 'react';

interface ExternalProps {
    instanceId: ConcordId;
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

const CancelProcessPopup = memo((props: ExternalProps) => {
    const [cancelling, setCancelling] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [success, setSuccess] = useState(false);

    const instanceId = props.instanceId;

    const cancelProcess = useCallback(async () => {
        setCancelling(true);

        try {
            await apiKill(instanceId);
            setSuccess(true);
        } catch (e) {
            setError(e);
        } finally {
            setCancelling(false);
        }
    }, [instanceId]);

    const reset = useCallback(() => {
        setCancelling(false);
        setSuccess(false);
        setError(undefined);
    }, []);

    const { trigger, refresh } = props;

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Cancel the process?"
            introMsg={<p>Are you sure you want to cancel the selected process?</p>}
            running={cancelling}
            runningMsg={<p>Cancelling...</p>}
            success={success}
            successMsg={<p>The cancel command was sent successfully.</p>}
            error={error}
            reset={reset}
            onDone={refresh}
            onConfirm={cancelProcess}
        />
    );
});

export default CancelProcessPopup;
