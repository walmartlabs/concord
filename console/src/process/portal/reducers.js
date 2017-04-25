// @flow
import {combineReducers} from "redux";
import types from "./actions";
import * as common from "../../reducers/common";

const submitting = common.booleanTrigger(types.PROCESS_PORTAL_START_REQUEST, types.PROCESS_PORTAL_START_RESPONSE);
const error = common.error(types.PROCESS_PORTAL_START_RESPONSE);

export default combineReducers({submitting, error});

export const getIsSubmitting = (state: any) => state.submitting;
export const getError = (state: any) => state.error;

