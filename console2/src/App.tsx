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
import { Dispatch, useEffect, useReducer, useState } from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router';

import { ProtectedRoute } from './components/organisms';
import {
    AboutPage,
    AddRepositoryPage,
    CustomResourcePage,
    JsonStorePage,
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
    ProcessCardFormPage,
    ProcessPage,
    ProcessWizardPage,
    ProfilePage,
    ProjectPage,
    RepositoryPage,
    SecretPage,
    TeamPage,
    UnauthorizedPage,
    UserActivityPage,
} from './components/pages';
import { Layout } from './components/templates';
import NewStorageQueryPage from './components/pages/JsonStorePage/NewStorageQueryPage';
import EditStoreQueryPage from './components/pages/JsonStorePage/EditStoreQueryPage';
import { initialState, LoadingAction, reducer } from './reducers/loading';
import NewStorePage from './components/pages/JsonStorePage/NewStorePage';
import NodeRosterPage from './components/pages/NodeRoster/NodeRosterPage';
import HostPage from './components/pages/NodeRoster/HostPage';
import { UserSessionContext, checkSession, UserInfo } from './session';

export const LoadingDispatch = React.createContext<Dispatch<LoadingAction>>(
    {} as Dispatch<LoadingAction>
);

export const LoadingState = React.createContext(false);

const App = () => {
    const [state, dispatch] = useReducer(reducer, initialState);

    const [userInfo, setUserInfo] = useState<UserInfo | undefined>();
    const [loggingIn, setLoggingIn] = useState(true);

    useEffect(() => {
        checkSession({ userInfo, setUserInfo, loggingIn: false, setLoggingIn });
    }, [userInfo]);

    return (
        <LoadingState.Provider value={state.loading}>
            <LoadingDispatch.Provider value={dispatch}>
                <HashRouter>
                    <UserSessionContext.Provider
                        value={{ userInfo, setUserInfo, loggingIn, setLoggingIn }}
                    >
                        <Routes>
                            <Route path="/" element={<Navigate to="/activity" replace={true} />} />

                            {/* pages with no decorations */}
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/logout/done" element={<LogoutPage />} />
                            <Route path="/unauthorized" element={<UnauthorizedPage />} />
                            <Route
                                path="/processCard/:cardId/form"
                                element={
                                    <ProtectedRoute>
                                        <ProcessCardFormPage />
                                    </ProtectedRoute>
                                }
                            />

                            {/* pages with standard decorations (provided by Layout) */}
                            <Route element={<ProtectedRoute />}>
                                <Route element={<Layout />}>
                                    <Route path="/activity" element={<UserActivityPage />} />
                                    <Route path="/org" element={<OrganizationListPage />} />
                                    <Route
                                        path="/org/:orgName/project/:projectName/repository/_new"
                                        element={<AddRepositoryPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/project/:projectName/repository/:repoName/*"
                                        element={<RepositoryPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/project/_new"
                                        element={<NewProjectPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/project/:projectName/*"
                                        element={<ProjectPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/secret/_new"
                                        element={<NewSecretPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/secret/:secretName/*"
                                        element={<SecretPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/team/_new"
                                        element={<NewTeamPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/team/:teamName/*"
                                        element={<TeamPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/jsonstore/_new"
                                        element={<NewStorePage />}
                                    />
                                    <Route
                                        path="/org/:orgName/jsonstore/:storeName/query/_new"
                                        element={<NewStorageQueryPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/jsonstore/:storeName/query/:queryName/edit"
                                        element={<EditStoreQueryPage />}
                                    />
                                    <Route
                                        path="/org/:orgName/jsonstore/:storeName/*"
                                        element={<JsonStorePage />}
                                    />
                                    <Route path="/org/:orgName/*" element={<OrganizationPage />} />

                                    <Route path="/process" element={<ProcessListPage />} />
                                    <Route
                                        path="/process/:processInstanceId/form/:formName/:mode"
                                        element={<ProcessFormPage />}
                                    />
                                    <Route
                                        path="/process/:instanceId/wizard"
                                        element={<ProcessWizardPage />}
                                    />
                                    <Route
                                        path="/process/:instanceId/*"
                                        element={<ProcessPage />}
                                    />

                                    <Route path="/noderoster/host/:id/*" element={<HostPage />} />
                                    <Route path="/noderoster/*" element={<NodeRosterPage />} />

                                    <Route path="/about" element={<AboutPage />} />
                                    <Route path="/profile/*" element={<ProfilePage />} />
                                    <Route
                                        path="/custom/:resourceName"
                                        element={<CustomResourcePage />}
                                    />
                                    <Route path="*" element={<NotFoundPage />} />
                                </Route>
                            </Route>
                            <Route path="*" element={<NotFoundPage />} />
                        </Routes>
                    </UserSessionContext.Provider>
                </HashRouter>
            </LoadingDispatch.Provider>
        </LoadingState.Provider>
    );
    // }
};

export default App;
