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
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Dropdown } from 'semantic-ui-react';

import { getCurrentOrg } from '../../../session/reducers';

import { actions, selectors } from './effects';

const LOCKED_SECRET_TYPE = 'PASSWORD';

class SecretsListDropdown extends Component {
    componentDidMount() {
        const { loadFn, org, loadSecretStoreType } = this.props;
        loadSecretStoreType();
        loadFn(org.name);
    }

    render() {
        const {
            data,
            isLoading,
            loadFn,
            loadSecretStoreType,
            org,
            secretStoreTypeList,
            ...rest
        } = this.props;

        let options = [];
        if (data) {
            const newdata = filter(secretStoreTypeList, data);
            options = newdata.map(({ name, type, storageType, storeType }) => {
                const icon = storageType === LOCKED_SECRET_TYPE ? 'lock' : undefined;
                return { text: `${name} (${type})`, value: name, icon: icon };
            });
        }

        return <Dropdown loading={isLoading} options={options} {...rest} search />;
    }
}

const filter = (secretStoreTypeList, data) => {
    var activeSecretlist = [];
    for (var i = 0; i < data.length; i++) {
        if (exist(secretStoreTypeList, data[i].storeType)) {
            activeSecretlist.push(data[i]);
        }
    }
    return activeSecretlist;
};

const exist = (secretStoreTypeList, storeType) => {
    for (var i = 0; i < secretStoreTypeList.length; i++) {
        if (secretStoreTypeList[i].storeType === storeType) {
            return true;
        }
    }
    return false;
};

const mapStateToProps = ({ session, secretList }) => ({
    org: getCurrentOrg(session),
    data: selectors.getRows(secretList),
    secretStoreTypeList: selectors.getSecretStoreTypeList(secretList),
    isLoading: selectors.getIsLoading(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    loadFn: (orgName) => dispatch(actions.fetchSecretList(orgName)),
    loadSecretStoreType: () => dispatch(actions.getSecretStoreTypeList())
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretsListDropdown);
