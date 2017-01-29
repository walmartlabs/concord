import {combineReducers} from "redux";
import actionTypes from "../actions/actionTypes";

const data = (state = null, action) => {
    switch (action.type) {
        case actionTypes.log.FETCH_LOG_DATA_REQUEST:
            return null;
        case actionTypes.log.FETCH_LOG_DATA_SUCCESS:
            return action.response;
        case actionTypes.log.FETCH_LOG_DATA_FAILURE:
            return action.message;
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
}

export default combineReducers({data, loading, error});

export const getData = (state) => state.data;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
