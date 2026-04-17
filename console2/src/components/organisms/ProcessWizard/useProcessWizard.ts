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
import { useEffect, useState } from 'react';
import { useHistory } from '@/router';

import { ConcordId, RequestError } from '../../../api/common';
import { get as apiGetProcess, isFinal, ProcessStatus } from '../../../api/process';
import { list as apiListForms } from '../../../api/process/form';
import { getProcessLocation, openProcessForm } from '../ProcessFormActivity/processFormNavigation';

const POLL_INTERVAL = 1000;

export const useProcessWizard = (processInstanceId: ConcordId) => {
    const history = useHistory();
    const [error, setError] = useState<RequestError>();

    useEffect(() => {
        let cancelled = false;
        let timeoutId: number | undefined;

        const stopPolling = () => {
            if (timeoutId !== undefined) {
                window.clearTimeout(timeoutId);
                timeoutId = undefined;
            }
        };

        const poll = async () => {
            try {
                const process = await apiGetProcess(processInstanceId, []);
                const forms = await apiListForms(processInstanceId);

                if (cancelled) {
                    return;
                }

                if (forms.length > 0 && process.status === ProcessStatus.SUSPENDED) {
                    const [listedForm] = forms;

                    await openProcessForm({
                        history,
                        processInstanceId,
                        formName: listedForm.name,
                        custom: listedForm.custom,
                        yieldFlow: listedForm.yield,
                    });
                    return;
                }

                if (isFinal(process.status)) {
                    history.push(getProcessLocation(processInstanceId));
                    return;
                }

                timeoutId = window.setTimeout(poll, POLL_INTERVAL);
            } catch (e) {
                if (!cancelled) {
                    setError(e);
                }
            }
        };

        setError(undefined);
        poll();

        return () => {
            cancelled = true;
            stopPolling();
        };
    }, [history, processInstanceId]);

    return error;
};
