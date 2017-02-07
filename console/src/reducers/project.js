import {combineReducers} from "redux";
import {project as actionTypes} from "../actions/actionTypes";
import * as common from "./common";

const data = (state = null, action) => {
    switch (action.type) {
        case actionTypes.FETCH_PROJECT_RESULT:
            if (action.error) {
                return state;
            }
            return action.response;
        case actionTypes.MAKE_NEW_PROJECT:
            return null;
        default:
            return state;
    }
};

const loading = common.makeIsLoadingReducer(actionTypes.FETCH_PROJECT_REQUEST, actionTypes.FETCH_PROJECT_RESULT);
const error = common.makeErrorReducer(actionTypes.FETCH_PROJECT_REQUEST, actionTypes.FETCH_PROJECT_RESULT);

export default combineReducers({data, loading, error});

export const getData = (state) => state.data;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
