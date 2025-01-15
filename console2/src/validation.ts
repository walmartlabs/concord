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

/**
 * @see com.walmartlabs.concord.common.validation.ConcordKey
 * @type {RegExp}
 */
const CONCORD_KEY_PATTERN = /^[0-9a-zA-Z][0-9a-zA-Z_@.\-~]{2,128}$/;
const COMMIT_ID_PATTERN = /^[0-9a-f]{1,40}$/;

export const REPOSITORY_SSH_URL_PATTERN = /^(ssh:\/\/)?([a-zA-Z0-9\-_.]+)@([^:]+):?(.*)(.git)$/;

const requiredError = () => 'Required';
const tooLongError = (n: number) => `Must be not more than ${n} characters.`;
const invalidRepositoryUrlError = () =>
    "Invalid repository URL: must begin with 'https://', 'mvn://', 'ssh://' or use 'user@host:path' scheme.";
const invalidCommitIdError = () => 'Invalid commit ID: must be a valid revision.';
const concordKeyPatternError = () =>
    "Must start with a digit or a letter, may contain only digits, letters, underscores, hyphens, tildes, '.' or '@' or. Must be between 3 and 128 characters in length.";

export const projectAlreadyExistsError = (n: string) => `Project already exists: ${n}`;

export const secretAlreadyExistsError = (n: string) => `Secret already exists: ${n}`;

export const jsonStoreAlreadyExistsError = (n: string) => `JSON store already exists: ${n}`;

export const jsonStoreQueryAlreadyExistsError = (n: string) =>
    `JSON store query already exists: ${n}`;

export const repositoryAlreadyExistsError = (n: string) => `Repository already exists: ${n}`;

export const teamAlreadyExistsError = (n: string) => `Team already exists: ${n}`;

export const apiTokenAlreadyExistsError = (n: string) => `API Token already exists: ${n}`;

export const passwordTooWeakError = () =>
    `Password is too weak. It must contain at least 7 characters, a digit and an uppercase character.`;

const concordKeyValidator = (v?: string) => {
    if (!v) {
        return requiredError();
    }

    if (!v.match(CONCORD_KEY_PATTERN)) {
        return concordKeyPatternError();
    }

    return;
};

const repositoryUrlValidator = (v?: string) => {
    if (!v) {
        return requiredError();
    }

    if (!v.startsWith('https://') && !v.match(REPOSITORY_SSH_URL_PATTERN) && !v.startsWith('mvn://')) {
        return invalidRepositoryUrlError();
    }

    return;
};

const requiredValidator = (v?: {}) => {
    if (!v) {
        return requiredError();
    }
    return;
};

export const project = {
    name: concordKeyValidator,
    description: (v?: string) => {
        if (v && v.length > 1024) {
            return tooLongError(1024);
        }
        return;
    }
};

export const storage = {
    name: concordKeyValidator
};

export const storageQuery = {
    name: concordKeyValidator,
    query: (v?: string) => {
        if (!v) {
            return requiredError();
        }
        if (v && v.length > 4000) {
            return tooLongError(4000);
        }
        return;
    }
};

export const repository = {
    name: concordKeyValidator,
    url: repositoryUrlValidator,
    branch: (v?: string) => {
        if (!v) {
            return requiredError();
        }
        if (v && v.length > 255) {
            return tooLongError(255);
        }
        return;
    },
    commitId: (v?: string) => {
        if (!v) {
            return requiredError();
        }
        if (v && !v.match(COMMIT_ID_PATTERN)) {
            return invalidCommitIdError();
        }
        return;
    },
    path: (v?: string) => {
        if (v && v.length > 2048) {
            return tooLongError(2048);
        }
        return;
    },
    secret: concordKeyValidator,
    secretId: requiredValidator
};

export const secret = {
    name: concordKeyValidator,
    publicFile: requiredValidator,
    privateFile: requiredValidator,
    username: requiredValidator,
    password: requiredValidator,
    valueString: requiredValidator,
    valueFile: requiredValidator,
    storePassword: requiredValidator
};

export const team = {
    name: concordKeyValidator,
    description: (v?: string) => {
        if (v && v.length > 2048) {
            return tooLongError(2048);
        }
        return;
    }
};
