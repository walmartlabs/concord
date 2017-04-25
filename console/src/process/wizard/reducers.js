// @flow
import {combineReducers} from "redux";
import types from "./actions";
import * as common from "../../reducers/common";

const error = common.error(types.PROCESS_WIZARD_CANCEL);

export default combineReducers({error});

export const getError = (state: any) => state.error;
