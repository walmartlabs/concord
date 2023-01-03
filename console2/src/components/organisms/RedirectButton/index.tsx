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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { push as pushHistory } from 'connected-react-router';
import { Button } from 'semantic-ui-react';
import { ButtonProps } from 'semantic-ui-react/dist/commonjs/elements/Button/Button';

interface ExternalProps extends ButtonProps {
    location: string;
}

interface DispatchProps {
    redirect: () => void;
}

class RedirectButton extends React.PureComponent<ExternalProps & DispatchProps> {
    render() {
        const { redirect, location, ...rest } = this.props;
        return <Button {...rest} onClick={() => redirect()} />;
    }
}

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { location }: ExternalProps
): DispatchProps => ({
    redirect: () => dispatch(pushHistory(location))
});

export default connect(null, mapDispatchToProps)(RedirectButton);
