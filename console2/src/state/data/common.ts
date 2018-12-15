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
import { push as pushHistory } from 'react-router-redux';
import { Action, Reducer } from 'redux';
import { put } from 'redux-saga/effects';

import { ConcordId, GenericOperationResult, OperationResult, RequestError } from '../../api/common';

export const nullReducer = <T>(): Reducer<T | null> => (state = null, action) => {
    return state;
};

export const makeLoadingReducer = <T>(requestTypes: T[], responseTypes: T[]): Reducer<boolean> => (
    state = false,
    { type }: Action
) => {
    for (const t of requestTypes) {
        if (t === type) {
            return true;
        }
    }

    for (const t of responseTypes) {
        if (t === type) {
            return false;
        }
    }

    return state;
};

export const makeErrorReducer = <T>(
    requestTypes: T[],
    responseTypes: T[]
): Reducer<RequestError> => (state = null, { type, error }: { type: T; error: RequestError }) => {
    for (const t of requestTypes) {
        if (t === type) {
            return null;
        }
    }

    for (const t of responseTypes) {
        if (t === type) {
            return error ? error : null;
        }
    }

    return state;
};

interface HasError {
    error?: RequestError;
}

export const makeResponseReducer = <R, A extends R & Action & HasError>(
    responseType: {},
    resetType?: {}
): Reducer<R | null> => (state = null, action: A) => {
    switch (action.type) {
        case responseType:
            return action;
        case resetType:
            if (action.error) {
                return state;
            }
            return null;
        default:
            return state;
    }
};

interface HasId {
    id: ConcordId;
}

interface HasResponse<T extends HasId> {
    error?: RequestError;
    items?: T[];
}

export interface CollectionById<T extends HasId> {
    [id: string]: T;
}

export const makeEntityByIdReducer = <T extends HasId>(
    responseType: string
): Reducer<CollectionById<T>> => (state = {}, action: HasResponse<T> & Action) => {
    switch (action.type) {
        case responseType:
            if (action.error || !action.items) {
                return {};
            }

            const result = {};
            action.items.forEach((o) => {
                result[o.id] = o;
            });
            return result;
        default:
            return state;
    }
};

export function* handleErrors(responseType: string, error: RequestError) {
    console.debug("handleErrors ['%s', '%o']", responseType, error);

    yield put({
        type: responseType,
        error
    });

    if (error && error.status === 401) {
        // TODO we add a visual clue why we are redirecting the user
        yield put(pushHistory('/login'));
    }
}

export interface GenericOperationResultAction extends Action {
    ok: boolean;
    result: OperationResult;
}

export const genericResult = <T = any>(
    type: T,
    result: GenericOperationResult
): GenericOperationResultAction => ({
    type,
    ...result
});

export interface RequestState<T> {
    running: boolean;
    error: RequestError;
    response: T | null;
}
