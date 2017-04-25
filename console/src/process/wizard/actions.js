// @flow
import type {ConcordId} from "../../types";

const NAMESPACE = "process/wizard";

const types = {
    PROCESS_WIZARD_NEXT_FORM: `${NAMESPACE}/nextForm`,
    PROCESS_WIZARD_CANCEL: `${NAMESPACE}/cancel`
};

export default types;

export const showNextForm = (instanceId: ConcordId) => ({
    type: types.PROCESS_WIZARD_NEXT_FORM,
    instanceId
});
