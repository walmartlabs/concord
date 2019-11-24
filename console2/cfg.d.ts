export {};

export interface ConcordEnvironment {
    topBar?: TopBarMeta;
    loginUrl?: string;
    logoutUrl?: string;
    login?: LoginConfiguration;
    extraProcessMenuLinks?: ExtraProcessMenuLinks;
}

export interface TopBarMeta {
    systemLinks?: SystemLinks;
}

export type SystemLinks = LinkMeta[];

export type ExtraProcessMenuLinks = ExtraProcessMenuLink[];

export interface LinkMeta {
    text: string;
    url: string;
    icon?: string;
}

export interface LoginConfiguration {
    usernameValidator?: (username: string) => string | undefined;
    usernameHint?: string;
}

export interface ExtraProcessMenuLink {
    url: string;
    label: string;
    color: string;
    icon: string;
}

declare global {
    interface Window {
        concord: ConcordEnvironment;
    }
}
