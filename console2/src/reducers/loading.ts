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
export enum LoadingAction {
    START,
    STOP
}

export interface LoadingState {
    loading: boolean;
    loadingCounter: number;
}

export const initialState = { loading: false, loadingCounter: 0 };

export const reducer = (state: LoadingState, action: LoadingAction): LoadingState => {
    switch (action) {
        case LoadingAction.START:
            return { ...state, loadingCounter: state.loadingCounter + 1, loading: true };
        case LoadingAction.STOP: {
            const count = state.loadingCounter - 1;
            return { ...state, loadingCounter: count, loading: count > 0 };
        }
    }
};
