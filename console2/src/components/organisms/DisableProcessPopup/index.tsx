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
import { memo } from 'react';
import { useState } from 'react';
import { useCallback } from 'react';
import { disable as apiDisable } from '../../../api/process';

interface ExternalProps {
    instanceId: ConcordId;
    disabled: boolean;
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

const DisableProcessPopup = memo((props: ExternalProps) => {
    const [disabling, setDisabling] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [success, setSuccess] = useState(false);

    const { instanceId, disabled } = props;

    const disableProcess = useCallback(async () => {
        setDisabling(true);

        try {
            await apiDisable(instanceId, disabled);
            setSuccess(true);
        } catch (e) {
            setError(e);
        } finally {
            setDisabling(false);
        }
    }, [instanceId, disabled]);

    const reset = useCallback(() => {
        setDisabling(false);
        setSuccess(false);
        setError(undefined);
    }, []);

    const { trigger, refresh } = props;
    const operation = disabled ? 'Disable' : 'Enable';

    return (
        <SingleOperationPopup
            trigger={trigger}
            title={operation + ' the process?'}
            introMsg={
                <p>Are you sure you want to {operation.toLowerCase()} the selected process?</p>
            }
            running={disabling}
            runningMsg={disabled ? 'Disabling...' : 'Enabling...'}
            success={success}
            error={error}
            reset={reset}
            onDone={refresh}
            onConfirm={disableProcess}
        />
    );
});

export default DisableProcessPopup;
