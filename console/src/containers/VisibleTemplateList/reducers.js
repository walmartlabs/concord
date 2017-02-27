// @flow
import {combineReducers} from "redux";
import {actionTypes} from "./actions";
import * as common from "../../reducers/common";

const rows = common.makeListRowsReducer(actionTypes.FETCH_TEMPLATE_LIST_RESULT);
const loading = common.makeIsLoadingReducer(actionTypes.FETCH_TEMPLATE_LIST_REQUEST, actionTypes.FETCH_TEMPLATE_LIST_RESULT);
const error = common.makeErrorReducer(actionTypes.FETCH_TEMPLATE_LIST_REQUEST, actionTypes.FETCH_TEMPLATE_LIST_RESULT);

export default combineReducers({rows, loading, error});

export const getRows = (state: any) => state.rows;
export const getIsLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
