// @flow
import type {ConcordId, ConcordKey} from "./types";
import type {Store} from "redux";
import React from "react";
import {IndexRedirect, Route, Router} from "react-router";
import {syncHistoryWithStore} from "react-router-redux";
import VisibleLayout from "./containers/VisibleLayout";
import VisibleHistoryTable from "./containers/VisibleHistoryTable";
import VisibleProjectTable from "./containers/VisibleProjectTable";
import VisibleLogViewer from "./containers/VisibleLogViewer";
import VisibleLoginForm from "./containers/VisibleLoginForm";
// import VisibleProjectForm from "./containers/VisibleProjectForm";
import VisibleProcessPage from "./containers/VisibleProcessPage";
import VisibleProcessForm from "./containers/VisibleProcessForm";
import VisibleProcessWizard from "./containers/VisibleProcessWizard";
import {getIsLoggedIn} from "./reducers";

export const getProcessPath = (instanceId: ConcordId) => `/process/${instanceId}`;
export const getProcessFormPath = (processInstanceId: ConcordId, formInstanceId: ConcordId) => `/process/${processInstanceId}/form/${formInstanceId}`;
export const getProcessHistoryPath = () => "/process/history";
export const getProcessLogPath = (instanceId: ConcordId) => `/process/log/${instanceId}`;
export const getProcessWizardPath = (processInstanceId: ConcordId) => `/process/${processInstanceId}/wizard`;

export const getProjectListPath = () => "/project/list";
// export const getProjectNewPath = () => "/project/new";
export const getProjectPath = (name: ConcordKey) => {
    const n = encodeURIComponent(name);
    return `/project/${n}`;
};

export default (store: Store<mixed, mixed>, history: mixed) => {
    const h = syncHistoryWithStore(history, store);

    const checkAuth = (nextState, replace) => {
        if (!getIsLoggedIn(store.getState())) {
            replace("/login");
        }
    };

    return <Router history={h}>
        <Route path="/" component={VisibleLayout}>
            <IndexRedirect to="process"/>
            <Route path="login" component={VisibleLoginForm}/>
            <Route path="process" onEnter={checkAuth}>
                <IndexRedirect to="history"/>
                <Route path="history" component={VisibleHistoryTable}/>
                <Route path="log/:id" components={VisibleLogViewer}/>
                <Route path=":processId/wizard" components={VisibleProcessWizard}/>
                <Route path=":processId/form/:formId" components={VisibleProcessForm}/>
                <Route path=":id" component={VisibleProcessPage}/>
            </Route>
            <Route path="project" onEnter={checkAuth}>
                <IndexRedirect to="list"/>
                <Route path="list" component={VisibleProjectTable}/>
                {/*<Route path="new" component={VisibleProjectForm}/>*/}
                {/*<Route path=":name" component={VisibleProjectForm}/>*/}
            </Route>
        </Route>
    </Router>;
};