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
import { RequestState } from '../../common';
import { ConcordId } from '../../../../api/common';

export interface ListProcessAttachments extends Action {
    instanceId: ConcordId;
}

export interface ListProcessAttachmentResponse extends Action {
    items: string[];
}

export type ListProcessAttachmentState = RequestState<ListProcessAttachmentResponse>;

export interface State {
    list: ListProcessAttachmentState;
}
