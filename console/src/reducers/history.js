import {combineReducers} from "redux";
import actionTypes from "../actions/actionTypes";

const rows = (state = [], action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_SUCCESS:
            return action.response;
        default:
            return state;
    }
};

const loading = (state = false, action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_REQUEST:
            return true;
        case actionTypes.history.FETCH_HISTORY_DATA_SUCCESS:
        case actionTypes.history.FETCH_HISTORY_DATA_FAILURE:
            return false;
        default:
            return state;
    }
};

const error = (state = null, action) => {
    switch (action.type) {
        case actionTypes.history.FETCH_HISTORY_DATA_SUCCESS:
            return null;
        case actionTypes.history.FETCH_HISTORY_DATA_FAILURE:
            return action.message;
        default:
            return state;
    }
};

export default combineReducers({rows, loading, error});

export const getRows = (state) => state.rows;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
