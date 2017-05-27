// @flow

export const testRepository = (data: any) => {
    console.debug("API: testRepository ['%o'] -> starting...", data);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    };

    return fetch("/api/service/console/repository/test", opts)
        .then(response => {
            if (!response.ok) {
                const status = response.status;

                return response.text().then(txt => ({
                    error: true,
                    message: status === 500 ? txt : `${response.statusText} (${status})`
                }));
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: testRepository ['%o'] -> done: %o", data, json);
            return json;
        });
};
