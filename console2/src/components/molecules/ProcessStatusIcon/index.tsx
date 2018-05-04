import * as React from 'react';
import { Icon, Popup, SemanticICONS, SemanticCOLORS } from 'semantic-ui-react';

import { ProcessStatus } from '../../../api/process';
type IconSizeProp = 'mini' | 'tiny' | 'small' | 'large' | 'big' | 'huge' | 'massive';

const statusToIcon: {
    [status: string]: { name: SemanticICONS; color?: SemanticCOLORS; loading?: boolean };
} = {
    PREPARING: { name: 'info', color: 'blue' },
    ENQUEUED: { name: 'block layout', color: 'grey' },
    RESUMING: { name: 'circle notched', color: 'grey', loading: true },
    SUSPENDED: { name: 'wait', color: 'blue' },
    STARTING: { name: 'circle notched', color: 'grey', loading: true },
    RUNNING: { name: 'circle notched', color: 'blue', loading: true },
    FINISHED: { name: 'check', color: 'green' },
    FAILED: { name: 'remove', color: 'red' },
    CANCELLED: { name: 'remove', color: 'grey' }
};

interface ProcessStatusIconProps {
    status: ProcessStatus;
    size?: IconSizeProp;
    inverted?: boolean;
}

export default ({ status, size = 'large', inverted = true }: ProcessStatusIconProps) => {
    // TODO: Fix status to reference number..? reference number
    let i = statusToIcon[status];

    if (!i) {
        i = { name: 'question' };
    }

    return (
        <Popup
            trigger={<Icon name={i.name} color={i.color} size={size} loading={i.loading} />}
            content={status}
            inverted={inverted}
            position="top center"
        />
    );
};
