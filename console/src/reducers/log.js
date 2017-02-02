import {combineReducers} from "redux";
import actionTypes from "../actions/actionTypes";

const data = (state = [], action) => {
    switch (action.type) {
        case actionTypes.log.FETCH_LOG_DATA_REQUEST:
            if (action.fresh) {
                return [];
            }
            return state;
        case actionTypes.log.FETCH_LOG_DATA_SUCCESS:
            return [...state, action.data];
        case actionTypes.log.FETCH_LOG_DATA_FAILURE:
            return [action.message];
        default:
            return state;
    }
};

const loading = (state = false, action) => {
    switch (action.type) {
        case actionTypes.log.FETCH_LOG_DATA_REQUEST:
            return true;
        case actionTypes.log.FETCH_LOG_DATA_SUCCESS:
        case actionTypes.log.FETCH_LOG_DATA_FAILURE:
            return false;
        default:
            return state;
    }
};

const error = (state = null, action) => {
    switch (action.type) {
        case actionTypes.log.FETCH_LOG_DATA_SUCCESS:
            return null;
        case actionTypes.log.FETCH_LOG_DATA_FAILURE:
            return action.message;
        default:
            return state;
    }
};

const range = (state = {}, action) => {
    switch (action.type) {
        case actionTypes.log.FETCH_LOG_DATA_REQUEST:
            if (action.fresh) {
                return {};
            }
            return state;
        case actionTypes.log.FETCH_LOG_DATA_SUCCESS:
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
        case actionTypes.log.FETCH_LOG_DATA_SUCCESS:
            return action.status;
        default:
            return state;
    }
};

export default combineReducers({data, loading, error, range, status});

export const getData = (state) => state.data;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
export const getRange = (state) => state.range;
export const getStatus = (state) => state.status;