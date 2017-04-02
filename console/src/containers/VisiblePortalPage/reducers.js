// @flow
import {combineReducers} from "redux";
import {actionTypes} from "./actions";
import * as common from "../../reducers/common";

const submitting = common.makeBooleanTriggerReducer(actionTypes.START_PROCESS_REQUEST, actionTypes.START_PROCESS_RESULT);
const error = common.makeErrorReducer(actionTypes.START_PROCESS_REQUEST, actionTypes.START_PROCESS_RESULT);

export default combineReducers({submitting, error});

export const getIsSubmitting = (state: any) => state.submitting;
export const getError = (state: any) => state.error;

