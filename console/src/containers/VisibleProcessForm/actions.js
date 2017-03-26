// @flow
import type {ConcordId} from "../../types";

export const actionTypes = {
    FETCH_PROCESS_FORM_REQUEST: "FETCH_PROCESS_FORM_REQUEST",
    FETCH_PROCESS_FORM_RESULT: "FETCH_PROCESS_FORM_RESULT",

    SUBMIT_PROCESS_FORM_REQUEST: "SUBMIT_PROCESS_FORM_REQUEST",
    SUBMIT_PROCESS_FORM_RESULT: "SUBMIT_PROCESS_FORM_RESULT"
};

export const fetchData = (processInstanceId: ConcordId, formInstanceId: ConcordId) => ({
    type: actionTypes.FETCH_PROCESS_FORM_REQUEST,
    processInstanceId,
    formInstanceId
});

export const submit = (processInstanceId: ConcordId, formInstanceId: ConcordId, data: mixed, wizard: boolean) => ({
    type: actionTypes.SUBMIT_PROCESS_FORM_REQUEST,
    processInstanceId,
    formInstanceId,
    data,
    wizard
});
