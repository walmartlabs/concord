// @flow
import {combineReducers} from "redux";
import {actionTypes} from "./actions";
import * as common from "../../reducers/common";

const error = common.makeErrorReducer(actionTypes.SHOW_NEXT_PROCESS_FORM, actionTypes.CANCEL_PROCESS_WIZARD);

export default combineReducers({error});

export const getError = (state: any) => state.error;

