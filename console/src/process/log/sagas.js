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
import {call, put, fork, takeLatest, all} from "redux-saga/effects";
import * as process from "../api";
import * as log from "./api";
import types from "./actions";

function* loadData(action: any): Generator<*, *, *> {
    try {
        // TODO parallel?
        const status = yield call(process.fetchStatus, action.instanceId);
        const response = yield call(log.fetchLog, action.instanceId, action.fetchRange);

        yield put({
            type: types.PROCESS_LOG_RESPONSE,
            ...response,
            status: status.status
        });
    } catch (e) {
        console.error("fetchLogData -> error", e);
        yield put({
            type: types.PROCESS_LOG_RESPONSE,
            error: true,
            message: e.message || "Error while loading a log file"
        });
    }
}

export default function* (): Generator<*, *, *> {
    yield all([
        fork(takeLatest, types.PROCESS_LOG_REQUEST, loadData)
    ]);
}
