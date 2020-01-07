/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import * as React from 'react';
import { Provider } from 'react-redux';
import { Redirect, Route, Switch } from 'react-router';
import { ProtectedRoute } from './components/organisms';
import {
    AddRepositoryPage,
    EditRepositoryPage,
    LoginPage,
    NewProjectPage,
    NewSecretPage,
    NewTeamPage,
    NotFoundPage,
    OrganizationListPage,
    OrganizationPage,
    ProcessFormPage,
    ProcessListPage,
    ProcessPage,
    ProcessWizardPage,
    ProjectPage,
    SecretPage,
    TeamPage,
    AboutPage,
    ProfilePage,
    UserActivityPage,
    LogoutPage,
    UnauthorizedPage,
    CustomResourcePage
} from './components/pages';
import { Layout } from './components/templates';
import { actions as session } from './state/session';
import { history, store } from './store';
import { ConnectedRouter } from 'connected-react-router';

store.dispatch(session.checkAuth());

class App extends React.Component {
    render() {
        return (
            <Provider store={store}>
                <ConnectedRouter history={history}>
                    <Switch>
                        <Route exact={true} path="/">
                            <Redirect to="/activity" />
                        </Route>

                        <Route path="/login" component={LoginPage} />

                        <Route path="/logout/done" component={LogoutPage} />

                        <Route path="/unauthorized" component={UnauthorizedPage} />

                        <Layout>
                            <Switch>
                                <ProtectedRoute
                                    path="/activity"
                                    exact={true}
                                    component={UserActivityPage}
                                />

                                <ProtectedRoute path="/org">
                                    <Switch>
                                        <Route
                                            path="/org"
                                            exact={true}
                                            component={OrganizationListPage}
                                        />

                                        <Route
                                            path="/org/:orgName/project/:projectName/repository/_new"
                                            exact={true}
                                            component={AddRepositoryPage}
                                        />

                                        <Route
                                            path="/org/:orgName/project/:projectName/repository/:repoName"
                                            exact={true}
                                            component={EditRepositoryPage}
                                        />

                                        <Route
                                            path="/org/:orgName/project/_new"
                                            exact={true}
                                            component={NewProjectPage}
                                        />

                                        <Route
                                            path="/org/:orgName/project/:projectName"
                                            component={ProjectPage}
                                        />

                                        <Route
                                            path="/org/:orgName/secret/_new"
                                            exact={true}
                                            component={NewSecretPage}
                                        />

                                        <Route
                                            path="/org/:orgName/secret/:secretName"
                                            component={SecretPage}
                                        />

                                        <Route
                                            path="/org/:orgName/team/_new"
                                            exact={true}
                                            component={NewTeamPage}
                                        />

                                        <Route
                                            path="/org/:orgName/team/:teamName"
                                            component={TeamPage}
                                        />

                                        <Route path="/org/:orgName" component={OrganizationPage} />
                                    </Switch>
                                </ProtectedRoute>

                                <ProtectedRoute path="/process">
                                    <Switch>
                                        <Route
                                            path="/process"
                                            exact={true}
                                            component={ProcessListPage}
                                        />

                                        <Route
                                            path="/process/:processInstanceId/form/:formName/:mode"
                                            component={ProcessFormPage}
                                        />

                                        <ProtectedRoute
                                            path="/process/:instanceId/wizard"
                                            exact={true}
                                            component={ProcessWizardPage}
                                        />

                                        <Route
                                            path="/process/:instanceId"
                                            component={ProcessPage}
                                        />
                                    </Switch>
                                </ProtectedRoute>

                                <ProtectedRoute path="/about" exact={true} component={AboutPage} />

                                <ProtectedRoute path="/profile" component={ProfilePage} />

                                <ProtectedRoute path="/custom">
                                    <Switch>
                                        <Route
                                            path="/custom/:resourceName"
                                            component={CustomResourcePage}
                                        />
                                    </Switch>
                                </ProtectedRoute>

                                <Route component={NotFoundPage} />
                            </Switch>
                        </Layout>
                    </Switch>
                </ConnectedRouter>
            </Provider>
        );
    }
}

export default App;
