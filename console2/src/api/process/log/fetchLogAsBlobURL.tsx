/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

export default (processId: string) =>
    fetch(`/api/v1/process/${processId}/log`)
        .then((response) => response.body)
        .then((rs) => {
            if (rs) {
                const reader = rs.getReader();

                // ! Typescript currently does not support types for
                // ! readable streams, thus @ts-ignore
                // @ts-ignore
                return new ReadableStream({
                    async start(controller: any) {
                        while (true) {
                            const { done, value } = await reader.read();

                            // When no more data needs to be consumed, break the reading
                            if (done) {
                                break;
                            }

                            // Enqueue the next data chunk into our target stream
                            controller.enqueue(value);
                        }
                        // Close the stream
                        controller.close();
                        reader.releaseLock();
                    }
                });
            } else {
                throw `Process: ${processId} body missing`;
            }
        })
        // Create a new response out of the stream
        .then((rs) => new Response(rs))
        // Create an object URL for the response
        .then((response) => response.blob())
        .then((blob) => URL.createObjectURL(blob));
