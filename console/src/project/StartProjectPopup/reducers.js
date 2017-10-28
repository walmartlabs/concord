// @flow
import {combineReducers} from "redux";
import types from "./actions";

const result = (state: any = null, {type, error, response}: any) => {
    switch (type) {
        case types.PROJECT_START_RESPONSE:
            return {...response, error};
        case types.PROJECT_START_REQUEST:
        case types.PROJECT_START_RESET:
            return null;
        default:
            return state;
    }
};

const loading = (state = false, {type}: any) => {
    switch (type) {
        case types.PROJECT_START_REQUEST:
            return true;
        case types.PROJECT_START_RESPONSE:
        case types.PROJECT_START_RESET:
            return false;
        default:
            return state;
    }
};

export default combineReducers({result, loading});

export const getResult = (state: any) => state.result;
export const isLoading = (state: any) => state.loading;
