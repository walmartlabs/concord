// @flow
import type {ConcordId} from "../../types";

const NAMESPACE = "process/form";

const types = {
    PROCESS_FORM_REQUEST: `${NAMESPACE}/request`,
    PROCESS_FORM_RESPONSE: `${NAMESPACE}/response`,

    PROCESS_FORM_SUBMIT_REQUEST: `${NAMESPACE}/submit/request`,
    PROCESS_FORM_SUBMIT_RESPONSE: `${NAMESPACE}/submit/response`
};

export default types;

export const loadData = (instanceId: ConcordId, formInstanceId: ConcordId) => ({
    type: types.PROCESS_FORM_REQUEST,
    instanceId,
    formInstanceId
});

export const submit = (instanceId: ConcordId, formInstanceId: ConcordId, data: mixed, wizard: boolean, yieldFlow: boolean) => ({
    type: types.PROCESS_FORM_SUBMIT_REQUEST,
    instanceId,
    formInstanceId,
    data,
    wizard,
    yieldFlow
});
