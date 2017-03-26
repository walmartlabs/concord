// @flow
import type {ConcordId} from "../../types";

export const actionTypes = {
    SHOW_NEXT_PROCESS_FORM: "SHOW_NEXT_PROCESS_FORM",
    CANCEL_PROCESS_WIZARD: "CANCEL_PROCESS_WIZARD"
};

export const showNextForm = (processInstanceId: ConcordId) => ({
    type: actionTypes.SHOW_NEXT_PROCESS_FORM,
    processInstanceId
});