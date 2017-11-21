// @flow
import {combineReducers} from "redux";
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {reset as resetForm} from "redux-form";

import * as api from "./api";

const NAMESPACE = "team/secret";

const actionTypes = {
    CREATE_REQUEST: `${NAMESPACE}/create/request`,
    CREATE_RESPONSE: `${NAMESPACE}/create/response`,
    CREATE_RESET: `${NAMESPACE}/create/reset`
};

// Actions

export const actions = {
    createNewSecret: (teamName: string, req: any) => ({
        type: actionTypes.CREATE_REQUEST,
        teamName,
        ...req
    }),

    reset: () => ({
        type: actionTypes.CREATE_RESET
    })
};

// Reducers

const response = (state = null, {type, ...rest}) => {
    switch (type) {
        case actionTypes.CREATE_REQUEST:
        case actionTypes.CREATE_RESET: {
            return null;
        }
        case actionTypes.CREATE_RESPONSE: {
            return rest;
        }
        default: {
            return state;
        }
    }
};

const loading = (state = false, {type}) => {
    switch (type) {
        case actionTypes.CREATE_REQUEST: {
            return true;
        }
        case actionTypes.CREATE_RESPONSE:
        case actionTypes.CREATE_RESET: {
            return false;
        }
        default: {
            return state;
        }
    }
};

const error = (state = null, {type, error, message}) => {
    switch (type) {
        case actionTypes.CREATE_RESPONSE: {
            if (!error) {
                return null;
            }
            return message;
        }
        case actionTypes.CREATE_RESET: {
            return null;
        }
        default: {
            return state;
        }
    }
};

export const reducers = combineReducers({response, error, loading});

// Selectors

export const selectors = {
    response: (state: any) => state.response,
    error: (state: any) => state.error,
    loading: (state: any) => state.loading
};

// Sagas

function* onCreate(action: any): Generator<*, *, *> {
    try {
        const resp = yield call(api.create, action);
        if (resp.error) {
            yield put({
                type: actionTypes.CREATE_RESPONSE,
                error: true,
                message: resp.message || "Error while creating a secret"
            });
        } else {
            yield put({
                type: actionTypes.CREATE_RESPONSE,
                ...resp
            });

            yield put(resetForm("newSecretForm"));
        }
    } catch (e) {
        yield put({
            type: actionTypes.CREATE_RESPONSE,
            error: true,
            message: e.message || "Error while creating a secret"
        });
    }
}

function* onReset(action: any): Generator<*, *, *> {
    yield put(resetForm("newSecretForm"));
}

export function* sagas(): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.CREATE_REQUEST, onCreate),
        fork(takeLatest, actionTypes.CREATE_RESET, onReset)
    ];
};