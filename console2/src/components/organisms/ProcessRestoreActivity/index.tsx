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
import { useCallback, useState } from 'react';

import { ConcordKey, RequestError } from '../../../api/common';
import { isFinal, ProcessStatus } from '../../../api/process';
import { restoreProcess as apiRestoreProcess } from '../../../api/process/checkpoint';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

interface Props {
    instanceId: ConcordKey;
    processStatus: ProcessStatus;
    checkpointId: ConcordKey;
    checkpoint: string;
    renderOverride?: React.ReactNode;
}

export const isFinalStatus = (status: ProcessStatus): boolean => isFinal(status);

const ProcessRestoreActivity = ({
    instanceId,
    processStatus,
    checkpointId,
    checkpoint,
    renderOverride
}: Props) => {
    const [restoring, setRestoring] = useState(false);
    const [error, setError] = useState<RequestError>();

    const restoreProcess = useCallback(async () => {
        setRestoring(true);
        setError(undefined);

        try {
            await apiRestoreProcess(instanceId, checkpointId);
        } catch (e) {
            setError(e);
        } finally {
            setRestoring(false);
        }
    }, [checkpointId, instanceId]);

    return (
        <>
            {error && <RequestErrorMessage error={error} />}

            <ButtonWithConfirmation
                renderOverride={renderOverride}
                size={'mini'}
                floated={'right'}
                disabled={!isFinalStatus(processStatus)}
                content="restore"
                loading={restoring}
                confirmationHeader="Restore the process?"
                confirmationContent={`Are you sure you want to restore process at ${checkpoint} checkpoint?`}
                onConfirm={restoreProcess}
            />
        </>
    );
};

export default ProcessRestoreActivity;
