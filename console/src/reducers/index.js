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
import { reducer as formReducer } from 'redux-form';

import { reducers as session } from '../session';
import { reducers as about } from '../system/about';
import { reducers as login } from '../login';
import { reducers as process } from '../process';
import { reducers as queue } from '../process/queue';
import { reducers as log } from '../process/log';
import { reducers as processForm } from '../process/form';
import { reducers as wizard } from '../process/wizard';
import { reducers as portal } from '../process/portal';
import { reducers as project } from '../project';
import { reducers as projectList } from '../project/list';
import { reducers as projectStart } from '../project/StartProjectPopup';
import { reducers as repository } from '../project/repository';
import { reducers as modal } from '../shared/Modal';
import { reducers as landingList } from '../landing/list';
import { reducers as secretList } from '../org/secret/list';
import { reducers as secretForm } from '../org/secret/create/effects';
import { reducers as repositoryRefresh } from '../project/RepositoryRefreshPopup';

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
  landingList,
  modal,
  secretList,
  secretForm,
  repositoryRefresh,

  form: formReducer
};
