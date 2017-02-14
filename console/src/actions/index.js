import actionTypes from "./actionTypes";

// VisibleHistoryTable

export const fetchHistoryData = (sortBy, sortDir) => ({
    type: actionTypes.history.FETCH_HISTORY_DATA_REQUEST,
    sortBy,
    sortDir
});

export const killProc = (id) => ({
    type: actionTypes.history.KILL_PROC_REQUEST,
    id
});


// VisibleProjectTable

export const fetchProjectList = (sortBy, sortDir) => ({
    type: actionTypes.projectList.FETCH_PROJECT_LIST_REQUEST,
    sortBy,
    sortDir
});

export const deleteProject = (name) => ({
    type: actionTypes.projectList.DELETE_PROJECT_REQUEST,
    name
});

// VisibleProjectForm

export const fetchProject = (name) => ({
    type: actionTypes.project.FETCH_PROJECT_REQUEST,
    name
});

export const makeNewProject = () => ({
    type: actionTypes.project.MAKE_NEW_PROJECT
});

export const createProject = (data, resolve, reject) => ({
    type: actionTypes.project.CREATE_PROJECT_REQUEST,
    data,
    resolve,
    reject
});

export const updateProject = (name, data, resolve, reject) => ({
    type: actionTypes.project.UPDATE_PROJECT_REQUEST,
    name,
    data,
    resolve,
    reject
});

export const fetchTemplateList = (sortBy, sortDir) => ({
    type: actionTypes.templateList.FETCH_TEMPLATE_LIST_REQUEST,
    sortBy,
    sortDir
});

// VisibleLogViewer

export const fetchLogData = (instanceId, fetchRange, reset) => ({
    type: actionTypes.log.FETCH_LOG_DATA_REQUEST,
    instanceId,
    fetchRange,
    reset
});

