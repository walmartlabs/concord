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

import * as React from 'react';
import { Button, Header, Modal } from 'semantic-ui-react';
import { NotificationEntry } from '../../../api/notifications';
import { ConcordId } from '../../../api/common';

export interface NotificationModalProps {
    notification: NotificationEntry;
    onDismiss: (id: ConcordId) => Promise<void>;
    onClose: () => void;
}

const NotificationModal: React.FunctionComponent<NotificationModalProps> = ({
    notification,
    onDismiss,
    onClose
}) => {
    const [dismissing, setDismissing] = React.useState(false);
    const alreadyDismissed = !!notification.dismissedTimestamp;

    const handleDismiss = async () => {
        setDismissing(true);
        try {
            await onDismiss(notification.id);
        } finally {
            setDismissing(false);
        }
        onClose();
    };

    return (
        <Modal open={true} onClose={onClose} dimmer="inverted" size="small">
            <Header content={notification.summary} />
            <Modal.Content>
                <p style={{ whiteSpace: 'pre-wrap' }}>{notification.body}</p>
            </Modal.Content>
            <Modal.Actions>
                {notification.actionLink && (
                    <Button
                        primary={true}
                        content="Open"
                        onClick={() => window.open(notification.actionLink, '_blank')}
                    />
                )}
                {!alreadyDismissed && (
                    <Button
                        negative={true}
                        content="Dismiss"
                        loading={dismissing}
                        disabled={dismissing}
                        onClick={handleDismiss}
                    />
                )}
                <Button content="Close" onClick={onClose} />
            </Modal.Actions>
        </Modal>
    );
};

export default NotificationModal;
