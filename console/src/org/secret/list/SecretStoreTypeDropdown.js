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
import { actions, selectors } from './effects';

class SecretStoreTypeDropdown extends Component {
    componentDidMount() {
        const { loadFn } = this.props;
        loadFn();
    }

    render() {
        const { data, isLoading, loadFn, ...rest } = this.props;

        let options = [];
        if (data) {
            options = data.map(({ storeType, description }) => {
                return { text: `${description}`, value: `${storeType}` };
            });
        }

        const disabled = options.length <= 1;

        return <Dropdown disabled={disabled} loading={isLoading} options={options} {...rest} search />;
    }
}

const mapStateToProps = ({ secretList }) => ({
    data: selectors.getSecretStoreTypeList(secretList),
    isLoading: selectors.getIsLoading(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    loadFn: () => dispatch(actions.getSecretStoreTypeList())
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretStoreTypeDropdown);
