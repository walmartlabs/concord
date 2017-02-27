// @flow
import {combineReducers} from "redux";
import {actionTypes} from "./actions";
import * as common from "../../reducers/common";

const data = (state = [], action) => {
    switch (action.type) {
        case actionTypes.FETCH_LOG_DATA_REQUEST:
            if (action.error) {
                return state;
            }
            if (action.reset) {
                return [];
            }
            return state;
        case actionTypes.FETCH_LOG_DATA_RESULT:
            return [...state, action.data];
        default:
            return state;
    }
};

const loading = common.makeIsLoadingReducer(actionTypes.FETCH_LOG_DATA_REQUEST, actionTypes.FETCH_LOG_DATA_RESULT);
const error = common.makeErrorReducer(actionTypes.FETCH_LOG_DATA_REQUEST, actionTypes.FETCH_LOG_DATA_RESULT);

const range = (state = {}, action) => {
    switch (action.type) {
        case actionTypes.FETCH_LOG_DATA_REQUEST:
            if (action.reset) {
                return {};
            }
            return state;
        case actionTypes.FETCH_LOG_DATA_RESULT:
            if (action.error) {
                return state;
            }

            const a = action.range.low;
            const b = state.min === undefined ? a : state.min;
            const min = Math.min(a, b);
            return {...action.range, min};
        default:
            return state;
    }
};

const status = (state = null, action) => {
    switch (action.type) {
        case actionTypes.FETCH_LOG_DATA_RESULT:
            if (action.error) {
                return state;
            }

            return action.status;
        default:
            return state;
    }
};

export default combineReducers({data, loading, error, range, status});

export const getData = (state: any) => state.data;
export const getIsLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
export const getRange = (state: any) => state.range;
export const getStatus = (state: any) => state.status;