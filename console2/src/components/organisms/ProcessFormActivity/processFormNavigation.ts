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
import { ConcordId } from '../../../api/common';
import { startSession as apiStartSession } from '../../../api/service/custom_form';

export interface RouterLocation {
    pathname: string;
    search?: string;
}

interface NavigationHistory {
    push(location: RouterLocation): void;
    replace(location: RouterLocation): void;
}

interface OpenProcessFormArgs {
    history: NavigationHistory;
    processInstanceId: ConcordId;
    formName: string;
    custom: boolean;
    yieldFlow: boolean;
}

const CUSTOM_FORM_SESSION_RETRIES = 5;
const CUSTOM_FORM_SESSION_RETRY_DELAY_MS = 300;

const updateForDev = (uri: string) => {
    if (process.env.NODE_ENV !== 'production') {
        return `http://localhost:8001${uri}`;
    }

    return uri;
};

const delay = (ms: number) =>
    new Promise<void>((resolve) => {
        window.setTimeout(resolve, ms);
    });

const shouldRetryCustomFormSession = (error: unknown) =>
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    (error as { status?: number }).status === 404;

const startCustomFormSession = async (processInstanceId: ConcordId, formName: string) => {
    for (let attempt = 0; attempt < CUSTOM_FORM_SESSION_RETRIES; attempt++) {
        try {
            return await apiStartSession(processInstanceId, formName);
        } catch (error) {
            if (
                !shouldRetryCustomFormSession(error) ||
                attempt === CUSTOM_FORM_SESSION_RETRIES - 1
            ) {
                throw error;
            }

            await delay(CUSTOM_FORM_SESSION_RETRY_DELAY_MS);
        }
    }

    throw new Error('Unreachable');
};

export const getProcessLocation = (processInstanceId: ConcordId): RouterLocation => ({
    pathname: `/process/${processInstanceId}`,
});

export const getWizardLocation = (processInstanceId: ConcordId): RouterLocation => ({
    pathname: `/process/${processInstanceId}/wizard`,
    search: 'fullScreen=true',
});

export const getWizardFormLocation = (
    processInstanceId: ConcordId,
    formName: string,
    yieldFlow: boolean
): RouterLocation => ({
    pathname: `/process/${processInstanceId}/form/${formName}/wizard`,
    search: `fullScreen=true&yieldFlow=${yieldFlow}`,
});

export const openProcessForm = async ({
    history,
    processInstanceId,
    formName,
    custom,
    yieldFlow,
}: OpenProcessFormArgs) => {
    if (custom) {
        const { uri } = await startCustomFormSession(processInstanceId, formName);
        window.location.replace(updateForDev(uri));
        return;
    }

    history.replace(getWizardFormLocation(processInstanceId, formName, yieldFlow));
};
