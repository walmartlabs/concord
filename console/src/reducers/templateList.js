import {combineReducers} from "redux";
import {templateList as actionTypes} from "../actions/actionTypes";
import * as common from "./common";

const rows = common.makeListRowsReducer(actionTypes.FETCH_TEMPLATE_LIST_RESULT);
const loading = common.makeIsLoadingReducer(actionTypes.FETCH_TEMPLATE_LIST_REQUEST, actionTypes.FETCH_TEMPLATE_LIST_RESULT);
const error = common.makeErrorReducer(actionTypes.FETCH_TEMPLATE_LIST_REQUEST, actionTypes.FETCH_TEMPLATE_LIST_RESULT);

export default combineReducers({rows, loading, error});

export const getRows = (state) => state.rows;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
