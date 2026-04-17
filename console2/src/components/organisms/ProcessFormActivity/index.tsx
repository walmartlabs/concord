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
import { useCallback, useEffect, useRef, useState } from 'react';
import { useHistory } from '@/router';
import { Loader, Segment } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import {
    FormDataType,
    FormInstanceEntry,
    FormSubmitErrors,
    get as apiGetForm,
    submit as apiSubmitForm,
} from '../../../api/process/form';
import { ProcessForm, RequestErrorMessage } from '../../molecules';
import { getProcessLocation, getWizardLocation, openProcessForm } from './processFormNavigation';

interface Props {
    processInstanceId: ConcordId;
    formName: string;
    wizard: boolean;
}

const ProcessFormActivity = ({ processInstanceId, formName, wizard }: Props) => {
    const history = useHistory();
    const redirectTimeoutRef = useRef<number | undefined>(undefined);
    const submitRequestIdRef = useRef(0);

    const [form, setForm] = useState<FormInstanceEntry>();
    const [loading, setLoading] = useState(false);
    const [loadError, setLoadError] = useState<RequestError>();
    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<RequestError>();
    const [validationErrors, setValidationErrors] = useState<FormSubmitErrors>();
    const [completed, setCompleted] = useState(false);

    const clearSubmitState = useCallback(() => {
        setSubmitting(false);
        setSubmitError(undefined);
        setValidationErrors(undefined);
        setCompleted(false);
    }, []);

    const clearRedirectTimeout = useCallback(() => {
        if (redirectTimeoutRef.current !== undefined) {
            window.clearTimeout(redirectTimeoutRef.current);
            redirectTimeoutRef.current = undefined;
        }
    }, []);

    useEffect(() => {
        let cancelled = false;

        submitRequestIdRef.current += 1;
        clearRedirectTimeout();
        clearSubmitState();

        const loadForm = async () => {
            setLoading(true);
            setLoadError(undefined);
            setForm(undefined);

            try {
                const response = await apiGetForm(processInstanceId, formName);
                if (!cancelled) {
                    setForm(response);
                }
            } catch (e) {
                if (!cancelled) {
                    setLoadError(e);
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        loadForm();

        return () => {
            cancelled = true;
            submitRequestIdRef.current += 1;
            clearRedirectTimeout();
        };
    }, [clearRedirectTimeout, clearSubmitState, formName, processInstanceId]);

    useEffect(() => () => clearRedirectTimeout(), [clearRedirectTimeout]);

    const onSubmit = useCallback(
        async (data: FormDataType) => {
            const requestId = submitRequestIdRef.current + 1;
            submitRequestIdRef.current = requestId;

            clearRedirectTimeout();
            setSubmitting(true);
            setSubmitError(undefined);
            setValidationErrors(undefined);
            setCompleted(false);

            try {
                const response = await apiSubmitForm(processInstanceId, formName, data);

                if (submitRequestIdRef.current !== requestId) {
                    return;
                }

                setValidationErrors(response.errors);
                setCompleted(response.ok);

                if (!response.ok || !wizard || !form) {
                    return;
                }

                if (form.yield) {
                    redirectTimeoutRef.current = window.setTimeout(() => {
                        if (submitRequestIdRef.current === requestId) {
                            history.push(getProcessLocation(processInstanceId));
                        }
                    }, 1000);
                    return;
                }

                history.push(getWizardLocation(processInstanceId));
            } catch (e) {
                if (submitRequestIdRef.current === requestId) {
                    setSubmitError(e);
                }
            } finally {
                if (submitRequestIdRef.current === requestId) {
                    setSubmitting(false);
                }
            }
        },
        [clearRedirectTimeout, form, formName, history, processInstanceId, wizard]
    );

    const onStartCustomForm = useCallback(
        async (event: React.MouseEvent<HTMLAnchorElement>) => {
            event.preventDefault();

            if (!form) {
                return;
            }

            const requestId = submitRequestIdRef.current + 1;
            submitRequestIdRef.current = requestId;

            clearRedirectTimeout();
            setSubmitError(undefined);

            try {
                await openProcessForm({
                    history,
                    processInstanceId,
                    formName,
                    custom: form.custom,
                    yieldFlow: form.yield,
                });
            } catch (e) {
                if (submitRequestIdRef.current === requestId) {
                    setSubmitError(e);
                }
            }
        },
        [clearRedirectTimeout, form, formName, history, processInstanceId]
    );

    const onReturn = useCallback(() => {
        history.push(getProcessLocation(processInstanceId));
    }, [history, processInstanceId]);

    if (loading) {
        return <Loader active={true} />;
    }

    if (loadError) {
        return <RequestErrorMessage error={loadError} />;
    }

    if (!form || !form.fields) {
        return <h3>Form not found.</h3>;
    }

    return (
        <>
            {form.custom && (
                <Segment>
                    This form has a {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
                    <a href="#" onClick={onStartCustomForm}>
                        custom view
                    </a>
                    .
                </Segment>
            )}

            <ProcessForm
                form={form}
                submitting={submitting}
                submitError={submitError}
                completed={completed}
                errors={validationErrors}
                onSubmit={onSubmit}
                onReturn={onReturn}
            />
        </>
    );
};

export default ProcessFormActivity;
