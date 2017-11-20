// @flow

import * as common from "../../../api";
import {secretTypes} from "./constants";

export const exists = (teamName: string, secretName: string): any => {
    return fetch(`/api/service/console/team/${teamName}/secret/${secretName}/exists`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        });
};

export const create = ({teamName, name, secretType, storePassword, ...rest}: any): any => {
    const data = new FormData();

    data.append("name", name);

    switch (secretType) {
        case secretTypes.newKeyPair: {
            data.append("type", "KEY_PAIR");
            break;
        }
        case secretTypes.existingKeyPair: {
            if (!rest.publicFile || !rest.privateFile) {
                return Promise.reject(new Error("Missing public and/or private key files"));
            }

            data.append("type", "KEY_PAIR");
            data.append("public", rest.publicFile);
            data.append("private", rest.privateFile);
            break;
        }
        case secretTypes.usernamePassword: {
            if (!rest.username || !rest.password) {
                return Promise.reject(new Error("Missing username and/or password values"));
            }

            data.append("type", "USERNAME_PASSWORD");
            data.append("username", rest.username);
            data.append("password", rest.password);
            break;
        }
        case secretTypes.singleValue: {
            if (!rest.data) {
                return Promise.reject(new Error("Missing the data value"));
            }

            data.append("type", "DATA");
            data.append("data", rest.data);
            break;
        }
        default: {
            return Promise.reject(`Unsupported secret type: ${secretType}`);
        }
    }

    if (storePassword) {
        data.append("storePassword", storePassword);
    }

    let opts = {
        method: "POST",
        credentials: "same-origin",
        body: data
    };

    return fetch(`/api/v1/team/${teamName}/secret`, opts)
        .then(resp => {
            if (!resp.ok) {
                return common.parseError(resp);
            }
            return resp.json();
        });
};
