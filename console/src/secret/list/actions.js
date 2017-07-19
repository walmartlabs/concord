// @flow
import type {ConcordKey} from "../../types";

const NAMESPACE = "user/secret";

const types = {
    USER_SECRET_LIST_REQUEST: `${NAMESPACE}/list/request`,
    USER_SECRET_LIST_RESPONSE: `${NAMESPACE}/list/response`,

    USER_SECRET_DELETE_REQUEST: `${NAMESPACE}/delete/request`,
    USER_SECRET_DELETE_RESPONSE: `${NAMESPACE}/delete/response`
};

export default types;

export const fetchSecretList = () => ({
    type: types.USER_SECRET_LIST_REQUEST
});

export const deleteSecret = (name: ConcordKey, onSuccess: Array<string>) => ({
    type: types.USER_SECRET_DELETE_REQUEST,
    name,
    onSuccess
});