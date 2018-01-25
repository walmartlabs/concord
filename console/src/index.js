/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
// @flow
import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { hashHistory, IndexRedirect, Route, Router } from 'react-router';
import { syncHistoryWithStore } from 'react-router-redux';
import configureStore from './store';
import reducers from './reducers';
import Layout from './layout';
import LoginForm from './login';
import AboutPage from './system/about';
import ProcessStatus from './process';
import ProcessLogViewer from './process/log';
import ProcessQueue from './process/queue';
import ProcessForm from './process/form';
import ProcessWizard from './process/wizard';
import ProcessPortal from './process/portal';
import ProjectList from './project/list';
import LandingList from './landing/list';
import Project from './project';
import SecretList from './org/secret/list';
import CreateSecretPage from './org/secret/create';
import { actions as session } from './session';
import './index.css';
import 'lato-font/css/lato-font.min.css';
import 'semantic-ui-css/semantic.min.css';

const store = configureStore(hashHistory, reducers);

const history = syncHistoryWithStore(hashHistory, store);

const checkAuth = ({ location: { pathname, query } }) => {
  let destination = null;
  if (pathname !== '/login') {
    destination = { pathname, query };
  }

  store.dispatch(session.checkAuth(destination));
};

ReactDOM.render(
  <Provider store={store}>
    <Router history={history}>
      <Route path="login" component={LoginForm} />
      <Route path="/" component={Layout}>
        <IndexRedirect to="process" />
        <Route path="process" onEnter={checkAuth}>
          <IndexRedirect to="queue" />
          <Route path="queue" component={ProcessQueue} />
          <Route path="portal/start" component={ProcessPortal} />
          <Route path=":instanceId/log" component={ProcessLogViewer} />
          <Route path=":instanceId/form/:formInstanceId" component={ProcessForm} />
          <Route path=":instanceId/wizard" component={ProcessWizard} />
          <Route path=":instanceId" component={ProcessStatus} />
        </Route>

        <Route path="project" onEnter={checkAuth}>
          <IndexRedirect to="list" />
          <Route path="list" component={ProjectList} />
          <Route path=":projectName" component={Project} />
        </Route>

        <Route path="secret" onEnter={checkAuth}>
          <IndexRedirect to="list" />
          <Route path="list" component={SecretList} />
          <Route path="_new" component={CreateSecretPage} />
        </Route>

        <Route path="landing" onEnter={checkAuth}>
          <IndexRedirect to="list" />
          <Route path="list" component={LandingList} />
          <Route path=":landingName" component={Project} />
        </Route>

        <Route path="system" onEnter={checkAuth}>
          <IndexRedirect to="about" />
          <Route path="about" component={AboutPage} />
        </Route>

        {/* for backwards compatibility */}
        <Route path="portal/start">
          <IndexRedirect to="/process/portal/start" />
        </Route>
      </Route>
    </Router>
  </Provider>,
  document.getElementById('root')
);
