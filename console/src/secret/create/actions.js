const NAMESPACE = "user/secret";

const types = {
    USER_SECRET_CREATE_KEYPAIR: `${NAMESPACE}/keypair/request`,
    USER_SECRET_CREATE_KEYPAIR_RESPONSE: `${NAMESPACE}/keypair/response`,
};

export default types;

export const createNewKeyPair = () => ({
    type: types.USER_SECRET_CREATE_KEYPAIR
});
