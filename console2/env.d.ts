export {};

export interface ConcordEnvironment {
    topBar?: TopBarMeta;
    loginUrl?: string;
    logoutUrl?: string;
    login?: LoginConfiguration;
}

export interface TopBarMeta {
    systemLinks?: SystemLinks;
}

export type SystemLinks = LinkMeta[];

export interface LinkMeta {
    text: string;
    url: string;
    icon?: string;
}

export interface LoginConfiguration {
    usernameValidator?: (username: string) => string | undefined;
    usernameHint?: string;
}

declare global {
    interface Window {
        concord: ConcordEnvironment;
    }
}
