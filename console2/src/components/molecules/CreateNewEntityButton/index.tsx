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
import RedirectButton from '../../organisms/RedirectButton';
import { ConcordKey } from '../../../api/common';
import { Popup } from 'semantic-ui-react';

interface Props {
    entity: string;
    title?: string;
    orgName: ConcordKey;
    userInOrg: boolean;
    enabledInPolicy: boolean;
}

export default ({ entity, title, orgName, userInOrg, enabledInPolicy }: Props) => {
    const button = (disabled?: boolean) => (
        <RedirectButton
            disabled={disabled}
            icon="plus"
            positive={true}
            labelPosition="left"
            content={title || `New ${entity}`}
            location={`/org/${orgName}/${entity}/_new`}
        />
    );

    const disabled = !userInOrg || !enabledInPolicy;
    if (!disabled) {
        return button();
    }

    let explanation = '';
    if (!userInOrg) {
        explanation = `Only the organization members can create new ${entity}s.`;
    } else if (!enabledInPolicy) {
        explanation = `The organization's policy forbids the creation of new ${entity}s.`;
    }

    // by default Semantic-UI doesn't trigger popup for the disabled elements
    // wrap in <span/> as a workaround
    return <Popup trigger={<span>{button(true)}</span>} content={explanation} />;
};
