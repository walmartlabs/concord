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
import { useEffect } from 'react';
import { ProcessEntry, ProcessStatus } from '../../../api/process';

const setFavicon = (src: string) => {
    const head = document.head || document.getElementsByTagName('head')[0];

    const oldLink = document.getElementById('favicon');

    const newLink = document.createElement('link');
    newLink.id = 'favicon';
    newLink.rel = 'shortcut icon';
    newLink.href = src;

    if (oldLink) {
        head.removeChild(oldLink);
    }

    document.head.appendChild(newLink);
};

export const useStatusFavicon = (process: ProcessEntry | undefined) =>
    useEffect(() => {
        if (!process) {
            return;
        }

        switch (process.status) {
            case ProcessStatus.NEW:
                setFavicon('/favicon-status-new.png');
                break;
            case ProcessStatus.PREPARING:
                setFavicon('/favicon-status-preparing.png');
                break;
            case ProcessStatus.ENQUEUED:
                setFavicon('/favicon-status-enqueued.png');
                break;
            case ProcessStatus.WAITING:
                setFavicon('/favicon-status-waiting.png');
                break;
            case ProcessStatus.STARTING:
                setFavicon('/favicon-status-starting.png');
                break;
            case ProcessStatus.RESUMING:
                setFavicon('/favicon-status-resuming.png');
                break;
            case ProcessStatus.SUSPENDED:
                setFavicon('/favicon-status-suspended.png');
                break;
            case ProcessStatus.RUNNING:
                setFavicon('/favicon-status-running.png');
                break;
            case ProcessStatus.FINISHED:
                setFavicon('/favicon-status-finished.png');
                break;
            case ProcessStatus.FAILED:
                setFavicon('/favicon-status-failed.png');
                break;
            case ProcessStatus.CANCELLED:
                setFavicon('/favicon-status-cancelled.png');
                break;
            case ProcessStatus.TIMED_OUT:
                setFavicon('/favicon-status-timedout.png');
                break;
        }

        return () => setFavicon('/favicon.png');
    }, [process]);
