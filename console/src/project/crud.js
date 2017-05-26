// @flow
import DataItem from "../shared/DataItem";
import * as api from "./api";

const {actions, reducers, selectors, sagas} = DataItem("project", {},
    api.fetchProject,
    api.updateProject,
    api.deleteProject);

export {actions, reducers, selectors, sagas};
