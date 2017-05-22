// @flow
import {reducers as session} from "../session";
import {reducers as login} from "../login";
import {reducers as process} from "../process";
import {reducers as queue} from "../process/queue";
import {reducers as log} from "../process/log";
import {reducers as form} from "../process/form";
import {reducers as wizard} from "../process/wizard";
import {reducers as portal} from "../process/portal";
import {reducers as projectList} from "../project/list";
import {reducers as secret} from "../user/secret";
import {reducers as modal} from "../shared/Modal";

export default {
    session,
    login,
    process,
    queue,
    log,
    form,
    wizard,
    portal,
    projectList,
    secret,
    modal
};