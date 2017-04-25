// @flow
import type {ConcordId} from "../types";

const NAMESPACE = "process";

const types = {
    PROCESS_INFO_REQUEST: `${NAMESPACE}/info/request`,
    PROCESS_INFO_RESPONSE: `${NAMESPACE}/info/response`,

    PROCESS_KILL_REQUEST: `${NAMESPACE}/kill/request`,
    PROCESS_KILL_RESPONSE: `${NAMESPACE}/kill/response`
};

export default types;

export const load = (instanceId: ConcordId) => ({
    type: types.PROCESS_INFO_REQUEST,
    instanceId
});

export const kill = (instanceId: ConcordId, onSuccess: Array<any>) => ({
    type: types.PROCESS_KILL_REQUEST,
    instanceId,
    onSuccess
});