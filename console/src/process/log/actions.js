// @flow
import type {ConcordId, FetchRange} from "../../types";

const NAMESPACE = "process/log";

const types = {
    PROCESS_LOG_REQUEST: `${NAMESPACE}/request`,
    PROCESS_LOG_RESPONSE: `${NAMESPACE}/response`
};

export default types;

export const loadData = (instanceId: ConcordId, fetchRange: FetchRange, reset: boolean) => ({
    type: types.PROCESS_LOG_REQUEST,
    instanceId,
    fetchRange,
    reset
});
