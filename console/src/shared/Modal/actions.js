// @flow
const NAMESPACE = "modal";

const types = {
    MODAL_OPEN: `${NAMESPACE}/open`,
    MODAL_CLOSE: `${NAMESPACE}/close`
};

export default types;

export const open = (kind: string, opts: any) => ({
    type: types.MODAL_OPEN,
    kind,
    opts
});

export const close = () => ({
    type: types.MODAL_CLOSE
});

