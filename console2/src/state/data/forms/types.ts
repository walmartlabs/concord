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

import { Action } from 'redux';

import { ConcordId, RequestError } from '../../../api/common';
import { FormInstanceEntry, FormSubmitResponse } from '../../../api/process/form';
import { RequestState } from '../common';

export interface GetProcessFormRequest extends Action {
    processInstanceId: ConcordId;
    formInstanceId: string;
}

export interface FormDataType {
    [name: string]: any;
}

export interface SubmitProcessFormRequest extends Action {
    processInstanceId: ConcordId;
    formInstanceId: string;
    wizard: boolean;
    yieldFlow: boolean;
    data: FormDataType;
}

export interface StartProcessWizard extends Action {
    processInstanceId: ConcordId;
}

export interface StartProcessForm extends Action {
    processInstanceId: ConcordId;
    formInstanceId: string;
}

export type GetProcessFormState = RequestState<FormInstanceEntry>;
export type SubmitProcessFormState = RequestState<FormSubmitResponse>;

export interface ProcessWizardState {
    error: RequestError;
}

export interface State {
    get: GetProcessFormState;
    submit: SubmitProcessFormState;
    wizard: ProcessWizardState;
}
