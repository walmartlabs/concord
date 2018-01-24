/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
// @flow
import { combineReducers } from 'redux';
import types from './actions';
import * as common from '../../reducers/common';

const submitting = common.booleanTrigger(
  types.PROCESS_PORTAL_START_REQUEST,
  types.PROCESS_PORTAL_START_RESPONSE
);
const error = common.error(types.PROCESS_PORTAL_START_RESPONSE);

export default combineReducers({ submitting, error });

export const getIsSubmitting = (state: any) => state.submitting;
export const getError = (state: any) => state.error;
