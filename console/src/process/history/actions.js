// @flow
import type {ConcordId} from "../../types";

const NAMESPACE = "process/history";

const types = {
    PROCESS_HISTORY_REQUEST: `${NAMESPACE}/request`,
    PROCESS_HISTORY_RESPONSE: `${NAMESPACE}/response`,
    PROCESS_HISTORY_REFRESH: `${NAMESPACE}/refresh`
};

export default types;

export const loadData = () => ({
    type: types.PROCESS_HISTORY_REQUEST
});

export const refresh = (instanceId: ConcordId) => ({
    type: types.PROCESS_HISTORY_REFRESH,
    instanceId
});