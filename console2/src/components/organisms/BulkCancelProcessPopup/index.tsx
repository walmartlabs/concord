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
import { useCallback, useState } from 'react';

import { ConcordId, RequestError } from '../../../api/common';
import { killBulk as apiKillBulk } from '../../../api/process';
import { SingleOperationPopup } from '../../molecules';

interface Props {
    data: ConcordId[];
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

const BulkCancelProcessPopup = ({ data, refresh, trigger }: Props) => {
    const [cancelling, setCancelling] = useState(false);
    const [success, setSuccess] = useState(false);
    const [error, setError] = useState<RequestError>();

    const reset = useCallback(() => {
        setCancelling(false);
        setSuccess(false);
        setError(undefined);
    }, []);

    const onConfirm = useCallback(async () => {
        setCancelling(true);
        setSuccess(false);
        setError(undefined);

        try {
            await apiKillBulk(data);
            setSuccess(true);
        } catch (e) {
            setError(e);
        } finally {
            setCancelling(false);
        }
    }, [data]);

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Cancel the process(es)?"
            introMsg={<p>Are you sure you want to cancel the selected process(es)?</p>}
            running={cancelling}
            runningMsg={<p>Cancelling...</p>}
            success={success}
            successMsg={<p>The cancel command was sent successfully.</p>}
            error={error}
            reset={reset}
            onConfirm={onConfirm}
            onDone={refresh}
        />
    );
};

export default BulkCancelProcessPopup;
