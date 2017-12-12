// @flow
import type {ConcordKey} from "../../types";

const NAMESPACE = "project/StartProjectPopup";

const types = {
    PROJECT_START_REQUEST: `${NAMESPACE}/start/request`,
    PROJECT_START_RESPONSE: `${NAMESPACE}/start/response`,
    PROJECT_START_RESET: `${NAMESPACE}/start/reset`
};

export default types;

export const startProject = (repositoryId: ConcordKey) => ({
    type: types.PROJECT_START_REQUEST,
    repositoryId
});

export const resetStart = () => ({
    type: types.PROJECT_START_RESET
});
