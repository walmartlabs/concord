// @flow
import {reducer as formReducer} from "redux-form";

import {reducers as session} from "../session";
import {reducers as about} from "../system/about";
import {reducers as login} from "../login";
import {reducers as process} from "../process";
import {reducers as queue} from "../process/queue";
import {reducers as log} from "../process/log";
import {reducers as processForm} from "../process/form";
import {reducers as wizard} from "../process/wizard";
import {reducers as portal} from "../process/portal";
import {reducers as project} from "../project";
import {reducers as projectList} from "../project/list";
import {reducers as projectStart} from "../project/StartProjectPopup";
import {reducers as repository} from "../project/repository";
import {reducers as secretList} from "../secret/list";
import {reducers as secretForm} from "../secret/create";
import {reducers as modal} from "../shared/Modal";
import {reducers as landingList} from "../landing/list";
import {reducers as teamSecretForm} from "../team/secret/create/effects";

export default {
    session,
    login,
    about,
    process,
    queue,
    log,
    processForm,
    wizard,
    portal,
    project,
    projectList,
    projectStart,
    repository,
    secretList,
    secretForm,
    landingList,
    modal,
    teamSecretForm,
    
    form: formReducer
};
