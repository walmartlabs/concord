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
import { Dispatch, useReducer } from 'react';
import { Provider } from 'react-redux';
import { Redirect, Route, Switch } from 'react-router';

import { ProtectedRoute } from './components/organisms';
import {
    AboutPage,
    AddRepositoryPage,
    CustomResourcePage,
    EditRepositoryPage,
    LoginPage,
    LogoutPage,
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
    ProfilePage,
    ProjectPage,
    SecretPage,
    JsonStorePage,
    TeamPage,
    UnauthorizedPage,
    UserActivityPage
} from './components/pages';
import { Layout } from './components/templates';
import { actions as session } from './state/session';
import { history, store } from './store';
import { ConnectedRouter } from 'connected-react-router';
import NewStorageQueryPage from './components/pages/JsonStorePage/NewStorageQueryPage';
import EditStoreQueryPage from './components/pages/JsonStorePage/EditStoreQueryPage';
import { initialState, LoadingAction, reducer } from './reducers/loading';
import NewStorePage from './components/pages/JsonStorePage/NewStorePage';

store.dispatch(session.checkAuth());

export const LoadingDispatch = React.createContext<Dispatch<LoadingAction>>(
    {} as Dispatch<LoadingAction>
);

export const LoadingState = React.createContext(false);

const App = () => {
    const [state, dispatch] = useReducer(reducer, initialState);

    return (
        <Provider store={store}>
            <LoadingState.Provider value={state.loading}>
                <LoadingDispatch.Provider value={dispatch}>
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

                                            <Route
                                                path="/org/:orgName/jsonstore/_new"
                                                exact={true}
                                                component={NewStorePage}
                                            />

                                            <Route
                                                path="/org/:orgName/jsonstore/:storageName/query/_new"
                                                exact={true}
                                                component={NewStorageQueryPage}
                                            />

                                            <Route
                                                path="/org/:orgName/jsonstore/:storageName/query/:queryName/edit"
                                                exact={true}
                                                component={EditStoreQueryPage}
                                            />

                                            <Route
                                                path="/org/:orgName/jsonstore/:storageName"
                                                component={JsonStorePage}
                                            />

                                            <Route
                                                path="/org/:orgName"
                                                component={OrganizationPage}
                                            />
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

                                    <ProtectedRoute
                                        path="/about"
                                        exact={true}
                                        component={AboutPage}
                                    />

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
                </LoadingDispatch.Provider>
            </LoadingState.Provider>
        </Provider>
    );
    // }
};

export default App;
