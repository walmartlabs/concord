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

export const secretTypes = {
  newKeyPair: 'NEW_KEY_PAIR',
  existingKeyPair: 'EXISTING_KEY_PAIR',
  usernamePassword: 'USERNAME_PASSWORD',
  singleValue: 'DATA'
};

export const storePwdTypes = {
  doNotUse: 'DO_NOT_USE_STORE_PWD',
  specify: 'SPECIFY_STORE_PWD',
  generate: 'GENERATE_STORE_PWD'
};
