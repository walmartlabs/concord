// @flow
import React from "react";
import ReactDOM from "react-dom";
import {Provider} from "react-redux";
import {hashHistory, IndexRedirect, Route, Router} from "react-router";
import {syncHistoryWithStore} from "react-router-redux";
import configureStore from "./store";
import reducers from "./reducers";
import Layout from "./layout";
import LoginForm from "./login";
import ProcessStatus from "./process";
import ProcessLogViewer from "./process/log";
import ProcessHistory from "./process/history";
import ProcessForm from "./process/form";
import ProcessWizard from "./process/wizard";
import ProcessPortal from "./process/portal";
import UserSecrets from "./user/secret";
import {actions as session} from "./session";
import "./index.css";

const store = configureStore(hashHistory, reducers);

const history = syncHistoryWithStore(hashHistory, store);

const checkAuth = () => {
    store.dispatch(session.checkAuth());
};

ReactDOM.render(
    <Provider store={store}>
        <Router history={history}>
            <Route path="/" component={Layout}>
                <IndexRedirect to="process"/>
                <Route path="login" component={LoginForm}/>
                <Route path="process" onEnter={checkAuth}>
                    <IndexRedirect to="history"/>
                    <Route path="history" component={ProcessHistory}/>
                    <Route path="portal/start" component={ProcessPortal}/>
                    <Route path=":instanceId/log" component={ProcessLogViewer}/>
                    <Route path=":instanceId/form/:formInstanceId" component={ProcessForm}/>
                    <Route path=":instanceId/wizard" component={ProcessWizard}/>
                    <Route path=":instanceId" component={ProcessStatus}/>
                </Route>

                <Route path="user" onEnter={checkAuth}>
                    <IndexRedirect to="secret"/>
                    <Route path="secret" component={UserSecrets}/>
                </Route>

                {/* for backwards compatibility */}
                <Route path="portal/start">
                    <IndexRedirect to="/process/portal/start"/>
                </Route>
            </Route>
        </Router>
    </Provider>,
    document.getElementById("root")
);
