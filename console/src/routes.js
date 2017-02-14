import React from "react";
import {Router, Route, IndexRedirect} from "react-router";
import {syncHistoryWithStore} from "react-router-redux";
import VisibleLayout from "./containers/VisibleLayout";
import VisibleHistoryTable from "./containers/VisibleHistoryTable";
import VisibleProjectTable from "./containers/VisibleProjectTable";
import VisibleLogViewer from "./containers/VisibleLogViewer";
import VisibleLoginForm from "./containers/VisibleLoginForm";
import VisibleProjectForm from "./containers/VisibleProjectForm";
import {getIsLoggedIn} from "./reducers";

export const getProcessHistoryPath = () => "/process/history";
export const getProcessLogPath = (instanceId) => "/process/log/" + instanceId;
export const getProcessStartPath = () => "/process/start";

export const getProjectListPath = () => "/project/list";
export const getProjectNewPath = () => "/project/new";
export const getProjectTemplateListPath = () => "/project/templates";
export const getProjectPath = (name) => "/project/" + name;

export default (store, history) => {
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
            </Route>
            <Route path="project" onEnter={checkAuth}>
                <IndexRedirect to="list"/>
                <Route path="list" component={VisibleProjectTable}/>
                <Route path="new" component={VisibleProjectForm}/>
                <Route path=":name" component={VisibleProjectForm}/>
            </Route>
        </Route>
    </Router>;
};