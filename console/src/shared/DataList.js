// @flow
import {combineReducers} from "redux";
import {call, fork, put, takeLatest} from "redux-saga/effects";

const NAMESPACE = "shared/data_list";

const actionTypes = {
    DATA_LIST_REQUEST: `${NAMESPACE}/request`,
    DATA_LIST_RESPONSE: `${NAMESPACE}/response`
};

/**
 * Generates actions, reducers, selectors and sagas for a simple list backed by a function.
 * Includes a loading state indicator and error handling.
 *
 * @param key Unique ID of the component. Will be used to dispatch and process actions.
 * @param dataFn A function to retrieve data.
 * @returns {{
 *  actionTypes: {API_TABLE_LIST_REQUEST: string, API_TABLE_LIST_RESPONSE: string},
 *  actions: {loadData: (function(): {type: string, componentKey: String})},
 *  reducers: Reducer<S>, selectors: {getData: (function(any): *), getError: (function(any): *), isLoading: (function(any): *)},
 *  sagas: sagas
 * }}
 */
export default (key: string, dataFn: Function) => {

    // ACTIONS

    const types = {
        DATA_LIST_REQUEST: `${actionTypes.DATA_LIST_REQUEST}/${key}`,
        DATA_LIST_RESPONSE: `${actionTypes.DATA_LIST_RESPONSE}/${key}`
    };

    const actions = {
        loadData: () => ({
            type: types.DATA_LIST_REQUEST,
            componentKey: key
        })
    };

    // REDUCERS

    const data = (state = [], {type, error, response}) => {
        switch (type) {
            case types.DATA_LIST_RESPONSE:
                if (error) {
                    return state;
                }
                return response;
            default:
                return state;
        }
    };

    const error = (state = null, {type, error, message}) => {
        switch (type) {
            case types.DATA_LIST_RESPONSE:
                if (error) {
                    return message;
                }
                return null;
            default:
                return state;
        }
    };

    const loading = (state = false, {type}) => {
        switch (type) {
            case types.DATA_LIST_REQUEST:
                return true;
            case types.DATA_LIST_RESPONSE:
                return false;
            default:
                return state;
        }
    };

    const reducers = combineReducers({data, error, loading});

    // SELECTORS

    const selectors = {
        getData: (state: any) => state.data,
        getError: (state: any) => state.error,
        isLoading: (state: any) => state.loading
    };

    // SAGAS

    function* loadData(action: any): Generator<*, *, *> {
        try {
            const response = yield call(dataFn);
            yield put({
                type: types.DATA_LIST_RESPONSE,
                response
            });
        } catch (e) {
            yield put({
                type: types.DATA_LIST_RESPONSE,
                error: true,
                message: e.message || "Error while loading data"
            });
        }
    }

    function* sagas(): Generator<*, *, *> {
        yield [
            fork(takeLatest, types.DATA_LIST_REQUEST, loadData)
        ];
    }

    return {types, actions, reducers, selectors, sagas}
};
