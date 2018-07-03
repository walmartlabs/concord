import { Action } from 'redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { TriggerEntry } from '../../../api/org/project/repository';
import { RequestState } from '../common';

export interface ListTriggersRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

export interface ListTriggersResponse extends Action {
    error?: RequestError;
    items?: TriggerEntry[];
}

export type ListTriggersState = RequestState<ListTriggersResponse>;

export interface State {
    listTriggers: ListTriggersState;
}
