import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';
import { ConcordKey } from '../../../api/common';
import { listTriggers as apiList } from '../../../api/org/project/repository';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { ListTriggersRequest, ListTriggersState, State } from './types';

export { State };

const NAMESPACE = 'triggers';

const actionTypes = {
    LIST_TRIGGERS_REQUEST: `${NAMESPACE}/list/request`,
    LIST_TRIGGERS_RESPONSE: `${NAMESPACE}/list/response`
};

export const actions = {
    listTriggers: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey
    ): ListTriggersRequest => ({
        type: actionTypes.LIST_TRIGGERS_REQUEST,
        orgName,
        projectName,
        repoName
    })
};

const listTriggersReducer = combineReducers<ListTriggersState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_TRIGGERS_REQUEST],
        [actionTypes.LIST_TRIGGERS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.LIST_TRIGGERS_REQUEST],
        [actionTypes.LIST_TRIGGERS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.LIST_TRIGGERS_RESPONSE)
});

export const reducers = combineReducers<State>({
    listTriggers: listTriggersReducer
});

function* onList({ orgName, projectName, repoName }: ListTriggersRequest) {
    try {
        const response = yield call(apiList, orgName, projectName, repoName);
        yield put({
            type: actionTypes.LIST_TRIGGERS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_TRIGGERS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.LIST_TRIGGERS_REQUEST, onList)]);
};
