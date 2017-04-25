// @flow
import {reducers as session} from "../session";
import {reducers as login} from "../login";
import {reducers as process} from "../process";
import {reducers as history} from "../process/history";
import {reducers as log} from "../process/log";
import {reducers as form} from "../process/form";
import {reducers as wizard} from "../process/wizard";
import {reducers as portal} from "../process/portal";
import {reducers as secret} from "../user/secret";
import {reducers as modal} from "../shared/Modal";

export default {
    session,
    login,
    process,
    history,
    log,
    form,
    wizard,
    portal,
    secret,
    modal
};