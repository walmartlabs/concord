// @flow
import type {ConcordKey} from "../../../types";

const NAMESPACE = "user/secret";

export const types = {
    USER_SECRET_LIST_REQUEST: `${NAMESPACE}/list/request`,
    USER_SECRET_LIST_RESPONSE: `${NAMESPACE}/list/response`,

    USER_SECRET_DELETE_REQUEST: `${NAMESPACE}/delete/request`,
    USER_SECRET_DELETE_RESPONSE: `${NAMESPACE}/delete/response`,

    USER_SECRET_PUBLICKEY_REQUEST: `${NAMESPACE}/publickey/request`,
    USER_SECRET_PUBLICKEY_RESPONSE: `${NAMESPACE}/publickey/response`,
};

export default types;

export const fetchSecretList = (teamName: ConcordKey) => ({
    type: types.USER_SECRET_LIST_REQUEST,
    teamName
});

export const deleteSecret = (teamName: ConcordKey, name: ConcordKey, onSuccess: Array<string>) => ({
    type: types.USER_SECRET_DELETE_REQUEST,
    teamName,
    name,
    onSuccess
});