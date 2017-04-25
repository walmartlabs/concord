// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import types from "./actions";
import * as api from "./api";

function* load(action: any): Generator<*, *, *> {
    const {instanceId} = action;

    try {
        const [status, forms] = yield [
            call(api.fetchStatus, instanceId),
            call(api.listForms, instanceId)
        ];

        yield put({
            type: types.PROCESS_INFO_RESPONSE,
            instanceId,
            response: {
                ...status,
                forms
            }
        });
    } catch (e) {
        yield put({
            type: types.PROCESS_INFO_RESPONSE,
            instanceId,
            error: true,
            message: e.message || "Error while loading a process info"
        });
    }
}

function* kill(action: any): Generator<*, *, *> {
    const {instanceId, onSuccess} = action;

    try {
        yield call(api.kill, instanceId);

        yield put({
            type: types.PROCESS_KILL_RESPONSE,
            instanceId
        });

        if (onSuccess) {
            for (const a of onSuccess) {
                yield put(a);
            }
        }
    } catch (e) {
        yield put({
            type: types.PROCESS_KILL_RESPONSE,
            instanceId,
            error: true,
            message: e.message || "Error while killing a process"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.PROCESS_INFO_REQUEST, load),
        fork(takeLatest, types.PROCESS_KILL_REQUEST, kill)
    ];
}
