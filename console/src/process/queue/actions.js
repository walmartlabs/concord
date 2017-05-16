// @flow
import type {ConcordId} from "../../types";

const NAMESPACE = "process/queue";

const types = {
    PROCESS_QUEUE_REQUEST: `${NAMESPACE}/request`,
    PROCESS_QUEUE_RESPONSE: `${NAMESPACE}/response`,
    PROCESS_QUEUE_REFRESH: `${NAMESPACE}/refresh`
};

export default types;

export const loadData = () => ({
    type: types.PROCESS_QUEUE_REQUEST
});

export const refresh = (instanceId: ConcordId) => ({
    type: types.PROCESS_QUEUE_REFRESH,
    instanceId
});