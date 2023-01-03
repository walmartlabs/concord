/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import './styles.css';
import { Card, CardContent, CardHeader, Divider, Image } from 'semantic-ui-react';
import { RedirectButton } from '../../organisms';

export default class extends React.PureComponent {
    render() {
        return (
            <div className="flexbox-container">
                <Card centered={true}>
                    <CardContent textAlign={'center'}>
                        <Image id="concord-logo" src="/images/concord.svg" size="medium" />

                        <CardHeader>You are now logged out.</CardHeader>

                        <Divider />

                        <RedirectButton primary={true} fluid={true} location={'/'}>
                            Login again
                        </RedirectButton>
                    </CardContent>
                </Card>
            </div>
        );
    }
}
