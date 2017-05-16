// @flow
import {combineReducers} from "redux";
import types from "./actions";

const data = (state = [], {type, error, response}) => {
    switch (type) {
        case types.PROCESS_QUEUE_RESPONSE:
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
        case types.PROCESS_QUEUE_RESPONSE:
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
        case types.PROCESS_QUEUE_REQUEST:
            return true;
        case types.PROCESS_QUEUE_RESPONSE:
            return false;
        default:
            return state;
    }
};

export default combineReducers({data, error, loading});

export const getData = (state: any) => state.data;
export const getError = (state: any) => state.error;
export const isLoading = (state: any) => state.loading;
