import {combineReducers} from "redux";
import actionTypes from "../actions/actionTypes";

const rows = (state = [], action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_RESULT:
            if (action.error) {
                return state;
            }
            return action.response;
        default:
            return state;
    }
};

const loading = (state = false, action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_REQUEST:
            return true;
        case actionTypes.history.FETCH_HISTORY_DATA_RESULT:
            return false;
        default:
            return state;
    }
};

const error = (state = null, action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_RESULT:
            if (action.error) {
                return action.message;
            }
            return null;
        default:
            return state;
    }
};

const lastQuery = (state = null, action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_REQUEST:
            return { sortBy: action.sortBy, sortDir: action.sortDir };
        default:
            return state;
    }
};

export default combineReducers({rows, loading, error, lastQuery});

export const getRows = (state) => state.rows;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
export const getLastQuery = (state) => state.lastQuery;
