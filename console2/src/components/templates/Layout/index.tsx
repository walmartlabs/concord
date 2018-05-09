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
import { RouteComponentProps, withRouter } from 'react-router';
import { Grid } from 'semantic-ui-react';

import { TopBar } from '../../organisms';

import './styles.css';

type Props = RouteComponentProps<{}>;

class Layout extends React.PureComponent<Props> {
    render() {
        // TODO is there a better way?
        const fullScreen = this.props.location.search.search('fullScreen=true') >= 0;

        return (
            <Grid centered={true}>
                {!fullScreen && (
                    <Grid.Column width={16}>
                        <TopBar />
                    </Grid.Column>
                )}
                <Grid.Column width={11} className="contentColumn">
                    {this.props.children}
                </Grid.Column>
            </Grid>
        );
    }
}

export default withRouter(Layout);
