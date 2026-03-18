/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import { Loader } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { RequestErrorMessage } from '../../molecules';
import { useProcessWizard } from './useProcessWizard';

interface Props {
    processInstanceId: ConcordId;
}

const ProcessWizard = ({ processInstanceId }: Props) => {
    const error = useProcessWizard(processInstanceId);

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    return <Loader active={true} />;
};

export default ProcessWizard;
