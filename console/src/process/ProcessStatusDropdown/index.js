/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { connect } from 'react-redux';

import { ProcessStatusDropdown } from './ProcessStatusDropdown';
import * as types from '../actions';

export class ConnectedProcessStatusDropdown extends React.Component {
    render() {
        const { setQueueFilterFn } = this.props;
        return <ProcessStatusDropdown onChangeFn={setQueueFilterFn} />;
    }
}

const mapStateToProps = (state) => ({});

const mapDispatchToProps = (dispatch) => ({
    setQueueFilterFn: (filter) => {
        dispatch(types.setQueueFilter(filter));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(ConnectedProcessStatusDropdown);
